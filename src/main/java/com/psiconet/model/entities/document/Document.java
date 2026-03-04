package com.psiconet.model.entities.document;

import com.psiconet.model.entities.clinical.TreatmentLink;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "documento")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    @ManyToOne
    @JoinColumn(name = "tipo_documento_id")
    private DocumentType documentType;

    @Column(name = "data_emissao")
    private LocalDate issueDate;

    @Column(name = "arquivo")
    private String fileUrl;
}