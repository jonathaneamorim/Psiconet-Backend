package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.embeddable.Location;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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

    private Location location;

    @Column(name = "data_hora")
    private LocalDateTime dateTime;

    private String status;
}