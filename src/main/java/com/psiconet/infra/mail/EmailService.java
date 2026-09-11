package com.psiconet.infra.mail;

public interface EmailService {
    void sendPasswordResetLink(String toEmail, String fullName, String token, int expirationMinutes);
}
