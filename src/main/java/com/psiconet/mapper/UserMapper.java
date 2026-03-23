package com.psiconet.mapper;

import com.psiconet.model.dtos.access.UserDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import org.mapstruct.*;

@Mapper(componentModel = "spring",  unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", source = "role")
    @Mapping(target = "email", source = "email")
    User toUser(String email, RoleEnum role);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UserDTO toDto(User user);
}
