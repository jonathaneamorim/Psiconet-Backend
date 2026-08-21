package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.AppointmentMapper;
import com.psiconet.model.dtos.clinical.AppointmentCancelDTO;
import com.psiconet.model.dtos.clinical.AppointmentCreateDTO;
import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.email.EmailDetails;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.clinical.AppointmentService;
import com.psiconet.services.interfaces.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private static final long DEFAULT_DURATION_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final TreatmentLinkRepository treatmentLinkRepository;
    private final PatientRepository patientRepository;
    private final PsychologistRepository psychologistRepository;
    private final AppointmentMapper appointmentMapper;
    private final EmailService emailService;

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
        appointment.setStatus(AppointmentStatusEnum.SCHEDULED);

        emailService.sendMailAppointment(appointment);

        if (dto.getMeetingType() == MeetingTypeEnum.VIDEO_CALL) {
            appointment.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
            appointment.setMeetingLink(dto.getMeetingLink());
        } else {
            appointment.setLocation(dto.getLocation());
        }

        appointment = appointmentRepository.save(appointment);

        return appointmentMapper.toDto(appointment);
    }

    @Override
    @Transactional
    public AppointmentDTO accept(User patientUser, UUID appointmentId) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);

        Patient patient = patientRepository.findByUser(patientUser)
                .orElseThrow(() -> new EntityNotFoundException(Patient.class, patientUser.getId()));

        // Se o usuario quiser recusar um encontro que não é dele
        if (!appointment.getTreatmentLink().getPatient().getId().equals(patient.getId())) {
            throw new BusinessException("appointment", "Você não tem permissão para responder a este agendamento.");
        }

        // Se o usuario tentar aceitar agendamentos que nao sejam pendentes
        if (appointment.getStatus() != AppointmentStatusEnum.SCHEDULED) {
            throw new BusinessException("appointment", "Apenas agendamentos pendentes podem ser aceitos.");
        }

        emailService.sendMailConfirmationAppointment(appointment);
        appointment.setStatus(AppointmentStatusEnum.ACCEPTED);
        appointment = appointmentRepository.save(appointment);

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

        if (appointment.getStatus() != AppointmentStatusEnum.SCHEDULED
                && appointment.getStatus() != AppointmentStatusEnum.ACCEPTED) {
            throw new BusinessException("appointment", "Este agendamento não pode mais ser cancelado.");
        }

        if (!appointment.getStartDateTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("appointment", "Não é possível cancelar uma consulta em que o horário já passou.");
        }

        if (isPatient) {
            if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
                throw new BusinessException("reason", "Informe o motivo do cancelamento.");
            }
            appointment.setCancelledBy(RoleEnum.PATIENT);
        } else {
            appointment.setCancelledBy(RoleEnum.PSYCHOLOGIST);
        }

        appointment.setStatus(AppointmentStatusEnum.CANCELLED);
        appointment.setCancellationReason(dto != null ? dto.getReason() : null);

        appointment = appointmentRepository.save(appointment);

        return appointmentMapper.toDto(appointment);
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

    private void validateMeetingFields(AppointmentCreateDTO dto) {
        if (dto.getMeetingType() == MeetingTypeEnum.VIDEO_CALL
                && (dto.getMeetingLink() == null || dto.getMeetingLink().isBlank())) {
            throw new BusinessException("meetingLink", "Informe o link da chamada de vídeo.");
        }

        if (dto.getMeetingType() == MeetingTypeEnum.IN_PERSON && dto.getLocation() == null) {
            throw new BusinessException("location", "Informe o endereço da consulta presencial.");
        }
    }

    private Appointment findAppointmentOrThrow(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException(Appointment.class, appointmentId));
    }
}
