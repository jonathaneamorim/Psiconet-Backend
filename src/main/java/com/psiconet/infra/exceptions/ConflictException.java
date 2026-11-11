package com.psiconet.infra.exceptions;

import lombok.Getter;

/** Conflito com o estado atual (ex.: dado único já cadastrado). Respondido como 409. */
@Getter
public class ConflictException extends RuntimeException {

    private final String field;

    public ConflictException(String field, String message) {
        super(message);
        this.field = field;
    }
}
