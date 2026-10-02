package com.psiconet.model.dtos.auth;

/** Política única de senha (cadastro, troca, redefinição e criação de administradores). */
public final class PasswordPolicy {
    public static final String REGEX = "^(?=.*[A-Z])(?=.*\\W).{8,}$";
    public static final String MESSAGE =
            "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula e um caractere especial.";

    private PasswordPolicy() {}
}
