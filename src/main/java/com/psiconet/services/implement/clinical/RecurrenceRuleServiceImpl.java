package com.psiconet.services.implement.clinical;

import java.util.ArrayList;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.dtos.clinical.RecurrenceOccurrenceOverlapDTO;
import org.springframework.security.access.AccessDeniedException;
import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.mapper.RecurrenceRuleMapper;
import com.psiconet.model.dtos.clinical.RecurrenceRuleCreateDTO;
import com.psiconet.model.dtos.clinical.RecurrenceRuleDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.services.interfaces.clinical.RecurrenceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurrenceRuleServiceImpl implements RecurrenceRuleService {

    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final TreatmentLinkRepository treatmentLinkRepository;
    private final RecurrenceRuleMapper recurrenceRuleMapper;
    private final RecurrenceGenerationService recurrenceGenerationService;
    private final VideoProperties videoProperties;
    private final ScheduleOverlapService scheduleOverlapService;

    // Rascunho do formulário: verifica as ocorrências dos próximos 90 dias.
    private static final int DRAFT_WINDOW_DAYS = 90;

    @Override
    @Transactional
    public RecurrenceRuleDTO create(User psychologistUser, UUID treatmentLinkId, RecurrenceRuleCreateDTO dto) {
        RecurrenceRule rule = buildValidatedRule(psychologistUser, treatmentLinkId, dto);

        // ADR 019: a 1ª ocorrência (gerada agora) segue a regra da consulta avulsa — outro paciente no horário
        // exige confirmação; o mesmo paciente bloqueia. As seguintes são geradas e sinalizadas pelo job.
        List<Appointment> firstConflicts = recurrenceGenerationService.upcomingStarts(rule, LocalDate.now(), LocalDate.now().plusYears(2))
                .stream().findFirst()
                .map(start -> scheduleOverlapService.conflictsFor(rule.getTreatmentLink().getPsychologist(),
                        start, start.toLocalDate().atTime(rule.getEndTime()), null))
                .orElse(List.of());
        scheduleOverlapService.requireCreatable(firstConflicts, rule.getTreatmentLink().getPatient().getUser().getId(),
                dto.isAcknowledgeOverlap(), "startDate");

        RecurrenceRule saved = recurrenceRuleRepository.save(rule);

        // Sobreposição da 1ª ocorrência já foi confirmada pelo psicólogo: sem notificação redundante.
        recurrenceGenerationService.ensureNextInstance(saved, firstConflicts.isEmpty());

        return recurrenceRuleMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecurrenceOccurrenceOverlapDTO> draftOverlaps(User psychologistUser, UUID treatmentLinkId, RecurrenceRuleCreateDTO dto) {
        RecurrenceRule draft = buildValidatedRule(psychologistUser, treatmentLinkId, dto);
        LocalDate from = LocalDate.now();
        return overlapsOf(draft, from, from.plusDays(DRAFT_WINDOW_DAYS));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecurrenceOccurrenceOverlapDTO> previewOverlaps(User psychologistUser, UUID ruleId, LocalDate from, LocalDate to) {
        RecurrenceRule rule = recurrenceRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException(RecurrenceRule.class, ruleId));
        if (!rule.getTreatmentLink().getPsychologist().getUser().getId().equals(psychologistUser.getId())) {
            throw new EntityNotFoundException(RecurrenceRule.class, ruleId);
        }
        if (!to.isAfter(from)) {
            throw new BusinessException("to", "A data final deve ser posterior à data inicial.");
        }
        if (!Boolean.TRUE.equals(rule.getIsActive())) {
            return List.of();
        }
        return overlapsOf(rule, from, to);
    }

    // Ocorrências futuras que coincidem com outra consulta (exceto as da própria série).
    private List<RecurrenceOccurrenceOverlapDTO> overlapsOf(RecurrenceRule rule, LocalDate from, LocalDate to) {
        List<RecurrenceOccurrenceOverlapDTO> result = new ArrayList<>();
        for (LocalDateTime start : recurrenceGenerationService.upcomingStarts(rule, from, to)) {
            LocalDateTime end = start.toLocalDate().atTime(rule.getEndTime());
            List<Appointment> conflicts = scheduleOverlapService.conflictsFor(rule.getTreatmentLink().getPsychologist(), start, end, null)
                    .stream()
                    .filter(a -> a.getRecurrenceRule() == null || rule.getId() == null || !rule.getId().equals(a.getRecurrenceRule().getId()))
                    .toList();
            if (!conflicts.isEmpty()) {
                result.add(new RecurrenceOccurrenceOverlapDTO(start, end, scheduleOverlapService.summarize(conflicts)));
            }
        }
        return result;
    }

    /** Valida o pedido (papel, dono, vínculo, frequência, limites, reunião) e monta a regra sem persistir. */
    private RecurrenceRule buildValidatedRule(User psychologistUser, UUID treatmentLinkId, RecurrenceRuleCreateDTO dto) {
        if (psychologistUser.getRole() != RoleEnum.PSYCHOLOGIST) {
            throw new AccessDeniedException("Somente psicólogos podem criar regras de recorrência.");
        }

        TreatmentLink treatmentLink = treatmentLinkRepository.findById(treatmentLinkId)
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, treatmentLinkId));

        if (!treatmentLink.getPsychologist().getUser().getId().equals(psychologistUser.getId())) {
            throw new EntityNotFoundException(TreatmentLink.class, treatmentLinkId);
        }

        if (!Boolean.TRUE.equals(treatmentLink.getIsActive())) {
            throw new BusinessException(
                    "treatmentLinkId",
                    "Você só pode agendar consultas com pacientes com quem tenha um vínculo de tratamento ativo."
            );
        }

        PatientSchedulingGuard.requireSchedulable(treatmentLink.getPatient().getUser());

        // Um mesmo vínculo pode ter mais de uma regra ativa simultaneamente
        // (ex.: paciente e psicólogo se encontram 2x por semana, em dias diferentes).

        boolean isWeekBased = dto.getFrequency() == RecurrenceFrequencyEnum.WEEKLY
                || dto.getFrequency() == RecurrenceFrequencyEnum.BIWEEKLY;

        if (isWeekBased && dto.getDayOfWeek() == null) {
            throw new BusinessException("dayOfWeek", "Informe o dia da semana para essa frequência.");
        }

        if (isWeekBased && dto.getStartDate().getDayOfWeek() != dto.getDayOfWeek()) {
            throw new BusinessException(
                    "startDate",
                    "A data de início deve cair no dia da semana selecionado (" + dto.getDayOfWeek() + ")."
            );
        }

        boolean supportsWeekendAdjustment = dto.getFrequency() == RecurrenceFrequencyEnum.FIRST_DAY_OF_MONTH
                || dto.getFrequency() == RecurrenceFrequencyEnum.LAST_DAY_OF_MONTH;

        if (dto.isAdjustForWeekend() && !supportsWeekendAdjustment) {
            throw new BusinessException(
                    "adjustForWeekend",
                    "O ajuste de fim de semana só se aplica às frequências de primeiro/último dia do mês."
            );
        }

        boolean isEveryNDays = dto.getFrequency() == RecurrenceFrequencyEnum.EVERY_N_DAYS;

        if (isEveryNDays && (dto.getIntervalDays() == null || dto.getIntervalDays() < 1)) {
            throw new BusinessException("intervalDays", "Informe o intervalo em dias (maior que zero).");
        }

        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("endTime", "O horário de fim deve ser posterior ao início.");
        }
        AppointmentLimits.requireDuration(dto.getStartDate().atTime(dto.getStartTime()), dto.getStartDate().atTime(dto.getEndTime()), "endTime");
        AppointmentLimits.requireWithinHorizon(dto.getStartDate(), "startDate");

        MeetingFieldsValidator.MeetingFields meeting = MeetingFieldsValidator.validate(
                dto.getMeetingType(), dto.getMeetingProvider(), dto.getMeetingLink(), dto.getLocation(),
                videoProperties.isAvailable());

        if (dto.getPrice() == null && treatmentLink.getDefaultPrice() == null) {
            throw new BusinessException(
                    "price",
                    "Informe o valor da consulta: este vínculo de tratamento não possui um preço padrão definido."
            );
        }

        RecurrenceRule rule = new RecurrenceRule();
        rule.setTreatmentLink(treatmentLink);
        rule.setFrequency(dto.getFrequency());
        rule.setDayOfWeek(isWeekBased ? dto.getDayOfWeek() : null);
        rule.setIntervalDays(isEveryNDays ? dto.getIntervalDays() : null);
        rule.setStartTime(dto.getStartTime());
        rule.setEndTime(dto.getEndTime());
        rule.setStartDate(dto.getStartDate());
        rule.setEndDate(dto.getEndDate());
        rule.setTitle(dto.getTitle());
        rule.setMeetingType(dto.getMeetingType());
        rule.setMeetingLink(meeting.meetingLink());
        rule.setLocation(dto.getMeetingType() == MeetingTypeEnum.IN_PERSON ? dto.getLocation() : null);
        rule.setPrice(dto.getPrice());
        rule.setAdjustForWeekend(dto.isAdjustForWeekend());
        rule.setIsActive(true);

        // IN_APP: a regra NÃO guarda sala — cada ocorrência materializada recebe um roomId próprio.
        rule.setMeetingProvider(meeting.provider());

        return rule;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RecurrenceRuleDTO> list(User user, UUID treatmentLinkId, Pageable pageable) {
        TreatmentLink treatmentLink = treatmentLinkRepository.findById(treatmentLinkId)
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, treatmentLinkId));

        boolean isPsychologist = treatmentLink.getPsychologist().getUser().getId().equals(user.getId());
        boolean isPatient = treatmentLink.getPatient().getUser().getId().equals(user.getId());

        if (!isPsychologist && !isPatient) {
            throw new EntityNotFoundException(TreatmentLink.class, treatmentLinkId);
        }

        return recurrenceRuleRepository
                .findByTreatmentLinkOrderByStartDateDesc(treatmentLink, pageable)
                .map(recurrenceRuleMapper::toDto);
    }

    @Override
    @Transactional
    public void deactivate(User psychologistUser, UUID ruleId) {
        RecurrenceRule rule = recurrenceRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException(RecurrenceRule.class, ruleId));

        if (!rule.getTreatmentLink().getPsychologist().getUser().getId().equals(psychologistUser.getId())) {
            // Paciente do vínculo conhece a regra (403); qualquer outro usuário não deve saber que ela existe (404).
            if (rule.getTreatmentLink().getPatient().getUser().getId().equals(psychologistUser.getId())) {
                throw new AccessDeniedException("Somente o psicólogo do vínculo pode desativar esta regra.");
            }
            throw new EntityNotFoundException(RecurrenceRule.class, ruleId);
        }

        rule.setIsActive(false);
        recurrenceRuleRepository.save(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDateTime> previewOccurrences(User user, UUID ruleId, LocalDate from, LocalDate to) {
        RecurrenceRule rule = recurrenceRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException(RecurrenceRule.class, ruleId));

        boolean isPsychologist = rule.getTreatmentLink().getPsychologist().getUser().getId().equals(user.getId());
        boolean isPatient = rule.getTreatmentLink().getPatient().getUser().getId().equals(user.getId());

        if (!isPsychologist && !isPatient) {
            throw new EntityNotFoundException(RecurrenceRule.class, ruleId);
        }

        if (!to.isAfter(from)) {
            throw new BusinessException("to", "A data final deve ser posterior à data inicial.");
        }

        if (!Boolean.TRUE.equals(rule.getIsActive())) {
            return List.of();
        }

        LocalDate effectiveFrom = from.isBefore(rule.getStartDate()) ? rule.getStartDate() : from;
        LocalDate effectiveTo = (rule.getEndDate() != null && rule.getEndDate().isBefore(to)) ? rule.getEndDate() : to;

        if (effectiveFrom.isAfter(effectiveTo)) {
            return List.of();
        }

        return recurrenceGenerationService.computeOccurrenceDates(rule, effectiveFrom, effectiveTo).stream()
                .map(date -> date.atTime(rule.getStartTime()))
                .toList();
    }
}
