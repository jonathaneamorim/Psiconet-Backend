package com.psiconet.model.entities.financial;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pagamento")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    @OneToOne
    @JoinColumn(name = "agendamento_id", unique = true)
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatusEnum status;

    @Column(name = "motivo_rejeicao")
    private String rejectionReason;

    @Column(name = "mensagem_contestacao")
    private String disputeMessage;

    @Column(name = "recibo_enviado_em")
    private LocalDateTime receiptUploadedAt;

    @Column(name = "revisado_em")
    private LocalDateTime reviewedAt;

    // Lock otimista: impede que duas requisições concorrentes (ex.: aprovar + rejeitar em paralelo,
    // ou duplo clique) apliquem transições de status conflitantes silenciosamente (lost update).
    @Version
    private Long version;
}
