package com.psiconet.mapper;

import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PsychologistMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "fullName", constant = "")
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "experienceTime", ignore = true)
    @Mapping(target = "specialties", ignore = true)
    Psychologist toPsychologist(PsychologistRegisterRequest dto, User user);

}