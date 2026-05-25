package com.psiconet.model.dtos.admin;

import com.psiconet.model.enums.UserStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserStatusUpdateDTO {
    @NotNull(message = "O status é obrigatório.")
    private UserStatusEnum status;
}
