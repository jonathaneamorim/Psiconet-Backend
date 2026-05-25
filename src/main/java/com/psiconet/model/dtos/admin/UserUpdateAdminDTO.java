package com.psiconet.model.dtos.admin;

import com.psiconet.model.enums.UserStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserUpdateAdminDTO {
    @NotBlank(message = "O nome completo é obrigatório.")
    private String fullName;

    @NotBlank(message = "O telefone é obrigatório.")
    private String phone;

    @NotNull(message = "O status é obrigatório.")
    private UserStatusEnum status;
}
