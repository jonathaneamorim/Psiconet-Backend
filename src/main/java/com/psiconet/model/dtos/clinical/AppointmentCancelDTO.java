package com.psiconet.model.dtos.clinical;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentCancelDTO {
    // Obrigatoriedade é validada no service: obrigatório se quem cancela é o paciente,
    // opcional se for o psicólogo.
    private String reason;
}
