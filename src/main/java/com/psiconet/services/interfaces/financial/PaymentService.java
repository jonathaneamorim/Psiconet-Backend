package com.psiconet.services.interfaces.financial;

import com.psiconet.model.dtos.financial.FinancialSummaryDTO;
import com.psiconet.model.dtos.financial.PaymentDTO;
import com.psiconet.model.dtos.financial.PaymentDisputeDTO;
import com.psiconet.model.dtos.financial.PaymentRejectDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface PaymentService {
    PaymentDTO uploadReceipt(User patientUser, UUID paymentId, MultipartFile file);
    Resource downloadReceipt(User user, UUID paymentId);
    PaymentDTO approve(User psychologistUser, UUID paymentId);
    PaymentDTO reject(User psychologistUser, UUID paymentId, PaymentRejectDTO dto);
    PaymentDTO dispute(User patientUser, UUID paymentId, PaymentDisputeDTO dto);
    Page<PaymentDTO> list(User user, PaymentStatusEnum status, Pageable pageable);
    PaymentDTO getById(User user, UUID paymentId);
    FinancialSummaryDTO getFinancialSummary(User user, Integer year, Integer month);
}
