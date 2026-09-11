package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "regra_recorrencia")
public class RecurrenceRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id", nullable = false)
    private TreatmentLink treatmentLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequencia", nullable = false)
    private RecurrenceFrequencyEnum frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana")
    private DayOfWeek dayOfWeek;

    // Só usado quando frequency = EVERY_N_DAYS.
    @Column(name = "intervalo_dias")
    private Integer intervalDays;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime startTime;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime endTime;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate startDate;

    @Column(name = "data_fim")
    private LocalDate endDate;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reuniao")
    private MeetingTypeEnum meetingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor_reuniao")
    private MeetingProviderEnum meetingProvider;

    @Column(name = "link_reuniao")
    private String meetingLink;

    @Embedded
    private Location location;

    @Column(name = "preco")
    private BigDecimal price;

    @Column(name = "ajustar_fim_semana")
    private boolean adjustForWeekend;

    @Column(name = "ativo")
    private Boolean isActive;

    @Column(name = "gerado_ate")
    private LocalDate lastGeneratedUntil;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime updatedAt;
}
