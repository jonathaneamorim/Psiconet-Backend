package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.conection.UserConnectionStatusEnum;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchPatientDTO {
    private UUID id;
    private String fullName;
    private String photoUrl;
    private String city;
    private String state;
    private UserConnectionStatusEnum connectionStatus;
    private UUID connectionId;
}
