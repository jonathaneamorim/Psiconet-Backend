package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "agendamento")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "data_hora_inicio")
    private LocalDateTime startDateTime;

    @Column(name = "data_hora_fim")
    private LocalDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reuniao")
    private MeetingTypeEnum meetingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor_reuniao")
    private MeetingProviderEnum meetingProvider;

    @Column(name = "link_reuniao")
    private String meetingLink;

    // Preparado para futuras
    @Column(name = "sala_id")
    private String roomId;

    // Apenas quando a consulta for presencial, caso contrário será null
    @Embedded
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatusEnum status;

    // PSYCHOLOGIST ou PATIENT — nulo enquanto não for cancelado
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por")
    private RoleEnum cancelledBy;

    // obrigatório quando cancelledBy = PATIENT, opcional quando PSYCHOLOGIST
    @Column(name = "motivo_cancelamento")
    private String cancellationReason;

    @Column(name = "preco")
    private BigDecimal price;

    @ManyToOne
    @JoinColumn(name = "regra_recorrencia_id")
    private RecurrenceRule recurrenceRule;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime updatedAt;
}
