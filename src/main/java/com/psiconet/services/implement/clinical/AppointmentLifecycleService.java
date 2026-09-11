package com.psiconet.services.implement.clinical;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Faz a transição de consultas cujo horário já passou para {@code COMPLETED}. Essa transição
 * é o gatilho para {@link RecurrenceGenerationService} materializar a próxima instância de uma
 * série — enquanto a consulta atual estiver {@code ACCEPTED}, a próxima não é gerada.
 */
@Service
@RequiredArgsConstructor
public class AppointmentLifecycleService {

    private final AppointmentRepository appointmentRepository;
    private final RecurrenceGenerationService recurrenceGenerationService;

    @Scheduled(fixedRateString = "${psiconet.appointment.lifecycle-fixed-rate:300000}")
    @Transactional
    public void processExpiredAppointments() {
        LocalDateTime now = LocalDateTime.now();

        appointmentRepository.findAcceptedCompletable(now).forEach(appointment -> {
            appointment.setStatus(AppointmentStatusEnum.COMPLETED);
            appointmentRepository.save(appointment);
            advanceRecurrence(appointment);
        });
    }

    private void advanceRecurrence(Appointment appointment) {
        if (appointment.getRecurrenceRule() != null) {
            recurrenceGenerationService.ensureNextInstance(appointment.getRecurrenceRule());
        }
    }
}
