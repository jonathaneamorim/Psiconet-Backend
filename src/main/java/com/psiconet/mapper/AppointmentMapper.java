package com.psiconet.mapper;

import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.services.implement.video.VideoJoinWindow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class AppointmentMapper {

    @Autowired
    protected VideoJoinWindow videoJoinWindow;

    @Mapping(target = "patient", expression = "java(toPersonSummaryDto(appointment.getTreatmentLink().getPatient().getUser()))")
    @Mapping(target = "psychologist", expression = "java(toPersonSummaryDto(appointment.getTreatmentLink().getPsychologist().getUser()))")
    @Mapping(target = "recurrenceRuleId", expression = "java(appointment.getRecurrenceRule() != null ? appointment.getRecurrenceRule().getId() : null)")
    @Mapping(target = "recurrenceFrequency", expression = "java(appointment.getRecurrenceRule() != null ? appointment.getRecurrenceRule().getFrequency() : null)")
    @Mapping(target = "joinOpensAt", expression = "java(joinOpensAt(appointment))")
    @Mapping(target = "joinClosesAt", expression = "java(joinClosesAt(appointment))")
    public abstract AppointmentDTO toDto(Appointment appointment);

    // A janela de entrada da sala integrada vem sempre do servidor (o front só exibe).
    protected LocalDateTime joinOpensAt(Appointment appointment) {
        return hasJoinWindow(appointment) ? videoJoinWindow.opensAt(appointment) : null;
    }

    protected LocalDateTime joinClosesAt(Appointment appointment) {
        return hasJoinWindow(appointment) ? videoJoinWindow.closesAt(appointment) : null;
    }

    private boolean hasJoinWindow(Appointment appointment) {
        return videoJoinWindow != null && videoJoinWindow.appliesTo(appointment);
    }

    public AppointmentDTO.PersonSummaryDTO toPersonSummaryDto(User user) {
        if (user == null) return null;
        return AppointmentDTO.PersonSummaryDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .photoUrl(user.getPhotoUrl())
                .build();
    }
}
