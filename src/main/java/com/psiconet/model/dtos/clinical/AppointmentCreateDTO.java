package com.psiconet.model.dtos.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

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
    private String meetingLink;
    private Location location;
}
