package com.psiconet.mapper;

import com.psiconet.model.dtos.profile.ActiveConnectionDTO;
import com.psiconet.model.dtos.profile.ConnectedUserDTO;
import com.psiconet.model.dtos.profile.ConnectionDTO;
import com.psiconet.model.dtos.profile.SpecialtyDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.entities.profile.Specialty;
import com.psiconet.model.entities.profile.conection.Connection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ConnectionMapper {

    @Mapping(target = "sender", source = "sender")
    @Mapping(target = "receiver", source = "receiver")
    ConnectionDTO toDto(Connection connection);

    default ConnectionDTO.UserSummaryDTO toUserSummaryDto(User user) {
        if (user == null) return null;
        return ConnectionDTO.UserSummaryDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .photoUrl(user.getPhotoUrl())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .build();
    }

    default ActiveConnectionDTO toActiveDto(Connection connection, UUID currentUserId) {
        if (connection == null) return null;

        User otherUser = connection.getSender().getId().equals(currentUserId) 
                ? connection.getReceiver() 
                : connection.getSender();

        return ActiveConnectionDTO.builder()
                .connectionId(connection.getId())
                .connectedAt(connection.getAcceptedAt())
                .user(toConnectedUserDto(otherUser, null))
                .build();
    }

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "photoUrl", source = "user.photoUrl")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "city", source = "user.location.city")
    @Mapping(target = "state", source = "user.location.state")
    @Mapping(target = "crp", source = "psychologist.crp")
    @Mapping(target = "experienceTime", source = "psychologist.experienceTime")
    @Mapping(target = "description", source = "psychologist.description")
    @Mapping(target = "specialties", source = "psychologist.specialties", qualifiedByName = "mapSpecialties")
    ConnectedUserDTO toConnectedUserDto(User user, Psychologist psychologist);

    @Named("mapSpecialties")
    default List<SpecialtyDTO> mapSpecialties(List<Specialty> specialties) {
        if (specialties == null) return null;
        return specialties.stream()
                .map(s -> new SpecialtyDTO(s.getId().toString(), s.getName(), s.getDescription()))
                .collect(Collectors.toList());
    }
}
