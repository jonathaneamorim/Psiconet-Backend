package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

// Sem campo "role"/"status": o papel é definido pela rota (/register/patient ou /register/psychologist)
// e o Jackson ignora propriedades desconhecidas — mass assignment de ADMIN é impossível por construção.
@Getter
@Setter
public class PsychologistRegisterRequest  {

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    @Size(max = 254, message = "E-mail muito longo.")
    private String email;

    @NotBlank(message = "O nome completo é obrigatório.")
    @Size(min = 3, max = 150, message = "O nome completo deve ter entre 3 e 150 caracteres.")
    private String fullName;

    @NotBlank(message = "O CPF é obrigatório.")
    @CPF(message = "CPF inválido.")
    private String cpf;

    @NotBlank(message = "Para psicólogos o CRP é obrigatório.")
    @Size(max = 20, message = "CRP inválido.")
    private String crp;

    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve estar no passado.")
    private LocalDate birthDate;

    @NotBlank(message = "A senha é obrigatória.")
    @Size(max = 128, message = "Senha muito longa.")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String password;
}