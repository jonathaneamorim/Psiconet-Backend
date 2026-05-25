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
    private String fullName;

    private String phone;

    @NotNull(message = "O status é obrigatório.")
    private UserStatusEnum status;
}
