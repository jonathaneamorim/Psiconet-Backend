package com.psiconet.services.interfaces.clinical;

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
    Page<AppointmentDTO> listMyAppointments(User user, Pageable pageable);
    AppointmentStatsDTO getMonthlyStats(User user, int year, int month);
}
