package com.psiconet.model.dtos.financial;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRejectDTO {
    @NotBlank(message = "Informe o motivo da rejeição.")
    private String reason;
}
