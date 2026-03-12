package com.psiconet.model.dtos.auth;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PatientRegisterRequest {
    private String email;
    private String password;
    private String cpf;
    private LocalDate birthDate;
}