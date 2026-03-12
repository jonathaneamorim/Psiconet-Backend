package com.psiconet.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("E-mail já cadastrado");
    }

    public EmailAlreadyExistsException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
