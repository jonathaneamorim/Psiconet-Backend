package com.psiconet.infra.mail;

/**
 * Fachada de e-mails transacionais da Psiconet. Todos os métodos são "fire and forget": publicam
 * o pedido de envio, que só acontece depois do commit da transação corrente (ver {@link EmailDispatcher}).
 * <p>
 * Nenhum método recebe conteúdo clínico: os e-mails apenas avisam e levam o usuário à plataforma.
 */
public interface EmailService {

    void sendPasswordResetLink(String toEmail, String fullName, String token, int expirationMinutes);

    void sendWelcome(String toEmail, String fullName, String roleLabel);

    void sendAdminInvite(String toEmail, String fullName, String invitedByName);

    void sendTicketReplied(String toEmail, String fullName, String protocol, String ticketPath);

    void sendTicketClosed(String toEmail, String fullName, String protocol, String ticketPath);

    void sendAdminAnnouncement(String toEmail, String fullName, String title, String message);

    void sendRecordAccessRequested(String toEmail, String fullName);

    void sendRecordAccessDecided(String toEmail, String fullName, boolean granted);
}
