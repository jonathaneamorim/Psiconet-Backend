package com.psiconet.model.dtos.profile;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PatientDTO {
    private String fullName;
    private String cpf;
    private String phone;
    private String birthDate;
    private String photoUrl;
}
