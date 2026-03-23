package com.psiconet.infra.exceptions;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
