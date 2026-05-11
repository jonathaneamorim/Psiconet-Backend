package com.psiconet.model.dtos.profile.open;

import com.psiconet.model.dtos.profile.SpecialtyDTO;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PublicPsychologistDTO {
    private String fullName;
    private String email;
    private String phone;
    private String crp;
    private String photoUrl;
    private String experienceTime;
    private String description;
    private List<SpecialtyDTO> specialties;
}
