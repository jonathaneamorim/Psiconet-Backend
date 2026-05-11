package com.psiconet.model.dtos.access;

import com.psiconet.model.enums.RoleEnum;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private String id;
    private String username;
    private RoleEnum role;
}
