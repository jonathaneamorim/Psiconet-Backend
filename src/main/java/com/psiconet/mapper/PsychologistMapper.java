package com.psiconet.mapper;

import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.model.dtos.profile.PsychologistDTO;
import com.psiconet.model.dtos.profile.PsychologistMeDTO;
import com.psiconet.model.dtos.profile.PsychologistProfileDTO;
import com.psiconet.model.dtos.profile.SearchPsychologistDTO;
import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PsychologistMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "experienceTime", ignore = true)
    @Mapping(target = "specialties", ignore = true)
    Psychologist toPsychologist(PsychologistRegisterRequest dto, User user);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "location", source = "user.location")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "createdAt", source = "user.createdAt")
    PsychologistMeDTO toMeDto(Psychologist psychologist);

    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    PsychologistDTO toDto(Psychologist psychologist);

    Psychologist toEntity(PublicPsychologistDTO publicPsychologistDTO);

    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    PublicPsychologistDTO toPsychologistDto(Psychologist psychologist);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "city", source = "user.location.city")
    @Mapping(target = "state", source = "user.location.state")
    @Mapping(target = "createdAt", source = "user.createdAt")
    @Mapping(target = "connectionStatus", ignore = true)
    @Mapping(target = "connectionId", ignore = true)
    PsychologistProfileDTO toProfileDto(Psychologist psychologist);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "city", source = "user.location.city")
    @Mapping(target = "state", source = "user.location.state")
    @Mapping(target = "connectionStatus", ignore = true)
    @Mapping(target = "connectionId", ignore = true)
    SearchPsychologistDTO toSearchDto(Psychologist psychologist);
}