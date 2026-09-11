package com.psiconet.services.implement.clinical;

import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecurrenceGenerationServiceTest {

    private final RecurrenceGenerationService service = new RecurrenceGenerationService(null, null, null, null);

    private RecurrenceRule ruleStartingOn(LocalDate startDate, RecurrenceFrequencyEnum frequency) {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(frequency);
        rule.setStartDate(startDate);
        rule.setStartTime(LocalTime.of(14, 0));
        rule.setEndTime(LocalTime.of(15, 0));
        return rule;
    }

    @Test
    void monthlyKeepsTheSameDayOfMonth() {
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 15), RecurrenceFrequencyEnum.MONTHLY);

        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 15)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 3, 15),
                        LocalDate.of(2026, 4, 15),
                        LocalDate.of(2026, 5, 15),
                        LocalDate.of(2026, 6, 15)
                ),
                occurrences
        );
    }

    @Test
    void bimonthlyKeepsTheSameDayEveryTwoMonths() {
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 15), RecurrenceFrequencyEnum.BIMONTHLY);

        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 7, 15)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 3, 15),
                        LocalDate.of(2026, 5, 15),
                        LocalDate.of(2026, 7, 15)
                ),
                occurrences
        );
    }

    @Test
    void monthlyOnDay31ClampsOnShortMonthsButReturnsToDay31Afterwards() {
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 31), RecurrenceFrequencyEnum.MONTHLY);

        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 4, 30)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28), // fevereiro não tem dia 31 -> cai no último dia
                        LocalDate.of(2026, 3, 31), // volta pro dia 31 normalmente
                        LocalDate.of(2026, 4, 30)  // abril não tem dia 31 -> cai no último dia
                ),
                occurrences
        );
    }

    @Test
    void monthlyRecomputationFromALaterDateStillHonorsOriginalDayOfMonth() {
        // Simula o que ensureNextInstance faz: recalcula a partir de "from" bem depois do startDate original.
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 15), RecurrenceFrequencyEnum.MONTHLY);

        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 16), LocalDate.of(2026, 4, 30)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 3, 15),
                        LocalDate.of(2026, 4, 15)
                ),
                occurrences
        );
    }

    @Test
    void quarterlyKeepsTheSameDayEveryThreeMonths() {
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 15), RecurrenceFrequencyEnum.QUARTERLY);

        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 10, 15)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 4, 15),
                        LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 10, 15)
                ),
                occurrences
        );
    }

    @Test
    void everyNDaysStepsByTheConfiguredIntervalRegardlessOfWeekday() {
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 1), RecurrenceFrequencyEnum.EVERY_N_DAYS);
        rule.setIntervalDays(10);

        // A cada 10 dias a partir de 2026-01-01, a ocorrência cai em dias da semana
        // diferentes a cada rodada (inclusive fins de semana) — e isso é esperado, sem ajuste.
        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 10)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 11),
                        LocalDate.of(2026, 1, 21),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 10)
                ),
                occurrences
        );
    }

    @Test
    void everyNDaysRecomputationFromALaterDateKeepsTheOriginalCadence() {
        RecurrenceRule rule = ruleStartingOn(LocalDate.of(2026, 1, 1), RecurrenceFrequencyEnum.EVERY_N_DAYS);
        rule.setIntervalDays(10);

        List<LocalDate> occurrences = service.computeOccurrenceDates(
                rule, LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 10)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 21),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 10)
                ),
                occurrences
        );
    }
}
