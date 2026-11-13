package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Limites de sanidade de agendamento (BUG-07b), comuns à consulta avulsa e à regra de recorrência. */
final class AppointmentLimits {

    static final Duration MIN_DURATION = Duration.ofMinutes(15);
    static final Duration MAX_DURATION = Duration.ofHours(8);
    static final int MAX_MONTHS_AHEAD = 12;

    private AppointmentLimits() {}

    static void requireDuration(LocalDateTime start, LocalDateTime end, String field) {
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
            throw new BusinessException(field, "A consulta deve durar entre 15 minutos e 8 horas.");
        }
    }

    static void requireWithinHorizon(LocalDate date, String field) {
        if (date.isAfter(LocalDate.now().plusMonths(MAX_MONTHS_AHEAD))) {
            throw new BusinessException(field, "Agende com no máximo 12 meses de antecedência.");
        }
    }
}
