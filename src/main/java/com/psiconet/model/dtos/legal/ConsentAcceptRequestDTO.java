package com.psiconet.model.dtos.legal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reaceite. As versões informadas são as que o usuário VIU na tela; o servidor recusa se não forem
 * as vigentes (o documento mudou enquanto a tela estava aberta). Data, IP e navegador vêm do servidor.
 */
public record ConsentAcceptRequestDTO(
        @AssertTrue(message = "Você precisa concordar com os Termos de Uso e a Política de Privacidade.")
        boolean acceptTerms,

        // Só exigido para pacientes (validado no serviço).
        Boolean acceptHealthData,

        @NotBlank(message = "Versão dos Termos de Uso não informada.") @Size(max = 20)
        String termsVersion,

        @NotBlank(message = "Versão da Política de Privacidade não informada.") @Size(max = 20)
        String privacyVersion
) {}
