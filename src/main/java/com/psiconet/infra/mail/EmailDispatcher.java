package com.psiconet.infra.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia os e-mails publicados como {@link EmailRequestedEvent}.
 * <ul>
 *   <li>{@code AFTER_COMMIT}: o e-mail só sai se a operação de negócio foi gravada (sem e-mail de
 *       "cadastro concluído" para um cadastro que sofreu rollback).</li>
 *   <li>{@code fallbackExecution}: chamadas fora de transação também disparam o envio.</li>
 *   <li>{@code @Async}: SMTP fora da thread da requisição; falha de envio vira log e nunca
 *       derruba a operação que originou o e-mail.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    private final JavaMailSender mailSender;
    private final EmailRenderer renderer;

    @Value("${psiconet.mail.from:}")
    private String fromAddress;

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequested(EmailRequestedEvent event) {
        EmailMessage message = event.message();

        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("Envio de e-mail desabilitado (MAIL_FROM não configurado). Template '{}' não enviado.",
                    message.template().getTemplateName());
            return;
        }

        try {
            EmailRenderer.RenderedEmail rendered = renderer.render(message);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress, "Psiconet");
            helper.setTo(message.to());
            helper.setSubject(rendered.subject());
            // multipart/alternative: clientes sem HTML exibem a versão texto
            helper.setText(rendered.text(), rendered.html());
            mime.addHeader("Auto-Submitted", "auto-generated");
            mailSender.send(mime);
        } catch (Exception e) {
            // Sem conteúdo do e-mail nem destinatário completo no log (pode ser dado pessoal).
            log.error("Falha ao enviar e-mail '{}' para {}: {}",
                    message.template().getTemplateName(), maskEmail(message.to()), e.getClass().getSimpleName());
        }
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.isEmpty() ? "" : local.substring(0, 1);
        return visible + "***" + email.substring(at);
    }
}
