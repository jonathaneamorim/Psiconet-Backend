package com.psiconet.mapper;

import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "fullName", constant = "")
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    Patient toPatient(PatientRegisterRequest dto, User user);
}