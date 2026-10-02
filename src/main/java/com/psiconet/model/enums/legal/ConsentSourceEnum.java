package com.psiconet.model.enums.legal;

/** Onde o aceite aconteceu. */
public enum ConsentSourceEnum {
    /** No formulário de cadastro (paciente/psicólogo). */
    REGISTRATION,
    /** Reaceite obrigatório após nova versão, ou primeiro aceite de conta anterior aos documentos. */
    REACCEPTANCE,
    /** Primeiro acesso de administrador criado por outro administrador (ou admin base). */
    ADMIN_FIRST_ACCESS
}
