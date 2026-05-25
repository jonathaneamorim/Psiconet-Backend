package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.conection.UserConnectionStatusEnum;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PsychologistProfileDTO {
    private UUID id;
    private String fullName;
    private String photoUrl;
    private String crp;
    private List<SpecialtyDTO> specialties;
    private Integer experienceTime;
    private String description;
    private String city;
    private String state;
    private Instant createdAt;
    
    private UserConnectionStatusEnum connectionStatus;
    private UUID connectionId;
}
