package com.psiconet.mapper;

import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "patient", expression = "java(toPersonSummaryDto(appointment.getTreatmentLink().getPatient().getUser()))")
    @Mapping(target = "psychologist", expression = "java(toPersonSummaryDto(appointment.getTreatmentLink().getPsychologist().getUser()))")
    @Mapping(target = "recurrenceRuleId", expression = "java(appointment.getRecurrenceRule() != null ? appointment.getRecurrenceRule().getId() : null)")
    @Mapping(target = "recurrenceFrequency", expression = "java(appointment.getRecurrenceRule() != null ? appointment.getRecurrenceRule().getFrequency() : null)")
    AppointmentDTO toDto(Appointment appointment);

    default AppointmentDTO.PersonSummaryDTO toPersonSummaryDto(User user) {
        if (user == null) return null;
        return AppointmentDTO.PersonSummaryDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .photoUrl(user.getPhotoUrl())
                .build();
    }
}
