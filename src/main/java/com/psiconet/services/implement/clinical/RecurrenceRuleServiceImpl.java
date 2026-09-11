package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.RecurrenceRuleMapper;
import com.psiconet.model.dtos.clinical.RecurrenceRuleCreateDTO;
import com.psiconet.model.dtos.clinical.RecurrenceRuleDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
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

    @Override
    @Transactional
    public RecurrenceRuleDTO create(User psychologistUser, UUID treatmentLinkId, RecurrenceRuleCreateDTO dto) {
        if (psychologistUser.getRole() != RoleEnum.PSYCHOLOGIST) {
            throw new BusinessException("recurrenceRule", "Somente psicólogos podem criar regras de recorrência.");
        }

        TreatmentLink treatmentLink = treatmentLinkRepository.findById(treatmentLinkId)
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, treatmentLinkId));

        if (!treatmentLink.getPsychologist().getUser().getId().equals(psychologistUser.getId())) {
            throw new BusinessException("recurrenceRule", "Você não tem permissão para criar regras neste vínculo.");
        }

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

        MeetingFieldsValidator.validate(dto.getMeetingType(), dto.getMeetingLink(), dto.getLocation());

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
        rule.setMeetingLink(dto.getMeetingLink());
        rule.setLocation(dto.getLocation());
        rule.setPrice(dto.getPrice());
        rule.setAdjustForWeekend(dto.isAdjustForWeekend());
        rule.setIsActive(true);

        if (dto.getMeetingType() == MeetingTypeEnum.VIDEO_CALL) {
            rule.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
        }

        rule = recurrenceRuleRepository.save(rule);

        recurrenceGenerationService.ensureNextInstance(rule);

        return recurrenceRuleMapper.toDto(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RecurrenceRuleDTO> list(User user, UUID treatmentLinkId, Pageable pageable) {
        TreatmentLink treatmentLink = treatmentLinkRepository.findById(treatmentLinkId)
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, treatmentLinkId));

        boolean isPsychologist = treatmentLink.getPsychologist().getUser().getId().equals(user.getId());
        boolean isPatient = treatmentLink.getPatient().getUser().getId().equals(user.getId());

        if (!isPsychologist && !isPatient) {
            throw new BusinessException("recurrenceRule", "Você não tem permissão para visualizar as regras deste vínculo.");
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
            throw new BusinessException("recurrenceRule", "Você não tem permissão para desativar esta regra.");
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
            throw new BusinessException("recurrenceRule", "Você não tem permissão para visualizar esta regra.");
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
