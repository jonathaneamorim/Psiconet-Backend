package com.psiconet.model.dtos.clinical.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Visão somente leitura liberada ao paciente: só o registro documental (sem anotações pessoais). */
public record PatientRecordViewDTO(
        UUID accessRequestId,
        String patientName,
        String psychologistName,
        String psychologistCrp,
        LocalDate openingDate,
        String demand,
        String goals,
        String history,
        String procedures,
        String closing,
        List<SessionNoteDTO> notes,
        LocalDateTime accessExpiresAt,
        boolean exportAllowed
) {}
