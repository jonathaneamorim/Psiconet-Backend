package com.psiconet.model.dtos.support;

import com.psiconet.model.enums.support.TicketPriorityEnum;
import jakarta.validation.constraints.NotNull;

public record TicketPriorityUpdateDTO(
        @NotNull(message = "Informe a prioridade.")
        TicketPriorityEnum priority
) {}
