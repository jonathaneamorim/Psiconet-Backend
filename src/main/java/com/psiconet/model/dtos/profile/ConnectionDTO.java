package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.conection.ConectionStatusEnum;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConnectionDTO {
    private UUID id;
    private UserSummaryDTO sender;
    private UserSummaryDTO receiver;
    private ConectionStatusEnum status;
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserSummaryDTO {
        private UUID id;
        private String fullName;
        private String photoUrl;
        private String role;
    }
}
