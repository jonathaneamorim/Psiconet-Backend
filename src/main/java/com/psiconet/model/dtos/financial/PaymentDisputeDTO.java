package com.psiconet.model.dtos.financial;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentDisputeDTO {
    @NotBlank(message = "Informe a mensagem de contestação.")
    private String message;
}
