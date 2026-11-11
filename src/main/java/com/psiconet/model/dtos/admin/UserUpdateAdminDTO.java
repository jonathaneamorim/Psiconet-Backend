package com.psiconet.model.dtos.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Correção de dados cadastrais pelo admin. Sem "role" e sem "status": papel nunca muda
 * (psicólogo/paciente não viram admin) e status só pelo endpoint dedicado, com as proteções.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserUpdateAdminDTO {
    @NotBlank(message = "O nome completo é obrigatório.")
    @Size(min = 3, max = 150, message = "O nome completo deve ter entre 3 e 150 caracteres.")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{8,20}$", message = "Telefone inválido.")
    private String phone;

    @Email(message = "Formato de e-mail inválido.")
    @Size(max = 254, message = "E-mail muito longo.")
    private String email;
}
