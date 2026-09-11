package com.psiconet.services.interfaces;

public interface PasswordResetService {
    void requestReset(String email);
    void validateResetToken(String token);
    void confirmReset(String token, String newPassword);
}
