package com.psiconet.model.enums.legal;

/**
 * Item aceito pelo usuário. {@code HEALTH_DATA} é o consentimento específico e destacado para o
 * tratamento de dados sensíveis de saúde (LGPD art. 11, I) — colhido só de pacientes e sempre junto
 * da Política de Privacidade (mesma versão). Ver ADR 013.
 */
public enum ConsentTypeEnum {
    TERMS,
    PRIVACY,
    HEALTH_DATA
}
