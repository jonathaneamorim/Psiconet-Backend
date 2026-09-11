package com.psiconet.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationPreferenceDTO {
    private boolean appointmentEnabled;
    private boolean connectionEnabled;
    private boolean paymentEnabled;
}
