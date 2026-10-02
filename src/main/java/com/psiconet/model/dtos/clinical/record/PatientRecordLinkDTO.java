package com.psiconet.model.dtos.clinical.record;

import java.util.UUID;

/** Vínculo do paciente (ativo ou encerrado) com a situação da última solicitação de acesso. */
public record PatientRecordLinkDTO(
        UUID treatmentLinkId,
        String psychologistName,
        String psychologistCrp,
        boolean linkActive,
        AccessRequestDTO latestRequest
) {}
