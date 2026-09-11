package com.psiconet.model.dtos.profile;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.financial.PaymentAdvanceUnitEnum;
import com.psiconet.model.enums.financial.PaymentTimingEnum;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PsychologistMeDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String photoUrl;
    private Location location;
    private Location officeAddress;
    private RoleEnum role;
    private UserStatusEnum status;
    private String crp;
    private List<SpecialtyDTO> specialties;
    private Integer experienceTime;
    private String description;
    private String pixKey;
    private PaymentTimingEnum paymentTiming;
    private Integer paymentAdvanceValue;
    private PaymentAdvanceUnitEnum paymentAdvanceUnit;
    private Instant createdAt;
}
