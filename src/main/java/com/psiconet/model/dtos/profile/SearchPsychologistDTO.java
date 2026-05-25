package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.conection.UserConnectionStatusEnum;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchPsychologistDTO {
    private UUID id;
    private String fullName;
    private String photoUrl;
    private String crp;
    private List<SpecialtyDTO> specialties;
    private String city;
    private String state;
    private UserConnectionStatusEnum connectionStatus;
    private UUID connectionId; // ID of the connection if it exists
}
