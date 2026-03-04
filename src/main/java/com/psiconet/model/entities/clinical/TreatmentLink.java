package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "paciente_piscologo")
public class TreatmentLink {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_id")
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "psicologo_id")
    private Psychologist psychologist;

    @Column(name = "data_inicio")
    private LocalDate startDate;

    @Column(name = "ativo")
    private Boolean isActive;
}