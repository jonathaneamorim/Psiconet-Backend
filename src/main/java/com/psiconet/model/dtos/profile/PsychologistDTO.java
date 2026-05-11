package com.psiconet.model.dtos.profile;

import com.psiconet.model.dtos.access.UserDTO;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PsychologistDTO {
    private UserDTO user;
    private String fullName;
    private String phone;
    private String cpf;
    private String crp;
    private String photoUrl;
    private String birthDate;
    private String experienceTime;
    private String description;
    private List<SpecialtyDTO> specialties;
}
