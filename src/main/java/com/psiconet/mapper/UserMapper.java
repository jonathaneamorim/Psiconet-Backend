package com.psiconet.mapper;

import com.psiconet.model.dtos.access.UserDTO;
import com.psiconet.model.dtos.admin.UsersToListAdminDTO;
import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import org.mapstruct.*;

import java.time.LocalDate;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)

    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    @Mapping(target = "location", ignore = true)

    @Mapping(target = "role", source = "role")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "cpf", source = "cpf")
    @Mapping(target = "birthDate", source = "birthDate")

    User toUser(
            String email,
            String cpf,
            LocalDate birthDate,
            RoleEnum role
    );

    @InheritConfiguration(name = "toUser")
    default User toPatientUser(
            PatientRegisterRequest request,
            RoleEnum role
    ) {

        return toUser(
                request.getEmail(),
                request.getCpf(),
                request.getBirthDate(),
                role
        );
    }

    @InheritConfiguration(name = "toUser")
    default User toPsychologistUser(
            PsychologistRegisterRequest request,
            RoleEnum role
    ) {

        return toUser(
                request.getEmail(),
                request.getCpf(),
                request.getBirthDate(),
                role
        );
    }

    UserDTO toDto(User user);

    @Mapping(target = "maskedCpf", source = "cpf", qualifiedByName = "maskCpf")
    UsersToListAdminDTO toAdminListDto(User user);

    @Named("maskCpf")
    default String maskCpf(String cpf) {
        if (cpf == null || cpf.length() < 11) return cpf;
        // Format: 123.***.***-45
        return cpf.substring(0, 3) + ".***.***-" + cpf.substring(9);
    }
}