package com.psiconet.model.dtos.clinical;

import java.time.LocalDateTime;
import java.util.List;

/** Ocorrência de uma série recorrente que coincide com outra consulta do psicólogo (ADR 019). */
public record RecurrenceOccurrenceOverlapDTO(
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        List<OverlapSummaryDTO> overlappingAppointments
) {}
