package com.psiconet.infra.exceptions;

import com.psiconet.model.dtos.clinical.OverlapSummaryDTO;
import lombok.Getter;

import java.util.List;

/** Horário já ocupado e o psicólogo ainda não confirmou (acknowledgeOverlap) — 409 SCHEDULE_OVERLAP. */
@Getter
public class ScheduleOverlapException extends RuntimeException {

    private final List<OverlapSummaryDTO> conflicts;

    public ScheduleOverlapException(List<OverlapSummaryDTO> conflicts) {
        super(conflicts.size() == 1
                ? "Você já tem consulta neste horário. Confirme para agendar mesmo assim."
                : "Você já tem " + conflicts.size() + " consultas neste horário. Confirme para agendar mesmo assim.");
        this.conflicts = List.copyOf(conflicts);
    }
}
