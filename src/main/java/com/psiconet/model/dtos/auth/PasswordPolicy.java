package com.psiconet.model.dtos.auth;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Política única de senha (cadastro, troca, redefinição e criação de administradores). Aplique com {@link StrongPassword}. */
public final class PasswordPolicy {
    public static final String REGEX = "^(?=.*[A-Z])(?=.*\\W).{8,}$";
    public static final String MESSAGE =
            "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula e um caractere especial.";

    /**
     * O BCrypt só usa os primeiros 72 bytes: acima disso, duas senhas com o mesmo começo seriam aceitas
     * como iguais. Bytes UTF-8, não caracteres — letras acentuadas ocupam 2.
     */
    public static final int MAX_BYTES = 72;
    public static final String TOO_LONG_MESSAGE =
            "A senha pode ter no máximo 72 bytes (cerca de 72 caracteres sem acento ou 36 acentuados).";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private PasswordPolicy() {}

    public static boolean exceedsMaxBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    public static boolean matches(String password) {
        return PATTERN.matcher(password).matches();
    }

    /** Senha válida pela regra completa (padrão + tamanho). */
    public static boolean isValid(String password) {
        return password != null && !exceedsMaxBytes(password) && matches(password);
    }
}
