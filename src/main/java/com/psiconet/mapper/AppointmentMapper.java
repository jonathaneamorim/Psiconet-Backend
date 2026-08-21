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
