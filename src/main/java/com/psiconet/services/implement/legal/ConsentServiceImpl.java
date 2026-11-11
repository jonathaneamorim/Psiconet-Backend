package com.psiconet.services.implement.legal;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.LegalVersionOutdatedException;
import com.psiconet.infra.legal.LegalDocumentRegistry;
import com.psiconet.infra.legal.LegalDocumentType;
import com.psiconet.infra.web.ClientIpResolver;
import com.psiconet.model.dtos.legal.ConsentAcceptRequestDTO;
import com.psiconet.model.dtos.legal.ConsentRecordDTO;
import com.psiconet.model.dtos.legal.ConsentStatusDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.legal.UserConsent;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.legal.ConsentSourceEnum;
import com.psiconet.model.enums.legal.ConsentTypeEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.legal.UserConsentRepository;
import com.psiconet.services.interfaces.audit.AuditService;
import com.psiconet.services.interfaces.legal.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ConsentServiceImpl implements ConsentService {

    private final LegalDocumentRegistry registry;
    private final UserConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final ClientIpResolver clientIpResolver;
    private final AuditService auditService;

    @Override
    public void requireCurrentVersions(String termsVersion, String privacyVersion) {
        if (!Objects.equals(termsVersion, registry.currentVersion(LegalDocumentType.TERMS))
                || !Objects.equals(privacyVersion, registry.currentVersion(LegalDocumentType.PRIVACY))) {
            throw new LegalVersionOutdatedException();
        }
    }

    @Override
    @Transactional
    public void recordRegistration(User user, boolean healthDataAccepted) {
        if (user.getRole() == RoleEnum.PATIENT && !healthDataAccepted) {
            throw new BusinessException("acceptHealthData", "Para usar a Psiconet como paciente, autorize o tratamento dos seus dados de saúde.");
        }
        record(user, ConsentSourceEnum.REGISTRATION);
    }

    @Override
    public List<String> pendingFor(User user) {
        List<String> pending = new ArrayList<>();
        if (!Objects.equals(user.getAcceptedTermsVersion(), registry.currentVersion(LegalDocumentType.TERMS))) {
            pending.add(ConsentTypeEnum.TERMS.name());
        }
        if (!Objects.equals(user.getAcceptedPrivacyVersion(), registry.currentVersion(LegalDocumentType.PRIVACY))) {
            pending.add(ConsentTypeEnum.PRIVACY.name());
            if (user.getRole() == RoleEnum.PATIENT) pending.add(ConsentTypeEnum.HEALTH_DATA.name());
        }
        return pending;
    }

    @Override
    public ConsentStatusDTO status(User user) {
        List<String> pending = pendingFor(user);
        return new ConsentStatusDTO(!pending.isEmpty(), pending,
                registry.currentVersion(LegalDocumentType.TERMS), registry.currentVersion(LegalDocumentType.PRIVACY));
    }

    @Override
    @Transactional
    public ConsentStatusDTO accept(User principal, ConsentAcceptRequestDTO request) {
        requireCurrentVersions(request.termsVersion(), request.privacyVersion());
        // Trava a linha: dois cliques simultâneos não geram aceites duplicados.
        User user = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new BadCredentialsException("Sessão inválida"));

        if (pendingFor(user).isEmpty()) {
            return status(user);
        }
        if (user.getRole() == RoleEnum.PATIENT && !Boolean.TRUE.equals(request.acceptHealthData())) {
            throw new BusinessException("acceptHealthData", "Para continuar como paciente, autorize o tratamento dos seus dados de saúde.");
        }

        boolean firstAdminAccess = user.getRole() == RoleEnum.ADMIN && user.getAcceptedTermsVersion() == null;
        record(user, firstAdminAccess ? ConsentSourceEnum.ADMIN_FIRST_ACCESS : ConsentSourceEnum.REACCEPTANCE);
        return status(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentRecordDTO> history(User user) {
        return consentRepository.findByUserOrderByAcceptedAtDesc(user).stream()
                .map(c -> new ConsentRecordDTO(c.getType().name(), c.getVersion(), c.getAcceptedAt(), c.getSource().name()))
                .toList();
    }

    /** Grava TERMS + PRIVACY (+ HEALTH_DATA para paciente) nas versões vigentes e atualiza o usuário. */
    private void record(User user, ConsentSourceEnum source) {
        Instant now = Instant.now();
        String ip = clientIpResolver.currentRequestIp();
        String userAgent = clientIpResolver.currentRequestUserAgent();
        String termsVersion = registry.currentVersion(LegalDocumentType.TERMS);
        String privacyVersion = registry.currentVersion(LegalDocumentType.PRIVACY);

        List<UserConsent> consents = new ArrayList<>();
        consents.add(consent(user, ConsentTypeEnum.TERMS, termsVersion, now, ip, userAgent, source));
        consents.add(consent(user, ConsentTypeEnum.PRIVACY, privacyVersion, now, ip, userAgent, source));
        if (user.getRole() == RoleEnum.PATIENT) {
            consents.add(consent(user, ConsentTypeEnum.HEALTH_DATA, privacyVersion, now, ip, userAgent, source));
        }
        consentRepository.saveAll(consents);

        user.setAcceptedTermsVersion(termsVersion);
        user.setAcceptedPrivacyVersion(privacyVersion);
        userRepository.save(user);

        auditService.record(user, "LEGAL_TERMS_ACCEPTED", "USER", user.getId(),
                "Termos " + termsVersion + ", Privacidade " + privacyVersion + " (" + source.name() + ")");
    }

    private static UserConsent consent(User user, ConsentTypeEnum type, String version, Instant at,
                                       String ip, String userAgent, ConsentSourceEnum source) {
        UserConsent consent = new UserConsent();
        consent.setUser(user);
        consent.setType(type);
        consent.setVersion(version);
        consent.setAcceptedAt(at);
        consent.setIpAddress(ip);
        consent.setUserAgent(userAgent);
        consent.setSource(source);
        return consent;
    }
}
