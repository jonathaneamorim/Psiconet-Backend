package com.psiconet.model.dtos.legal;

import java.util.List;

/**
 * Situação do usuário logado: {@code pending} lista o que precisa ser (re)aceito agora
 * (TERMS, PRIVACY e, para pacientes, HEALTH_DATA); vazia = nada pendente.
 */
public record ConsentStatusDTO(boolean consentRequired, List<String> pending, String termsVersion, String privacyVersion) {}
