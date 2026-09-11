package com.psiconet.model.dtos.profile;

import com.psiconet.model.enums.financial.PaymentAdvanceUnitEnum;
import com.psiconet.model.enums.financial.PaymentTimingEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingSettingsUpdateDTO {
    private String pixKey;
    private PaymentTimingEnum paymentTiming;

    // Usados só quando paymentTiming = BEFORE_APPOINTMENT.
    private Integer paymentAdvanceValue;
    private PaymentAdvanceUnitEnum paymentAdvanceUnit;
}
