package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MeetingFieldsValidatorTest {

    private static Location address() {
        Location location = new Location();
        location.setStreet("Rua A");
        location.setCity("Recife");
        location.setState("PE");
        return location;
    }

    private static String fieldOf(Runnable call) {
        return assertThrows(BusinessException.class, call::run).getField();
    }

    @Test
    void presencialExigeEnderecoEIgnoraProvedorELink() {
        var fields = MeetingFieldsValidator.validate(MeetingTypeEnum.IN_PERSON, MeetingProviderEnum.IN_APP,
                "https://x.com", address(), true);
        assertNull(fields.provider());
        assertNull(fields.meetingLink());
        assertEquals("location", fieldOf(() -> MeetingFieldsValidator.validate(
                MeetingTypeEnum.IN_PERSON, null, null, null, true)));
    }

    @Test
    void salaPsiconetIgnoraLinkEDispensaEndereco() {
        var fields = MeetingFieldsValidator.validate(MeetingTypeEnum.VIDEO_CALL, MeetingProviderEnum.IN_APP,
                "https://meet.google.com/x", null, true);
        assertEquals(MeetingProviderEnum.IN_APP, fields.provider());
        assertNull(fields.meetingLink());
    }

    @Test
    void salaPsiconetComFuncionalidadeDesligadaERecusada() {
        assertEquals("meetingProvider", fieldOf(() -> MeetingFieldsValidator.validate(
                MeetingTypeEnum.VIDEO_CALL, MeetingProviderEnum.IN_APP, null, null, false)));
    }

    @Test
    void provedorOmitidoEquivaleALinkExterno() {
        var fields = MeetingFieldsValidator.validate(MeetingTypeEnum.VIDEO_CALL, null, "https://zoom.us/j/1", null, false);
        assertEquals(MeetingProviderEnum.EXTERNAL_LINK, fields.provider());
        assertEquals("https://zoom.us/j/1", fields.meetingLink());
    }

    @Test
    void linkExternoEObrigatorio() {
        assertEquals("meetingLink", fieldOf(() -> MeetingFieldsValidator.validate(
                MeetingTypeEnum.VIDEO_CALL, MeetingProviderEnum.EXTERNAL_LINK, "  ", null, true)));
        assertEquals("meetingLink", fieldOf(() -> MeetingFieldsValidator.validate(
                MeetingTypeEnum.VIDEO_CALL, null, null, null, true)));
    }

    @Test
    void linkSemEsquemaRecebeHttpsEHttpEhAceito() {
        assertEquals("https://meet.google.com/abc", MeetingFieldsValidator.normalizeExternalLink(" meet.google.com/abc "));
        assertEquals("http://intranet.example.com/sala", MeetingFieldsValidator.normalizeExternalLink("http://intranet.example.com/sala"));
        assertEquals("https://meet.example.com:8443/x", MeetingFieldsValidator.normalizeExternalLink("meet.example.com:8443/x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "JavaScript:alert(1)", "javascript://%0Aalert(1)", "data:text/html;base64,PHNjcmlwdD4=",
            "vbscript:msgbox", "file:///etc/passwd", "ftp://example.com/x", "https://user:senha@evil.example.com",
            "https://meet google.com", "https://", "https://exa mple.com\nx"
    })
    void linksPerigososOuInvalidosSaoRecusados(String link) {
        assertThrows(BusinessException.class, () -> MeetingFieldsValidator.normalizeExternalLink(link));
    }

    @Test
    void linkMaiorQueOLimiteERecusado() {
        String link = "https://example.com/" + "a".repeat(MeetingFieldsValidator.MAX_LINK_LENGTH);
        assertThrows(BusinessException.class, () -> MeetingFieldsValidator.normalizeExternalLink(link));
    }
}
