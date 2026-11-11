package com.psiconet.model.dtos.support;

import com.psiconet.model.enums.support.TicketStatusEnum;
import jakarta.validation.constraints.NotNull;

public record TicketStatusUpdateDTO(
        @NotNull(message = "Informe o status.")
        TicketStatusEnum status
) {}
