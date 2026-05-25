package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.conection.UserConnectionStatusEnum;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatientProfileDTO {
    private UUID id;
    private String fullName;
    private String photoUrl;
    private String city;
    private String state;
    private Instant createdAt;

    private UserConnectionStatusEnum connectionStatus;
    private UUID connectionId;
}
