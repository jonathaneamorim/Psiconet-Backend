package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

@Getter
@Setter
public class PatientRegisterRequest {

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    @NotBlank(message = "O nome completo é obrigatório.")
    @Size(min = 3, max = 150, message = "O nome completo deve ter entre 3 e 150 caracteres.")
    private String fullName;

    @NotBlank(message = "A senha é obrigatória.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\W).{8,}$",
            message = "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula e um caractere especial."
    )
    private String password;

    @NotBlank(message = "O CPF é obrigatório.")
    @CPF(message = "CPF inválido.")
    private String cpf;

    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve estar no passado.")
    private LocalDate birthDate;
}