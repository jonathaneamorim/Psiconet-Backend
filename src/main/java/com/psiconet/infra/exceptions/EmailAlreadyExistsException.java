package com.psiconet.infra.exceptions;

/**
 * E-mail já cadastrado. A mensagem não ecoa o e-mail informado (evita refletir dado pessoal
 * em logs/respostas) e orienta o próximo passo.
 */
public class EmailAlreadyExistsException extends ConflictException {
    public EmailAlreadyExistsException() {
        super("email", "Este e-mail já está cadastrado. Faça login ou recupere sua senha.");
    }
}
