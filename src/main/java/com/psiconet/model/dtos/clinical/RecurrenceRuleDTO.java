package com.psiconet.model.dtos.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecurrenceRuleDTO {
    private UUID id;
    private RecurrenceFrequencyEnum frequency;
    private DayOfWeek dayOfWeek;
    private Integer intervalDays;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate startDate;
    private LocalDate endDate;
    private String title;
    private MeetingTypeEnum meetingType;
    private MeetingProviderEnum meetingProvider;
    private String meetingLink;
    private Location location;
    private BigDecimal price;
    private boolean adjustForWeekend;
    private Boolean isActive;
}
