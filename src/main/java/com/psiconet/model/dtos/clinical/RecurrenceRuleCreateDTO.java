package com.psiconet.model.dtos.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class RecurrenceRuleCreateDTO {
    @NotNull
    private RecurrenceFrequencyEnum frequency;

    private DayOfWeek dayOfWeek;

    // Obrigatório só quando frequency = EVERY_N_DAYS.
    @Min(1)
    private Integer intervalDays;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    private LocalDate endDate;

    private String title;

    @NotNull
    private MeetingTypeEnum meetingType;
    // Só para VIDEO_CALL. Omitido ⇒ EXTERNAL_LINK (compatibilidade com clientes antigos).
    private MeetingProviderEnum meetingProvider;
    private String meetingLink;
    private Location location;

    @DecimalMin("0.0")
    private BigDecimal price;

    private boolean adjustForWeekend;

    // Horário já ocupado por outra consulta: só cria com confirmação explícita (409 SCHEDULE_OVERLAP sem ela).
    private boolean acknowledgeOverlap;
}
