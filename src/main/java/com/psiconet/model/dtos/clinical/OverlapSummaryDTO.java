package com.psiconet.model.dtos.clinical;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consulta que ocupa o mesmo horário (ADR 019). Só vai para o PSICÓLOGO dono da agenda: o nome do outro
 * paciente nunca aparece para paciente, em notificação ou em e-mail.
 */
public record OverlapSummaryDTO(
        UUID id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String title,
        String patientName
) {}
