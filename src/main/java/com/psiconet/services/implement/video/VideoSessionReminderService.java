package com.psiconet.services.implement.video;

import com.psiconet.infra.mail.EmailService;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.services.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lembrete das sessões na sala integrada (~15 min antes): notificação no site + e-mail para psicólogo
 * e paciente, respeitando as preferências de "agendamento". Cada consulta é marcada de forma atômica
 * antes do envio, então reexecuções (ou mais de uma instância) não duplicam o lembrete.
 * Sem conteúdo clínico e sem o nome da outra parte.
 */
@Service
@RequiredArgsConstructor
public class VideoSessionReminderService {

    private static final DateTimeFormatter START_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final VideoProperties properties;

    @Scheduled(fixedRateString = "${psiconet.video.reminder-fixed-rate:60000}")
    @Transactional
    public void sendDueReminders() {
        if (!properties.isAvailable()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plus(properties.getReminderBefore());

        for (Appointment appointment : appointmentRepository.findInAppVideoNeedingReminder(now, until)) {
            if (appointmentRepository.markVideoReminderSent(appointment.getId(), now) == 0) {
                continue;
            }
            String startsAt = appointment.getStartDateTime().format(START_FORMAT);
            remind(appointment.getTreatmentLink().getPsychologist().getUser(), appointment, startsAt);
            remind(appointment.getTreatmentLink().getPatient().getUser(), appointment, startsAt);
        }
    }

    private void remind(User recipient, Appointment appointment, String startsAt) {
        notificationService.create(
                recipient,
                null,
                NotificationType.VIDEO_SESSION_REMINDER,
                "Sua sessão online começa em breve",
                "Sua sessão na sala da Psiconet começa em " + startsAt + ". A sala abre alguns minutos antes.",
                "APPOINTMENT",
                appointment.getId()
        );
        if (notificationService.isEnabled(recipient, NotificationType.VIDEO_SESSION_REMINDER)) {
            emailService.sendVideoSessionReminder(recipient.getEmail(), recipient.getFullName(), startsAt,
                    "/call/" + appointment.getId());
        }
    }
}
