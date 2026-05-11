package com.psiconet.infra.exceptions;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        super(message);
        this.field = null;
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }
}