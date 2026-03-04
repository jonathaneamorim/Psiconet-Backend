package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.profile.Location;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "agendamento")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    @ManyToOne
    @JoinColumn(name = "local_id")
    private Location location;

    @Column(name = "data_hora")
    private LocalDateTime dateTime;

    private String status;
}