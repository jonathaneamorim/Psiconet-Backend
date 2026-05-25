package com.psiconet.model.dtos.profile;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PsychologistMeDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String photoUrl;
    private Location location;
    private RoleEnum role;
    private UserStatusEnum status;
    private String crp;
    private List<SpecialtyDTO> specialties;
    private Integer experienceTime;
    private String description;
    private Instant createdAt;
}
