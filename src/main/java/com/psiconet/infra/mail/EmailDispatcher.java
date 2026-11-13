package com.psiconet.infra.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Envia os e-mails publicados como {@link EmailRequestedEvent}.
 * <ul>
 *   <li>{@code AFTER_COMMIT}: o e-mail só sai se a operação de negócio foi gravada (sem e-mail de
 *       "cadastro concluído" para um cadastro que sofreu rollback).</li>
 *   <li>{@code fallbackExecution}: chamadas fora de transação também disparam o envio.</li>
 *   <li>{@code @Async}: SMTP fora da thread da requisição; falha de envio vira log e nunca
 *       derruba a operação que originou o e-mail.</li>
 *   <li>Falha transitória (timeout, servidor indisponível) ganha até {@code psiconet.mail.retry-attempts}
 *       tentativas com espera crescente; erro de credencial/endereço não é repetido.</li>
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

    @Value("${psiconet.mail.retry-attempts:3}")
    private int maxAttempts;

    @Value("${psiconet.mail.retry-backoff:2s}")
    private Duration retryBackoff;

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequested(EmailRequestedEvent event) {
        EmailMessage message = event.message();

        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("Envio de e-mail desabilitado (MAIL_FROM não configurado). Template '{}' não enviado.",
                    message.template().getTemplateName());
            return;
        }

        for (int attempt = 1; ; attempt++) {
            try {
                send(message);
                return;
            } catch (Exception e) {
                boolean retry = isTransient(e) && attempt < maxAttempts;
                // Sem conteúdo do e-mail nem destinatário completo no log (pode ser dado pessoal).
                log.error("Falha ao enviar e-mail '{}' para {} (tentativa {}/{}): {}",
                        message.template().getTemplateName(), maskEmail(message.to()), attempt,
                        retry ? maxAttempts : attempt, e.getClass().getSimpleName());
                if (!retry || !sleep(retryBackoff.multipliedBy(attempt))) return;
            }
        }
    }

    private void send(EmailMessage message) throws Exception {
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
    }

    // Credencial errada, endereço inválido ou template quebrado não melhoram tentando de novo.
    private static boolean isTransient(Exception e) {
        return e instanceof MailSendException && !(e instanceof MailAuthenticationException);
    }

    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
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
