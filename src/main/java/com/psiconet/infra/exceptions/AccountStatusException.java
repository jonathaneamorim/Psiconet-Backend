package com.psiconet.infra.exceptions;

import lombok.Getter;

/**
 * Conta com credenciais corretas, mas em status que não permite acesso (inativa, bloqueada,
 * aguardando verificação). Só é lançada DEPOIS da senha conferir — antes disso, qualquer falha é
 * "credenciais inválidas", para não revelar se o e-mail existe.
 */
@Getter
public class AccountStatusException extends RuntimeException {

    private final String code;

    public AccountStatusException(String code, String message) {
        super(message);
        this.code = code;
    }
}
