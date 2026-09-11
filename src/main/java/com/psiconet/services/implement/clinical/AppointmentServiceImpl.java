package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.AppointmentMapper;
import com.psiconet.model.dtos.clinical.AppointmentCancelDTO;
import com.psiconet.model.dtos.clinical.AppointmentCreateDTO;
import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.dtos.clinical.AppointmentStatsDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.AppointmentCancelScopeEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.financial.PaymentRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.implement.financial.PaymentGenerationService;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.clinical.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private static final long DEFAULT_DURATION_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final TreatmentLinkRepository treatmentLinkRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final RecurrenceGenerationService recurrenceGenerationService;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final PsychologistRepository psychologistRepository;
    private final UserRepository userRepository;
    private final AppointmentMapper appointmentMapper;
    private final NotificationService notificationService;
    private final PaymentGenerationService paymentGenerationService;

    @Override
    @Transactional
    public AppointmentDTO create(User psychologistUser, AppointmentCreateDTO dto) {
        // Retorna erro caso não seja o psicologo a criar o agendamento
        if (psychologistUser.getRole() != RoleEnum.PSYCHOLOGIST) {
            throw new BusinessException("appointment", "Somente psicólogos podem criar agendamentos.");
        }

        Psychologist psychologist = psychologistRepository.findByUser(psychologistUser)
                .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, psychologistUser.getId()));

        Patient patient = patientRepository.findByUserId(dto.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException(Patient.class, dto.getPatientId()));

        TreatmentLink treatmentLink = treatmentLinkRepository
                .findByPatientAndPsychologistAndIsActiveTrue(patient, psychologist)
                .orElseThrow(() -> new BusinessException(
                        "patientId",
                        "Você só pode agendar consultas com pacientes com quem tenha um vínculo de tratamento ativo."
                ));

        if (dto.getStartDateTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("startDateTime", "A data e hora de início não podem estar no passado.");
        }

        LocalDateTime endDateTime = dto.getEndDateTime() != null
                ? dto.getEndDateTime()
                : dto.getStartDateTime().plusMinutes(DEFAULT_DURATION_MINUTES);

        if (!endDateTime.isAfter(dto.getStartDateTime())) {
            throw new BusinessException("endDateTime", "A data e hora de fim devem ser posteriores ao início.");
        }

        String title = (dto.getTitle() != null && !dto.getTitle().isBlank())
                ? dto.getTitle()
                : psychologistUser.getFullName() + " X " + patient.getUser().getFullName();

        validateMeetingFields(dto);

        // Trava o psicólogo como mutex antes de checar conflito de horário: sem isso, duas requisições
        // concorrentes de criação de agendamento para o mesmo psicólogo poderiam ambas ler "sem
        // conflito" e ambas inserir, resultando em dois agendamentos sobrepostos (race condition
        // clássica de check-then-insert / phantom read).
        userRepository.findByIdForUpdate(psychologistUser.getId());

        boolean hasConflict = !appointmentRepository
                .findConflicting(psychologist, dto.getStartDateTime(), endDateTime)
                .isEmpty();

        if (hasConflict) {
            throw new BusinessException("startDateTime", "Já existe um agendamento neste horário.");
        }

        Appointment appointment = new Appointment();
        appointment.setTreatmentLink(treatmentLink);
        appointment.setTitle(title);
        appointment.setDescription(dto.getDescription());
        appointment.setStartDateTime(dto.getStartDateTime());
        appointment.setEndDateTime(endDateTime);
        appointment.setMeetingType(dto.getMeetingType());
        // Nasce direto como ACCEPTED: já existe vínculo de tratamento ativo entre psicólogo e
        // paciente, então exigir um aceite explícito do paciente era redundante — e, pior, consultas
        // não respondidas a tempo eram canceladas automaticamente (ver AppointmentLifecycleService),
        // fazendo o valor delas sumir do "a receber" mesmo quando a consulta de fato aconteceu.
        appointment.setStatus(AppointmentStatusEnum.ACCEPTED);

        BigDecimal price = dto.getPrice() != null ? dto.getPrice() : treatmentLink.getDefaultPrice();
        if (price == null) {
            throw new BusinessException(
                    "price",
                    "Informe o valor da consulta: este vínculo de tratamento não possui um preço padrão definido."
            );
        }
        appointment.setPrice(price);

        if (dto.getMeetingType() == MeetingTypeEnum.VIDEO_CALL) {
            appointment.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
            appointment.setMeetingLink(dto.getMeetingLink());
        } else {
            appointment.setLocation(dto.getLocation());
        }

        appointment = appointmentRepository.save(appointment);
        paymentGenerationService.generatePaymentIfNeeded(appointment);

        notificationService.create(
                patient.getUser(),
                psychologist.getUser(),
                NotificationType.APPOINTMENT_SCHEDULED,
                "Nova Consulta Agendada",
                psychologist.getUser().getFullName() + " agendou uma consulta com você.",
                "APPOINTMENT",
                appointment.getId()
        );

        return appointmentMapper.toDto(appointment);
    }

    @Override
    @Transactional
    public AppointmentDTO cancel(User user, UUID appointmentId, AppointmentCancelDTO dto) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);
        TreatmentLink treatmentLink = appointment.getTreatmentLink();

        boolean isPsychologist = treatmentLink.getPsychologist().getUser().getId().equals(user.getId());
        boolean isPatient = treatmentLink.getPatient().getUser().getId().equals(user.getId());

        if (!isPsychologist && !isPatient) {
            throw new BusinessException("appointment", "Você não tem permissão para cancelar este agendamento.");
        }

        if (appointment.getStatus() != AppointmentStatusEnum.ACCEPTED) {
            throw new BusinessException("appointment", "Este agendamento não pode mais ser cancelado.");
        }

        if (!appointment.getStartDateTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("appointment", "Não é possível cancelar uma consulta em que o horário já passou.");
        }

        if (isPatient && (dto == null || dto.getReason() == null || dto.getReason().isBlank())) {
            throw new BusinessException("reason", "Informe o motivo do cancelamento.");
        }

        AppointmentCancelScopeEnum scope = (dto != null && dto.getCancelScope() != null)
                ? dto.getCancelScope()
                : AppointmentCancelScopeEnum.SINGLE;

        if (appointment.getRecurrenceRule() == null && scope != AppointmentCancelScopeEnum.SINGLE) {
            throw new BusinessException("cancelScope", "Esta consulta não faz parte de uma série recorrente.");
        }

        if (scope != AppointmentCancelScopeEnum.SINGLE && !isPsychologist) {
            throw new BusinessException(
                    "cancelScope",
                    "Apenas o psicólogo pode cancelar toda a série ou as próximas ocorrências."
            );
        }

        RoleEnum cancelledBy = isPatient ? RoleEnum.PATIENT : RoleEnum.PSYCHOLOGIST;
        String reason = dto != null ? dto.getReason() : null;

        cancelSingle(appointment, cancelledBy, reason);

        if (scope == AppointmentCancelScopeEnum.THIS_AND_FOLLOWING) {
            RecurrenceRule rule = appointment.getRecurrenceRule();
            cancelFutureInstances(rule, appointment.getStartDateTime(), cancelledBy, reason);
            rule.setEndDate(appointment.getStartDateTime().toLocalDate());
            recurrenceRuleRepository.save(rule);
        } else if (scope == AppointmentCancelScopeEnum.ALL_SERIES) {
            RecurrenceRule rule = appointment.getRecurrenceRule();
            cancelFutureInstances(rule, LocalDateTime.now(), cancelledBy, reason);
            rule.setIsActive(false);
            recurrenceRuleRepository.save(rule);
        } else if (scope == AppointmentCancelScopeEnum.SINGLE && appointment.getRecurrenceRule() != null) {
            // Mantém a série viva: assim que esta instância é cancelada, já materializa a próxima.
            recurrenceGenerationService.ensureNextInstance(appointment.getRecurrenceRule());
        }

        return appointmentMapper.toDto(appointment);
    }

    private void cancelSingle(Appointment appointment, RoleEnum cancelledBy, String reason) {
        appointment.setCancelledBy(cancelledBy);
        appointment.setCancellationReason(reason);
        appointment.setStatus(AppointmentStatusEnum.CANCELLED);
        appointmentRepository.save(appointment);

        paymentRepository.findByAppointment(appointment).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatusEnum.APPROVED && payment.getStatus() != PaymentStatusEnum.CANCELLED) {
                payment.setStatus(PaymentStatusEnum.CANCELLED);
                paymentRepository.save(payment);
            }
        });
    }

    private void cancelFutureInstances(RecurrenceRule rule, LocalDateTime threshold, RoleEnum cancelledBy, String reason) {
        List<Appointment> future = appointmentRepository.findByRecurrenceRuleAndStartDateTimeAfterAndStatusIn(
                rule, threshold, List.of(AppointmentStatusEnum.ACCEPTED)
        );

        future.forEach(instance -> cancelSingle(instance, cancelledBy, reason));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentDTO> listMyAppointments(User user, Pageable pageable) {
        if (user.getRole() == RoleEnum.PSYCHOLOGIST) {
            Psychologist psychologist = psychologistRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, user.getId()));
            return appointmentRepository
                    .findByTreatmentLink_PsychologistOrderByStartDateTimeAsc(psychologist, pageable)
                    .map(appointmentMapper::toDto);
        }

        if (user.getRole() == RoleEnum.PATIENT) {
            Patient patient = patientRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Patient.class, user.getId()));
            return appointmentRepository
                    .findByTreatmentLink_PatientOrderByStartDateTimeAsc(patient, pageable)
                    .map(appointmentMapper::toDto);
        }

        throw new BusinessException("appointment", "Apenas psicólogos e pacientes possuem agendamentos.");
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentStatsDTO getMonthlyStats(User user, int year, int month) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        List<Appointment> appointments;

        if (user.getRole() == RoleEnum.PSYCHOLOGIST) {
            Psychologist psychologist = psychologistRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, user.getId()));
            appointments = appointmentRepository.findByTreatmentLink_PsychologistAndStartDateTimeBetween(psychologist, start, end);
        } else if (user.getRole() == RoleEnum.PATIENT) {
            Patient patient = patientRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Patient.class, user.getId()));
            appointments = appointmentRepository.findByTreatmentLink_PatientAndStartDateTimeBetween(patient, start, end);
        } else {
            throw new BusinessException("appointment", "Apenas psicólogos e pacientes possuem agendamentos.");
        }

        Map<AppointmentStatusEnum, Long> counts = appointments.stream()
                .collect(Collectors.groupingBy(Appointment::getStatus, Collectors.counting()));

        return AppointmentStatsDTO.builder()
                .accepted(counts.getOrDefault(AppointmentStatusEnum.ACCEPTED, 0L))
                .completed(counts.getOrDefault(AppointmentStatusEnum.COMPLETED, 0L))
                .cancelled(counts.getOrDefault(AppointmentStatusEnum.CANCELLED, 0L))
                .noShow(counts.getOrDefault(AppointmentStatusEnum.NO_SHOW, 0L))
                .build();
    }

    private void validateMeetingFields(AppointmentCreateDTO dto) {
        MeetingFieldsValidator.validate(dto.getMeetingType(), dto.getMeetingLink(), dto.getLocation());
    }

    private Appointment findAppointmentOrThrow(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException(Appointment.class, appointmentId));
    }
}
