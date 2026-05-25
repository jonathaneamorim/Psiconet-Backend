package com.psiconet.model.dtos.admin;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UsersToListAdminDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String maskedCpf;
    private RoleEnum role;
    private UserStatusEnum status;
    private Instant createdAt;
}
