package com.psiconet.infra.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final ApplicationEventPublisher publisher;

    @Value("${psiconet.frontend-url}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetLink(String toEmail, String fullName, String token, int expirationMinutes) {
        // Só o token vai na URL (o e-mail do usuário não aparece no link/histórico do navegador).
        String link = frontendUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        publish(toEmail, EmailTemplate.PASSWORD_RESET, vars(fullName,
                "link", link,
                "expiracaoMinutos", expirationMinutes));
    }

    @Override
    public void sendWelcome(String toEmail, String fullName, String roleLabel) {
        publish(toEmail, EmailTemplate.WELCOME, vars(fullName,
                "perfil", roleLabel,
                "link", frontendUrl + "/login"));
    }

    @Override
    public void sendAdminInvite(String toEmail, String fullName, String invitedByName) {
        publish(toEmail, EmailTemplate.ADMIN_INVITE, vars(fullName,
                "convidadoPor", invitedByName,
                "link", frontendUrl + "/login"));
    }

    @Override
    public void sendTicketReplied(String toEmail, String fullName, String protocol, String ticketPath) {
        publish(toEmail, EmailTemplate.TICKET_REPLIED, vars(fullName,
                "protocolo", protocol,
                "link", frontendUrl + ticketPath));
    }

    @Override
    public void sendTicketClosed(String toEmail, String fullName, String protocol, String ticketPath) {
        publish(toEmail, EmailTemplate.TICKET_CLOSED, vars(fullName,
                "protocolo", protocol,
                "link", frontendUrl + ticketPath));
    }

    @Override
    public void sendAdminAnnouncement(String toEmail, String fullName, String title, String message) {
        publish(toEmail, EmailTemplate.ADMIN_ANNOUNCEMENT, vars(fullName,
                "titulo", title,
                "mensagem", message,
                "link", frontendUrl + "/notifications"));
    }

    @Override
    public void sendRecordAccessRequested(String toEmail, String fullName) {
        publish(toEmail, EmailTemplate.RECORD_ACCESS_REQUESTED, vars(fullName,
                "link", frontendUrl + "/notifications"));
    }

    @Override
    public void sendRecordAccessDecided(String toEmail, String fullName, boolean granted) {
        publish(toEmail, EmailTemplate.RECORD_ACCESS_DECIDED, vars(fullName,
                "liberado", granted,
                "link", frontendUrl + "/patient/medical-records"));
    }

    private void publish(String to, EmailTemplate template, Map<String, Object> variables) {
        if (to == null || to.isBlank()) return;
        publisher.publishEvent(new EmailRequestedEvent(EmailMessage.of(to, template, variables)));
    }

    private static Map<String, Object> vars(String fullName, Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("nome", firstName(fullName));
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        return fullName.trim().split("\\s+")[0];
    }
}
