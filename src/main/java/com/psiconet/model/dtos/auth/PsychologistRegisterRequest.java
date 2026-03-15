package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

@Data
public class PsychologistRegisterRequest  {

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    @NotBlank(message = "O CPF é obrigatório.")
    @CPF(message = "CPF inválido.")
    private String cpf;

    @NotBlank(message = "Para psicólogos o CRP é obrigatório.")
    private String crp;

    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve estar no passado.")
    private LocalDate birthDate;

    @NotBlank(message = "A senha é obrigatória.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\W).{8,}$",
            message = "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula e um caractere especial."
    )
    private String password;
}