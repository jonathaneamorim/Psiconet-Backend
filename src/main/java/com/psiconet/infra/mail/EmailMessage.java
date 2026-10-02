package com.psiconet.infra.mail;

import java.util.Map;

/**
 * E-mail a ser renderizado e enviado. As variáveis são escapadas pelo Thymeleaf na renderização
 * ({@code th:text}), então podem conter texto digitado por usuários sem risco de injeção de HTML.
 */
public record EmailMessage(
        String to,
        EmailTemplate template,
        String subject,
        Map<String, Object> variables
) {
    public EmailMessage {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        subject = subject == null || subject.isBlank() ? template.getDefaultSubject() : subject;
    }

    public static EmailMessage of(String to, EmailTemplate template, Map<String, Object> variables) {
        return new EmailMessage(to, template, null, variables);
    }
}
