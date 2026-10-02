package com.psiconet.infra.exceptions;

/** Prontuário indisponível porque a chave-mestra de criptografia não foi configurada (503). */
public class ClinicalUnavailableException extends RuntimeException {
    public ClinicalUnavailableException() {
        super("O prontuário está temporariamente indisponível. Contate a administração da plataforma.");
    }
}
