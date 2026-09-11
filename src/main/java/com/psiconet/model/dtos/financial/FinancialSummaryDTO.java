package com.psiconet.model.dtos.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/* Resumo financeiro de um mês, sob a perspectiva do usuário autenticado (psicólogo: recebido/a receber; paciente: pago/a pagar) */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FinancialSummaryDTO {
    private int year;
    private int month;

    // APPROVED
    private long confirmedCount;
    private BigDecimal confirmedAmount;

    // AWAITING_REVIEW (comprovante enviado, aguardando aprovação)
    private long awaitingReviewCount;
    private BigDecimal awaitingReviewAmount;

    // PENDING (ainda sem comprovante enviado)
    private long pendingCount;
    private BigDecimal pendingAmount;

    // DISPUTED
    private long disputedCount;
    private BigDecimal disputedAmount;

    // REJECTED
    private long rejectedCount;
    private BigDecimal rejectedAmount;

    // CANCELLED
    private long cancelledCount;
    private BigDecimal cancelledAmount;

    // Soma de tudo que ainda é esperado entrar/sair no mês (pending + awaitingReview + disputed)
    private long outstandingCount;
    private BigDecimal outstandingAmount;

    // Soma de confirmedAmount + outstandingAmount (exclui rejeitados e cancelados)
    private BigDecimal totalExpectedAmount;
}
