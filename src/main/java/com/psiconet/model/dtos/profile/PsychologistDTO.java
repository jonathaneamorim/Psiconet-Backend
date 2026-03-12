package com.psiconet.model.dtos.profile;

import com.psiconet.model.dtos.access.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PsychologistDTO {
    private String id;
    private UserDTO user;
    private String name;
    private String fullName;
    private String email;
    private String phone;
    private String cpf;
    private String crp;
    private String photoUrl;
    private String isActive;
    private String birthDate;
    private String experienceTime;
    private String description;
    private List<SpecialtyDTO> specialties;
}
