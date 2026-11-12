package com.psiconet.services.interfaces.clinical;

import java.util.List;
import java.time.LocalDateTime;
import com.psiconet.model.dtos.clinical.OverlapSummaryDTO;
import com.psiconet.model.dtos.clinical.AppointmentCancelDTO;
import com.psiconet.model.dtos.clinical.AppointmentCreateDTO;
import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.dtos.clinical.AppointmentStatsDTO;
import com.psiconet.model.entities.access.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AppointmentService {
    AppointmentDTO create(User psychologistUser, AppointmentCreateDTO dto);
    AppointmentDTO cancel(User user, UUID appointmentId, AppointmentCancelDTO dto);
    AppointmentDTO getById(User user, UUID appointmentId);
    Page<AppointmentDTO> listMyAppointments(User user, Pageable pageable);
    AppointmentStatsDTO getMonthlyStats(User user, int year, int month);
    /** Consultas do psicólogo que cruzam o intervalo — pré-checagem do formulário (ADR 019). */
    List<OverlapSummaryDTO> findOverlaps(User psychologistUser, LocalDateTime start, LocalDateTime end, UUID excludeId);
}
