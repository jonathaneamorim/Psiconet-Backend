package com.psiconet.model.dtos.profile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatientDTO {
    private String fullName;
    private String cpf;
    private String phone;
    private String birthDate;
    private String photoUrl;
}
