package com.psiconet.services.implement.clinical;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.services.implement.financial.PaymentGenerationService;
import com.psiconet.services.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Garante que cada {@link RecurrenceRule} ativa tenha sempre, no máximo, UMA
 * instância futura/atual de {@link Appointment} materializada. A regra em si é a
 * fonte de verdade da recorrência (o front pode computar/exibir as ocorrências
 * futuras direto a partir dela via preview); assim que a instância corrente é
 * consumida (concluída ou cancelada), a próxima é gerada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceGenerationService {

    private static final List<AppointmentStatusEnum> ACTIVE_STATUSES =
            List.of(AppointmentStatusEnum.ACCEPTED);

    private static final Map<RecurrenceFrequencyEnum, String> FREQUENCY_LABELS = new EnumMap<>(RecurrenceFrequencyEnum.class);
    static {
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.WEEKLY, "semanal");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.BIWEEKLY, "quinzenal");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.MONTHLY, "mensal");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.BIMONTHLY, "bimestral");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.QUARTERLY, "trimestral");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.FIRST_DAY_OF_MONTH, "no primeiro dia do mês");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.LAST_DAY_OF_MONTH, "no último dia do mês");
        FREQUENCY_LABELS.put(RecurrenceFrequencyEnum.EVERY_N_DAYS, "recorrente");
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final PaymentGenerationService paymentGenerationService;

    @Scheduled(fixedRateString = "${psiconet.recurrence.generation-fixed-rate:1800000}")
    @Transactional
    public void ensureNextInstanceForAllActiveRules() {
        recurrenceRuleRepository.findByIsActiveTrue().forEach(this::ensureNextInstance);
    }

    @Transactional
    public void ensureNextInstance(RecurrenceRule rule) {
        if (appointmentRepository.existsByRecurrenceRuleAndStatusIn(rule, ACTIVE_STATUSES)) {
            return;
        }

        LocalDate from = rule.getLastGeneratedUntil() != null
                ? rule.getLastGeneratedUntil().plusDays(1)
                : rule.getStartDate();

        LocalDate searchLimit = rule.getEndDate() != null ? rule.getEndDate() : from.plusYears(2);

        if (from.isAfter(searchLimit)) {
            return;
        }

        for (LocalDate date : computeOccurrenceDates(rule, from, searchLimit)) {
            rule.setLastGeneratedUntil(date);

            if (tryGenerateInstance(rule, date)) {
                recurrenceRuleRepository.save(rule);
                return;
            }
        }

        recurrenceRuleRepository.save(rule);
    }

    private boolean tryGenerateInstance(RecurrenceRule rule, LocalDate date) {
        LocalDateTime start = date.atTime(rule.getStartTime());
        LocalDateTime end = date.atTime(rule.getEndTime());

        boolean hasConflict = !appointmentRepository
                .findConflicting(rule.getTreatmentLink().getPsychologist(), start, end)
                .isEmpty();

        if (hasConflict) {
            log.warn("Ocorrência da regra de recorrência {} pulada por conflito de horário em {}", rule.getId(), date);
            return false;
        }

        Appointment appointment = new Appointment();
        appointment.setTreatmentLink(rule.getTreatmentLink());
        appointment.setRecurrenceRule(rule);
        appointment.setStartDateTime(start);
        appointment.setEndDateTime(end);
        appointment.setStatus(AppointmentStatusEnum.ACCEPTED);
        appointment.setMeetingType(rule.getMeetingType());
        appointment.setMeetingProvider(rule.getMeetingProvider());
        appointment.setMeetingLink(rule.getMeetingLink());
        appointment.setLocation(rule.getLocation());
        appointment.setPrice(rule.getPrice() != null ? rule.getPrice() : rule.getTreatmentLink().getDefaultPrice());

        String title = (rule.getTitle() != null && !rule.getTitle().isBlank())
                ? rule.getTitle()
                : rule.getTreatmentLink().getPsychologist().getUser().getFullName()
                        + " X " + rule.getTreatmentLink().getPatient().getUser().getFullName();
        appointment.setTitle(title);

        appointment = appointmentRepository.save(appointment);
        paymentGenerationService.generatePaymentIfNeeded(appointment);

        notificationService.create(
                rule.getTreatmentLink().getPatient().getUser(),
                rule.getTreatmentLink().getPsychologist().getUser(),
                NotificationType.APPOINTMENT_SCHEDULED,
                "Nova Consulta Periódica",
                "Você tem uma nova consulta periódica (" + FREQUENCY_LABELS.get(rule.getFrequency())
                        + ") agendada para " + date.format(DATE_FORMATTER) + ".",
                "APPOINTMENT",
                appointment.getId()
        );

        return true;
    }

    /**
     * Calcula (sem persistir) as datas de ocorrência da regra entre {@code from} e {@code to},
     * usado tanto para materializar a próxima instância quanto para o preview do calendário.
     */
    public List<LocalDate> computeOccurrenceDates(RecurrenceRule rule, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();

        switch (rule.getFrequency()) {
            case WEEKLY -> collectStepped(rule, from, to, 7, dates);
            case BIWEEKLY -> collectStepped(rule, from, to, 14, dates);
            case MONTHLY -> collectMonthly(rule, from, to, 1, dates);
            case BIMONTHLY -> collectMonthly(rule, from, to, 2, dates);
            case QUARTERLY -> collectMonthly(rule, from, to, 3, dates);
            case FIRST_DAY_OF_MONTH -> collectMonthlyBoundary(rule, from, to, true, dates);
            case LAST_DAY_OF_MONTH -> collectMonthlyBoundary(rule, from, to, false, dates);
            case EVERY_N_DAYS -> collectEveryNDays(rule, from, to, dates);
        }

        return dates;
    }

    private void collectStepped(RecurrenceRule rule, LocalDate from, LocalDate to, int stepDays, List<LocalDate> dates) {
        LocalDate cursor = rule.getStartDate().with(TemporalAdjusters.nextOrSame(rule.getDayOfWeek()));

        while (cursor.isBefore(from)) {
            cursor = cursor.plusDays(stepDays);
        }

        while (!cursor.isAfter(to)) {
            dates.add(cursor);
            cursor = cursor.plusDays(stepDays);
        }
    }

    private void collectMonthly(RecurrenceRule rule, LocalDate from, LocalDate to, int stepMonths, List<LocalDate> dates) {
        int dayOfMonth = rule.getStartDate().getDayOfMonth();
        YearMonth cursor = YearMonth.from(rule.getStartDate());

        while (true) {
            LocalDate occurrence = clampToMonth(cursor, dayOfMonth);

            if (occurrence.isAfter(to)) {
                break;
            }

            if (!occurrence.isBefore(from)) {
                dates.add(occurrence);
            }

            cursor = cursor.plusMonths(stepMonths);
        }
    }

    private void collectMonthlyBoundary(RecurrenceRule rule, LocalDate from, LocalDate to, boolean firstDay, List<LocalDate> dates) {
        LocalDate rangeStart = from.isBefore(rule.getStartDate()) ? rule.getStartDate() : from;
        YearMonth cursor = YearMonth.from(rangeStart);

        while (!cursor.atDay(1).isAfter(to)) {
            LocalDate occurrence = firstDay ? cursor.atDay(1) : cursor.atEndOfMonth();

            if (rule.isAdjustForWeekend()) {
                occurrence = adjustToPreviousFriday(occurrence);
            }

            if (!occurrence.isBefore(rangeStart) && !occurrence.isAfter(to)) {
                dates.add(occurrence);
            }

            cursor = cursor.plusMonths(1);
        }
    }

    // Intervalo livre em dias corridos a partir da data de início, sem nenhum ajuste
    // de dia da semana — pode cair em qualquer dia, incluindo fins de semana.
    private void collectEveryNDays(RecurrenceRule rule, LocalDate from, LocalDate to, List<LocalDate> dates) {
        int intervalDays = rule.getIntervalDays();
        LocalDate cursor = rule.getStartDate();

        while (cursor.isBefore(from)) {
            cursor = cursor.plusDays(intervalDays);
        }

        while (!cursor.isAfter(to)) {
            dates.add(cursor);
            cursor = cursor.plusDays(intervalDays);
        }
    }

    private LocalDate clampToMonth(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private LocalDate adjustToPreviousFriday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.minusDays(2);
            default -> date;
        };
    }
}
