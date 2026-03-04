package com.psiconet.model.entities.clinical;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "prontuario")
public class MedicalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    @Column(name = "data_abertura")
    private LocalDate openingDate;

    @Column(name = "queixa_principal")
    private String mainComplaint;

    @Column(name = "historico_familiar")
    private String familyHistory;

    @Column(name = "antecedentes_medicos")
    private String medicalHistory;

    @Column(name = "objetivos_terapeuticos")
    private String therapeuticGoals;
}