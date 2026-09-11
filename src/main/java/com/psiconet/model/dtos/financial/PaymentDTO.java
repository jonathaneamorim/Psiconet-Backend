package com.psiconet.model.dtos.financial;

import com.psiconet.model.enums.financial.PaymentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDTO {
    private UUID id;
    private UUID appointmentId;
    private BigDecimal amount;
    private PaymentStatusEnum status;
    private String receiptUrl;
    private String rejectionReason;
    private String disputeMessage;
    private LocalDateTime receiptUploadedAt;
    private LocalDateTime reviewedAt;
}
