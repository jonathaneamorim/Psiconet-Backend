package com.psiconet.model.dtos.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class AppointmentCreateDTO {
    @NotNull
    private UUID patientId;

    @NotNull
    @Future
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String title;
    private String description;

    @NotNull
    private MeetingTypeEnum meetingType;
    // Só para VIDEO_CALL. Omitido ⇒ EXTERNAL_LINK (compatibilidade com clientes antigos).
    private MeetingProviderEnum meetingProvider;
    private String meetingLink;
    private Location location;

    @DecimalMin("0.0")
    private BigDecimal price;

    // Horário já ocupado por outra consulta: só cria com confirmação explícita (409 SCHEDULE_OVERLAP sem ela).
    private boolean acknowledgeOverlap;
}
