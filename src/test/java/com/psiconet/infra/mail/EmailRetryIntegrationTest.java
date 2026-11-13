package com.psiconet.infra.mail;

import com.psiconet.support.IntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** BUG-06: falha transitória de SMTP ganha novas tentativas com espera; erro de credencial não. */
class EmailRetryIntegrationTest extends IntegrationTestBase {

    @Autowired private EmailService emailService;

    @Test
    void falhaTransitoriaETentadaNovamente() {
        doThrow(new MailSendException("timeout")).doThrow(new MailSendException("timeout")).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        emailService.sendWelcome("retry@teste.com", "Maria", "Paciente");

        verify(mailSender, after(2000).times(3)).send(any(MimeMessage.class));
    }

    @Test
    void falhaDeAutenticacaoNaoRepete() {
        doThrow(new MailAuthenticationException("credencial")).when(mailSender).send(any(MimeMessage.class));

        emailService.sendWelcome("auth@teste.com", "Maria", "Paciente");

        verify(mailSender, after(1500).times(1)).send(any(MimeMessage.class));
    }
}
