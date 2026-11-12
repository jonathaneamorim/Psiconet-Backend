package com.psiconet.services.implement.clinical;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.services.implement.financial.PaymentGenerationService;
import com.psiconet.services.implement.video.VideoRoomIds;
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
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final PaymentGenerationService paymentGenerationService;
    private final ScheduleOverlapService scheduleOverlapService;
    private final com.psiconet.repositories.access.UserRepository userRepository;

    @Scheduled(fixedRateString = "${psiconet.recurrence.generation-fixed-rate:1800000}")
    @Transactional
    public void ensureNextInstanceForAllActiveRules() {
        recurrenceRuleRepository.findByIsActiveTrue().forEach(this::ensureNextInstance);
    }

    @Transactional
    public void ensureNextInstance(RecurrenceRule candidate) {
        ensureNextInstance(candidate, true);
    }

    /**
     * @param notifyOverlap avisa o psicólogo se a ocorrência gerada coincidir com outra consulta. Falso só
     *                      quando ele acabou de confirmar a sobreposição na criação da série (ADR 019).
     */
    @Transactional
    public void ensureNextInstance(RecurrenceRule candidate, boolean notifyOverlap) {
        // Trava a regra no banco antes de conferir/gerar: chamadas simultâneas (job + aceite/criação pelo
        // usuário, ou duas instâncias da API) esperam aqui, e a segunda já enxerga a ocorrência criada.
        RecurrenceRule rule = recurrenceRuleRepository.findByIdForUpdate(candidate.getId()).orElse(null);
        if (rule == null || !Boolean.TRUE.equals(rule.getIsActive())) {
            return;
        }

        // Vínculo encerrado ou paciente sem conta ativa: não materializa novas consultas. A regra fica
        // como está (sem avançar o cursor) e volta a gerar se o vínculo/conta for reativado.
        if (!Boolean.TRUE.equals(rule.getTreatmentLink().getIsActive())
                || !PatientSchedulingGuard.canBeScheduled(rule.getTreatmentLink().getPatient().getUser())) {
            return;
        }

        if (appointmentRepository.existsByRecurrenceRuleAndStatusIn(rule, ACTIVE_STATUSES)) {
            return;
        }

        LocalDate from = rule.getLastGeneratedUntil() != null
                ? rule.getLastGeneratedUntil().plusDays(1)
                : rule.getStartDate();

        // Nunca materializa ocorrências passadas (ex.: série que ficou pausada com o paciente inativo).
        LocalDate today = LocalDate.now();
        if (from.isBefore(today)) {
            from = today;
        }

        LocalDate searchLimit = rule.getEndDate() != null ? rule.getEndDate() : from.plusYears(2);

        if (from.isAfter(searchLimit)) {
            return;
        }

        for (LocalDate date : computeOccurrenceDates(rule, from, searchLimit)) {
            rule.setLastGeneratedUntil(date);

            if (tryGenerateInstance(rule, date, notifyOverlap)) {
                recurrenceRuleRepository.save(rule);
                return;
            }
        }

        recurrenceRuleRepository.save(rule);
    }

    private boolean tryGenerateInstance(RecurrenceRule rule, LocalDate date, boolean notifyOverlap) {
        LocalDateTime start = date.atTime(rule.getStartTime());
        LocalDateTime end = date.atTime(rule.getEndTime());

        if (start.isBefore(LocalDateTime.now())) {
            return false;
        }

        // Mesmo mutex da criação manual (psicólogo): sem corrida que duplique o mesmo paciente no horário.
        userRepository.findByIdForUpdate(rule.getTreatmentLink().getPsychologist().getUser().getId());
        List<Appointment> conflicts = scheduleOverlapService.conflictsFor(rule.getTreatmentLink().getPsychologist(), start, end, null);

        // ADR 019: outro paciente no horário NÃO impede a ocorrência (antes ela era pulada em silêncio e a série
        // perdia a sessão); o psicólogo é avisado. Mesmo paciente no horário é duplicidade: essa data é pulada.
        if (scheduleOverlapService.includesPatient(conflicts, rule.getTreatmentLink().getPatient().getUser().getId())) {
            log.warn("Ocorrência da regra de recorrência {} pulada: o paciente já tem consulta em {}", rule.getId(), date);
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
        // Sala integrada: id novo e imprevisível em CADA ocorrência (nunca copiado da regra).
        if (rule.getMeetingProvider() == MeetingProviderEnum.IN_APP) {
            appointment.setMeetingLink(null);
            appointment.setRoomId(VideoRoomIds.newRoomId());
        }
        appointment.setLocation(rule.getLocation());
        appointment.setPrice(rule.getPrice() != null ? rule.getPrice() : rule.getTreatmentLink().getDefaultPrice());

        String title = (rule.getTitle() != null && !rule.getTitle().isBlank())
                ? rule.getTitle()
                : rule.getTreatmentLink().getPsychologist().getUser().getFullName()
                        + " X " + rule.getTreatmentLink().getPatient().getUser().getFullName();
        appointment.setTitle(title);

        appointment = appointmentRepository.save(appointment);
        paymentGenerationService.generatePaymentIfNeeded(appointment);

        // Uma notificação por ocorrência gerada (a geração é travada por regra e acontece uma vez por data).
        // Só data/hora: nunca o nome do outro paciente.
        if (!conflicts.isEmpty() && notifyOverlap) {
            notificationService.create(
                    rule.getTreatmentLink().getPsychologist().getUser(),
                    null,
                    NotificationType.SCHEDULE_OVERLAP,
                    "Horário com mais de uma consulta",
                    "A sessão recorrente de " + date.format(DATE_FORMATTER) + " às "
                            + start.toLocalTime().format(TIME_FORMATTER)
                            + " coincide com outra consulta da sua agenda.",
                    "APPOINTMENT",
                    appointment.getId()
            );
        }

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
    /** Datas/horários de início das ocorrências futuras da regra no intervalo (sem persistir). */
    public List<LocalDateTime> upcomingStarts(RecurrenceRule rule, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from.isBefore(rule.getStartDate()) ? rule.getStartDate() : from;
        LocalDate effectiveTo = rule.getEndDate() != null && rule.getEndDate().isBefore(to) ? rule.getEndDate() : to;
        if (effectiveFrom.isAfter(effectiveTo)) return List.of();
        LocalDateTime now = LocalDateTime.now();
        return computeOccurrenceDates(rule, effectiveFrom, effectiveTo).stream()
                .map(date -> date.atTime(rule.getStartTime()))
                .filter(start -> start.isAfter(now))
                .toList();
    }

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
