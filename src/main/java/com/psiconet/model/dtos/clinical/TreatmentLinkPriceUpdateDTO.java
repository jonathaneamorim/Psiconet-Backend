package com.psiconet.model.dtos.clinical;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TreatmentLinkPriceUpdateDTO {
    @DecimalMin("0.0")
    private BigDecimal defaultPrice;
}
