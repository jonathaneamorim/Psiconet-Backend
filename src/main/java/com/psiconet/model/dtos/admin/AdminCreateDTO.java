package com.psiconet.model.dtos.admin;

import com.psiconet.model.dtos.auth.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

/**
 * Criação de um NOVO administrador (sempre uma conta nova, nunca promoção de conta existente).
 * A senha temporária é definida por quem cria, repassada fora da plataforma e trocada no 1º acesso;
 * ela nunca é enviada por e-mail.
 */
public record AdminCreateDTO(
        @NotBlank(message = "O nome completo é obrigatório.")
        @Size(min = 3, max = 150, message = "O nome completo deve ter entre 3 e 150 caracteres.")
        String fullName,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Formato de e-mail inválido.")
        @Size(max = 254, message = "E-mail muito longo.")
        String email,

        @NotBlank(message = "O CPF é obrigatório.")
        @CPF(message = "CPF inválido.")
        String cpf,

        @NotBlank(message = "A senha temporária é obrigatória.")
        @StrongPassword
        String temporaryPassword
) {}
