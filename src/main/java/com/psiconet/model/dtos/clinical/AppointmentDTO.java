package com.psiconet.model.dtos.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppointmentDTO {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private MeetingTypeEnum meetingType;
    private MeetingProviderEnum meetingProvider;
    private String meetingLink;
    private Location location;
    private AppointmentStatusEnum status;
    private RoleEnum cancelledBy;
    private String cancellationReason;
    private BigDecimal price;
    private UUID recurrenceRuleId;
    private RecurrenceFrequencyEnum recurrenceFrequency;
    private PersonSummaryDTO patient;
    private PersonSummaryDTO psychologist;
    private LocalDateTime createdAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PersonSummaryDTO {
        private UUID id;
        private String fullName;
        private String photoUrl;
    }
}
