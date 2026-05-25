package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.conection.UserConnectionStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionStatusInfoDTO {
    private UUID connectionId;
    private UserConnectionStatusEnum status;
}
