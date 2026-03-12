package com.psiconet.model.dtos.auth;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PsychologistRegisterRequest  {
    private String email;
    private String cpf;
    private String crp;
    private LocalDate birthDate;
    private String password;
}