package com.psiconet.services.implement.legal;

import com.psiconet.infra.legal.LegalDocument;
import com.psiconet.infra.legal.LegalDocumentRegistry;
import com.psiconet.infra.legal.LegalDocumentType;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.legal.LegalDocumentVersion;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.legal.LegalDocumentVersionRepository;
import com.psiconet.services.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Na inicialização, registra as versões vigentes dos documentos legais. Quando uma versão NOVA de
 * Termos ou Privacidade entra no ar (já havia versão anterior registrada), avisa uma única vez todos
 * os usuários ativos: notificação obrigatória + e-mail, só com o nome dos documentos (ADR 013).
 * O reaceite em si é exigido pelo {@code SecurityFilter}. Mudança só na Política de Cookies não gera
 * aviso: o banner reaparece sozinho (versão no cookie de consentimento, ADR 012).
 * <p>
 * Com várias instâncias subindo juntas, a UNIQUE (tipo, versão) garante que só uma delas avise.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalVersionPublisher {

    private final LegalDocumentRegistry registry;
    private final LegalDocumentVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final PlatformTransactionManager transactionManager;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<String> updated = new ArrayList<>();
        for (LegalDocumentType type : LegalDocumentType.values()) {
            if (registerIfNew(registry.get(type)) && type != LegalDocumentType.COOKIES) {
                updated.add(registry.get(type).title());
            }
        }
        if (!updated.isEmpty()) {
            notifyActiveUsers(String.join(" e ", updated));
        }
    }

    /** @return true se é uma versão nova de um documento que já tinha versão anterior. */
    private boolean registerIfNew(LegalDocument doc) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            Boolean result = tx.execute(status -> {
                if (versionRepository.existsByTypeAndVersion(doc.type(), doc.version())) return false;
                boolean hadPrevious = versionRepository.existsByType(doc.type());
                LegalDocumentVersion version = new LegalDocumentVersion();
                version.setType(doc.type());
                version.setVersion(doc.version());
                version.setEffectiveDate(doc.effectiveDate());
                version.setPublishedAt(Instant.now());
                versionRepository.saveAndFlush(version);
                if (hadPrevious) log.info("Nova versão publicada: {} {}", doc.type(), doc.version());
                return hadPrevious;
            });
            return Boolean.TRUE.equals(result);
        } catch (DataIntegrityViolationException e) {
            // Outra instância registrou a mesma versão ao mesmo tempo: ela é quem avisa.
            return false;
        }
    }

    private void notifyActiveUsers(String documentNames) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            List<User> active = userRepository.findByStatus(UserStatusEnum.ACTIVE);
            for (User user : active) {
                notificationService.create(user, null, NotificationType.LEGAL_DOCUMENTS_UPDATED,
                        "Atualizamos nossos documentos",
                        "Publicamos uma nova versão de " + documentNames + ". Leia e confirme o aceite para continuar.",
                        "LEGAL", null);
                emailService.sendLegalDocumentsUpdated(user.getEmail(), user.getFullName(), documentNames);
            }
            log.info("Aviso de nova versão de documentos legais enviado a {} usuário(s).", active.size());
        });
    }
}
