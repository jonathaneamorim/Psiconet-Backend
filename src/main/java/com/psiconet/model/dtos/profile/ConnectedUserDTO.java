package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.RoleEnum;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedUserDTO {
    private UUID id;
    private String fullName;
    private String photoUrl;
    private RoleEnum role;
    private String city;
    private String state;

    // Psychologist fields (optional)
    private String crp;
    private List<SpecialtyDTO> specialties;
    private Integer experienceTime;
    private String description;
}
