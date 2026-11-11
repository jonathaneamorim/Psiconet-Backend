package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras dos campos de modalidade (consulta única e recorrência):
 * <ul>
 *   <li>{@code VIDEO_CALL + EXTERNAL_LINK} (ou provedor omitido): link obrigatório, só http/https, ≤ 255;</li>
 *   <li>{@code VIDEO_CALL + IN_APP}: link ignorado; recusado se a videochamada integrada estiver desligada;</li>
 *   <li>{@code IN_PERSON}: endereço obrigatório; provedor e link ignorados.</li>
 * </ul>
 */
public final class MeetingFieldsValidator {

    public static final int MAX_LINK_LENGTH = 255;

    // "esquema:" no início (javascript:, data:, vbscript:, file:...). "host:porta" não casa (porta é dígito).
    private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):(?!\\d)");

    private MeetingFieldsValidator() {}

    /** Provedor e link efetivos, já normalizados, a gravar na consulta/regra. */
    public record MeetingFields(MeetingProviderEnum provider, String meetingLink) {}

    public static MeetingFields validate(
            MeetingTypeEnum meetingType,
            MeetingProviderEnum meetingProvider,
            String meetingLink,
            Location location,
            boolean inAppVideoAvailable
    ) {
        if (meetingType == MeetingTypeEnum.IN_PERSON) {
            if (location == null) {
                throw new BusinessException("location", "Informe o endereço da consulta presencial.");
            }
            return new MeetingFields(null, null);
        }

        MeetingProviderEnum provider = meetingProvider != null ? meetingProvider : MeetingProviderEnum.EXTERNAL_LINK;

        if (provider == MeetingProviderEnum.IN_APP) {
            if (!inAppVideoAvailable) {
                throw new BusinessException(
                        "meetingProvider",
                        "A sala de vídeo da Psiconet não está disponível no momento. Use um link externo (Meet, Zoom...)."
                );
            }
            return new MeetingFields(MeetingProviderEnum.IN_APP, null);
        }

        return new MeetingFields(MeetingProviderEnum.EXTERNAL_LINK, normalizeExternalLink(meetingLink));
    }

    /** Link externo seguro: http/https com host, sem espaços/controles; sem esquema ⇒ https://. */
    static String normalizeExternalLink(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            throw new BusinessException("meetingLink", "Informe o link da chamada de vídeo.");
        }
        String link = rawLink.trim();

        if (link.length() > MAX_LINK_LENGTH) {
            throw new BusinessException("meetingLink", "O link da chamada deve ter no máximo " + MAX_LINK_LENGTH + " caracteres.");
        }
        if (link.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new BusinessException("meetingLink", "O link da chamada não pode conter espaços.");
        }

        var schemeMatcher = SCHEME.matcher(link);
        if (schemeMatcher.find()) {
            String scheme = schemeMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new BusinessException("meetingLink", "Use um link que comece com https:// (ex.: Google Meet ou Zoom).");
            }
        } else {
            link = "https://" + link;
            if (link.length() > MAX_LINK_LENGTH) {
                throw new BusinessException("meetingLink", "O link da chamada deve ter no máximo " + MAX_LINK_LENGTH + " caracteres.");
            }
        }

        try {
            URI uri = new URI(link);
            if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                throw new BusinessException("meetingLink", "Informe um link de chamada válido.");
            }
        } catch (URISyntaxException ex) {
            throw new BusinessException("meetingLink", "Informe um link de chamada válido.");
        }
        return link;
    }
}
