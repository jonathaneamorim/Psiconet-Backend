package com.psiconet.mapper;

import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.dtos.profile.PatientDTO;
import com.psiconet.model.dtos.profile.PatientMeDTO;
import com.psiconet.model.dtos.profile.PatientProfileDTO;
import com.psiconet.model.dtos.profile.SearchPatientDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    Patient toPatient(PatientRegisterRequest dto, User user);

    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    PatientDTO toDto(Patient patient);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "city", source = "user.location.city")
    @Mapping(target = "state", source = "user.location.state")
    @Mapping(target = "createdAt", source = "user.createdAt")
    PatientProfileDTO toProfileDto(Patient patient);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "location", source = "user.location")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "createdAt", source = "user.createdAt")
    PatientMeDTO toMeDto(Patient patient);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "city", source = "user.location.city")
    @Mapping(target = "state", source = "user.location.state")
    @Mapping(target = "connectionStatus", ignore = true)
    @Mapping(target = "connectionId", ignore = true)
    SearchPatientDTO toSearchDto(Patient patient);
}