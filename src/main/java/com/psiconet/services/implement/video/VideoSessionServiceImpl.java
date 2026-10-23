package com.psiconet.services.implement.video;

import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.video.VideoAccessException;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.infra.video.VideoProvider;
import com.psiconet.infra.video.VideoWebhookEvent;
import com.psiconet.mapper.AppointmentMapper;
import com.psiconet.model.dtos.video.VideoConfigDTO;
import com.psiconet.model.dtos.video.VideoJoinResponseDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.clinical.VideoSessionLog;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.VideoSessionEventEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.VideoSessionLogRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.video.VideoSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Sala de vídeo integrada (ADR 016). O backend é a autoridade de quem entra, quando e com quais
 * permissões; o provedor só recebe um nome de sala opaco e o id do usuário — nunca nome, e-mail ou
 * dado clínico. Nada é gravado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSessionServiceImpl implements VideoSessionService {

    /** Sessão 1:1 — psicólogo e paciente do vínculo, ninguém mais. */
    public static final int MAX_PARTICIPANTS = 2;

    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final VideoSessionLogRepository logRepository;
    private final VideoProperties properties;
    private final VideoProvider videoProvider;
    private final VideoJoinWindow joinWindow;
    private final AppointmentMapper appointmentMapper;
    private final NotificationService notificationService;

    @Override
    public VideoConfigDTO getConfig() {
        return new VideoConfigDTO(properties.isAvailable());
    }

    @Override
    @Transactional
    public VideoJoinResponseDTO join(User user, UUID appointmentId) {
        Appointment appointment = findParticipatingAppointment(user, appointmentId);
        boolean isHost = isPsychologist(appointment, user);

        if (!joinWindow.appliesTo(appointment) || appointment.getRoomId() == null) {
            throw new VideoAccessException(400, VideoAccessException.NOT_IN_APP,
                    "Esta consulta não usa a sala de vídeo da Psiconet.");
        }
        if (!properties.isAvailable()) {
            throw VideoAccessException.unavailable();
        }

        AppointmentStatusEnum status = appointment.getStatus();
        // COMPLETED é aceito dentro da janela: o job de ciclo de vida conclui a consulta no horário de
        // fim, mas a sala segue aberta pela tolerância configurada.
        if (status != AppointmentStatusEnum.ACCEPTED && status != AppointmentStatusEnum.COMPLETED) {
            throw new VideoAccessException(409, VideoAccessException.APPOINTMENT_INACTIVE,
                    status == AppointmentStatusEnum.CANCELLED
                            ? "Esta consulta foi cancelada."
                            : "Esta consulta não está mais disponível para atendimento.");
        }
        if (!Boolean.TRUE.equals(appointment.getTreatmentLink().getIsActive())) {
            throw new VideoAccessException(409, VideoAccessException.APPOINTMENT_INACTIVE,
                    "O vínculo de tratamento desta consulta foi encerrado.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(joinWindow.opensAt(appointment))) {
            throw new VideoAccessException(409, VideoAccessException.WINDOW_NOT_OPEN,
                    "A sala abre em " + joinWindow.opensAt(appointment).format(WINDOW_FORMAT) + ".");
        }
        if (now.isAfter(joinWindow.closesAt(appointment))) {
            throw new VideoAccessException(409, VideoAccessException.WINDOW_CLOSED,
                    "O horário de entrada desta sessão já terminou.");
        }

        String roomName = VideoRoomIds.roomName(appointment.getRoomId());
        videoProvider.ensureRoom(roomName, MAX_PARTICIPANTS);
        VideoProvider.IssuedToken issued = videoProvider.issueToken(roomName, user.getId().toString(), MAX_PARTICIPANTS);

        RoleEnum role = isHost ? RoleEnum.PSYCHOLOGIST : RoleEnum.PATIENT;
        record(appointment.getId(), user.getId(), role, VideoSessionEventEnum.TOKEN_ISSUED, null, now);
        // Nunca loga o token: só quem pediu e para qual consulta.
        log.info("Credencial de vídeo emitida: consulta={} usuario={} papel={}", appointment.getId(), user.getId(), role);

        User remote = isHost
                ? appointment.getTreatmentLink().getPatient().getUser()
                : appointment.getTreatmentLink().getPsychologist().getUser();

        return VideoJoinResponseDTO.builder()
                .appointmentId(appointment.getId())
                .provider(videoProvider.name())
                .serverUrl(properties.getUrl())
                .token(issued.token())
                .tokenExpiresAt(issued.expiresAt())
                .identity(user.getId().toString())
                .role(isHost ? "HOST" : "GUEST")
                .remoteIdentity(remote.getId().toString())
                .remoteParticipant(appointmentMapper.toPersonSummaryDto(remote))
                .startDateTime(appointment.getStartDateTime())
                .endDateTime(appointment.getEndDateTime())
                .joinClosesAt(joinWindow.closesAt(appointment))
                .build();
    }

    @Override
    @Transactional
    public void end(User user, UUID appointmentId) {
        Appointment appointment = findParticipatingAppointment(user, appointmentId);
        if (!isPsychologist(appointment, user)) {
            throw new VideoAccessException(403, "FORBIDDEN", "Somente o psicólogo pode encerrar a sessão para todos.");
        }
        if (!joinWindow.appliesTo(appointment) || appointment.getRoomId() == null) {
            throw new VideoAccessException(400, VideoAccessException.NOT_IN_APP,
                    "Esta consulta não usa a sala de vídeo da Psiconet.");
        }
        if (!properties.isAvailable()) {
            throw VideoAccessException.unavailable();
        }

        videoProvider.closeRoom(VideoRoomIds.roomName(appointment.getRoomId()));
        record(appointment.getId(), user.getId(), RoleEnum.PSYCHOLOGIST, VideoSessionEventEnum.ROOM_CLOSED_BY_HOST,
                null, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void handleWebhook(byte[] rawBody, String authorizationHeader) {
        if (!properties.isAvailable()) {
            throw VideoAccessException.unavailable();
        }

        Optional<VideoWebhookEvent> parsed = videoProvider.parseWebhook(rawBody, authorizationHeader);
        if (parsed.isEmpty()) return;
        VideoWebhookEvent event = parsed.get();

        // Reentrega do mesmo evento pelo provedor: já registrado.
        if (event.eventId() != null && logRepository.existsByProviderEventId(event.eventId())) return;

        Optional<Appointment> found = VideoRoomIds.roomIdFromName(event.roomName())
                .flatMap(appointmentRepository::findByRoomId);
        if (found.isEmpty()) {
            log.warn("Webhook de vídeo para sala desconhecida ignorado.");
            return;
        }
        Appointment appointment = found.get();
        LocalDateTime occurredAt = LocalDateTime.ofInstant(event.occurredAt(), ZoneId.systemDefault());

        if (event.type() == VideoWebhookEvent.Type.ROOM_FINISHED) {
            record(appointment.getId(), null, null, VideoSessionEventEnum.ROOM_FINISHED, event.eventId(), occurredAt);
            return;
        }

        Optional<User> participant = participantByIdentity(appointment, event.participantIdentity());
        if (participant.isEmpty()) {
            // Não deveria acontecer (só a API emite tokens), mas nunca registra identidade estranha à consulta.
            log.warn("Webhook de vídeo com participante que não pertence à consulta {}.", appointment.getId());
            return;
        }
        User user = participant.get();
        boolean isHost = isPsychologist(appointment, user);
        RoleEnum role = isHost ? RoleEnum.PSYCHOLOGIST : RoleEnum.PATIENT;

        if (event.type() == VideoWebhookEvent.Type.PARTICIPANT_JOINED) {
            boolean firstJoin = !logRepository.existsByAppointmentIdAndUserIdAndEvent(
                    appointment.getId(), user.getId(), VideoSessionEventEnum.PARTICIPANT_JOINED);
            record(appointment.getId(), user.getId(), role, VideoSessionEventEnum.PARTICIPANT_JOINED, event.eventId(), occurredAt);

            // Avisa o paciente só na primeira entrada do psicólogo (reconexões não geram nova notificação).
            if (isHost && firstJoin) {
                notificationService.create(
                        appointment.getTreatmentLink().getPatient().getUser(),
                        user,
                        NotificationType.VIDEO_PARTICIPANT_JOINED,
                        "Seu psicólogo entrou na sala",
                        "A sala da sua sessão online já está aberta. Entre quando estiver pronto(a).",
                        "APPOINTMENT",
                        appointment.getId()
                );
            }
            return;
        }

        record(appointment.getId(), user.getId(), role, VideoSessionEventEnum.PARTICIPANT_LEFT, event.eventId(), occurredAt);
    }

    private Appointment findParticipatingAppointment(User user, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException(Appointment.class, appointmentId));
        TreatmentLink link = appointment.getTreatmentLink();
        boolean participates = link.getPsychologist().getUser().getId().equals(user.getId())
                || link.getPatient().getUser().getId().equals(user.getId());
        if (!participates) {
            // Mesma resposta de "não existe": não confirma consultas de terceiros (anti-IDOR).
            throw new EntityNotFoundException(Appointment.class, appointmentId);
        }
        return appointment;
    }

    private static boolean isPsychologist(Appointment appointment, User user) {
        return appointment.getTreatmentLink().getPsychologist().getUser().getId().equals(user.getId());
    }

    private static Optional<User> participantByIdentity(Appointment appointment, String identity) {
        if (identity == null) return Optional.empty();
        User psychologist = appointment.getTreatmentLink().getPsychologist().getUser();
        User patient = appointment.getTreatmentLink().getPatient().getUser();
        if (psychologist.getId().toString().equals(identity)) return Optional.of(psychologist);
        if (patient.getId().toString().equals(identity)) return Optional.of(patient);
        return Optional.empty();
    }

    private void record(UUID appointmentId, UUID userId, RoleEnum role, VideoSessionEventEnum event,
                        String providerEventId, LocalDateTime occurredAt) {
        VideoSessionLog entry = new VideoSessionLog();
        entry.setAppointmentId(appointmentId);
        entry.setUserId(userId);
        entry.setRole(role);
        entry.setEvent(event);
        entry.setProviderEventId(providerEventId);
        entry.setOccurredAt(occurredAt);
        entry.setRecordedAt(LocalDateTime.now());
        logRepository.save(entry);
    }
}
