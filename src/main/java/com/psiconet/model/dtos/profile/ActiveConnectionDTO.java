package com.psiconet.model.dtos.profile;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveConnectionDTO {
    private UUID connectionId;
    private LocalDateTime connectedAt;
    private ConnectedUserDTO user;
}
