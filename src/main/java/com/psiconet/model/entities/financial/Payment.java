package com.psiconet.model.entities.financial;

import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.clinical.Appointment;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "pagamento")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    @ManyToOne
    @JoinColumn(name = "agendamento_id")
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "metodo_pagamento_id")
    private PaymentMethod paymentMethod;

    @Column(name = "valor")
    private BigDecimal amount;

    @Column(name = "data_pagamento")
    private LocalDate paymentDate;

    @Column(name = "recibo")
    private String receiptUrl;

    private String status;
}