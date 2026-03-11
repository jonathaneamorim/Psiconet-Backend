package com.psiconet.model.dtos.auth;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterPatientDTO {
    private String email;
    private String cpf;
    private LocalDate birthDate;
    private String password;
}