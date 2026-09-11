package com.psiconet.services.implement.financial;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.storage.FileStorageService;
import com.psiconet.mapper.PaymentMapper;
import com.psiconet.model.dtos.financial.FinancialSummaryDTO;
import com.psiconet.model.dtos.financial.PaymentDTO;
import com.psiconet.model.dtos.financial.PaymentDisputeDTO;
import com.psiconet.model.dtos.financial.PaymentRejectDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.financial.Payment;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.repositories.financial.PaymentRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.financial.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final List<PaymentStatusEnum> RECEIPT_UPLOADABLE_STATUSES =
            List.of(PaymentStatusEnum.PENDING, PaymentStatusEnum.REJECTED);
    private static final List<PaymentStatusEnum> REVIEWABLE_STATUSES =
            List.of(PaymentStatusEnum.AWAITING_REVIEW, PaymentStatusEnum.DISPUTED);

    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final PsychologistRepository psychologistRepository;
    private final PaymentMapper paymentMapper;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public PaymentDTO uploadReceipt(User patientUser, UUID paymentId, MultipartFile file) {
        Payment payment = findPaymentOrThrow(paymentId);
        TreatmentLink treatmentLink = payment.getTreatmentLink();

        if (!treatmentLink.getPatient().getUser().getId().equals(patientUser.getId())) {
            throw new BusinessException("payment", "Você não tem permissão para enviar o comprovante deste pagamento.");
        }

        if (!RECEIPT_UPLOADABLE_STATUSES.contains(payment.getStatus())) {
            throw new BusinessException("payment", "Não é possível enviar comprovante para este pagamento no status atual.");
        }

        String path = fileStorageService.store(file, "receipts");

        payment.setReceiptUrl(path);
        payment.setReceiptUploadedAt(LocalDateTime.now());
        payment.setStatus(PaymentStatusEnum.AWAITING_REVIEW);
        payment.setRejectionReason(null);
        payment = paymentRepository.save(payment);

        notificationService.create(
                treatmentLink.getPsychologist().getUser(),
                patientUser,
                NotificationType.PAYMENT_RECEIPT_SUBMITTED,
                "Comprovante Enviado",
                patientUser.getFullName() + " enviou o comprovante de pagamento.",
                "PAYMENT",
                payment.getId()
        );

        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadReceipt(User user, UUID paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        validateOwnership(user, payment);

        if (payment.getReceiptUrl() == null) {
            throw new BusinessException("payment", "Este pagamento ainda não possui comprovante.");
        }

        return fileStorageService.loadAsResource(payment.getReceiptUrl());
    }

    @Override
    @Transactional
    public PaymentDTO approve(User psychologistUser, UUID paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        validatePsychologistOwnership(psychologistUser, payment);

        if (!REVIEWABLE_STATUSES.contains(payment.getStatus())) {
            throw new BusinessException("payment", "Apenas pagamentos aguardando revisão podem ser aprovados.");
        }

        payment.setStatus(PaymentStatusEnum.APPROVED);
        payment.setReviewedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        notificationService.create(
                payment.getTreatmentLink().getPatient().getUser(),
                psychologistUser,
                NotificationType.PAYMENT_APPROVED,
                "Pagamento Aprovado",
                "Seu pagamento foi aprovado por " + psychologistUser.getFullName() + ".",
                "PAYMENT",
                payment.getId()
        );

        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional
    public PaymentDTO reject(User psychologistUser, UUID paymentId, PaymentRejectDTO dto) {
        Payment payment = findPaymentOrThrow(paymentId);
        validatePsychologistOwnership(psychologistUser, payment);

        if (!REVIEWABLE_STATUSES.contains(payment.getStatus())) {
            throw new BusinessException("payment", "Apenas pagamentos aguardando revisão podem ser rejeitados.");
        }

        payment.setStatus(PaymentStatusEnum.REJECTED);
        payment.setRejectionReason(dto.getReason());
        payment.setReviewedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        notificationService.create(
                payment.getTreatmentLink().getPatient().getUser(),
                psychologistUser,
                NotificationType.PAYMENT_REJECTED,
                "Pagamento Rejeitado",
                psychologistUser.getFullName() + " rejeitou seu comprovante: " + dto.getReason(),
                "PAYMENT",
                payment.getId()
        );

        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional
    public PaymentDTO dispute(User patientUser, UUID paymentId, PaymentDisputeDTO dto) {
        Payment payment = findPaymentOrThrow(paymentId);
        TreatmentLink treatmentLink = payment.getTreatmentLink();

        if (!treatmentLink.getPatient().getUser().getId().equals(patientUser.getId())) {
            throw new BusinessException("payment", "Você não tem permissão para contestar este pagamento.");
        }

        if (payment.getStatus() != PaymentStatusEnum.REJECTED) {
            throw new BusinessException("payment", "Só é possível contestar pagamentos rejeitados.");
        }

        payment.setStatus(PaymentStatusEnum.DISPUTED);
        payment.setDisputeMessage(dto.getMessage());
        payment = paymentRepository.save(payment);

        notificationService.create(
                treatmentLink.getPsychologist().getUser(),
                patientUser,
                NotificationType.PAYMENT_DISPUTED,
                "Pagamento Contestado",
                patientUser.getFullName() + " contestou a rejeição do pagamento.",
                "PAYMENT",
                payment.getId()
        );

        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentDTO> list(User user, PaymentStatusEnum status, Pageable pageable) {
        if (user.getRole() == RoleEnum.PSYCHOLOGIST) {
            Psychologist psychologist = psychologistRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, user.getId()));
            return paymentRepository.findByPsychologist(psychologist, status, pageable).map(paymentMapper::toDto);
        }

        if (user.getRole() == RoleEnum.PATIENT) {
            Patient patient = patientRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Patient.class, user.getId()));
            return paymentRepository.findByPatient(patient, status, pageable).map(paymentMapper::toDto);
        }

        throw new BusinessException("payment", "Apenas psicólogos e pacientes possuem pagamentos.");
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getById(User user, UUID paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        validateOwnership(user, payment);
        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryDTO getFinancialSummary(User user, Integer year, Integer month) {
        LocalDate now = LocalDate.now();
        int resolvedYear = year != null ? year : now.getYear();
        int resolvedMonth = month != null ? month : now.getMonthValue();

        if (resolvedMonth < 1 || resolvedMonth > 12) {
            throw new BusinessException("month", "Mês inválido. Informe um valor entre 1 e 12.");
        }
        if (resolvedYear < 2000 || resolvedYear > 2100) {
            throw new BusinessException("year", "Ano inválido.");
        }

        LocalDateTime start = LocalDate.of(resolvedYear, resolvedMonth, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        List<Object[]> rows;
        if (user.getRole() == RoleEnum.PSYCHOLOGIST) {
            Psychologist psychologist = psychologistRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, user.getId()));
            rows = paymentRepository.aggregateByPsychologistAndAppointmentMonth(psychologist, start, end);
        } else if (user.getRole() == RoleEnum.PATIENT) {
            Patient patient = patientRepository.findByUser(user)
                    .orElseThrow(() -> new EntityNotFoundException(Patient.class, user.getId()));
            rows = paymentRepository.aggregateByPatientAndAppointmentMonth(patient, start, end);
        } else {
            throw new BusinessException("payment", "Apenas psicólogos e pacientes possuem pagamentos.");
        }

        Map<PaymentStatusEnum, long[]> counts = new EnumMap<>(PaymentStatusEnum.class);
        Map<PaymentStatusEnum, BigDecimal> amounts = new EnumMap<>(PaymentStatusEnum.class);

        for (Object[] row : rows) {
            PaymentStatusEnum status = (PaymentStatusEnum) row[0];
            long count = (Long) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            counts.put(status, new long[]{count});
            amounts.put(status, amount);
        }

        long confirmedCount = countOf(counts, PaymentStatusEnum.APPROVED);
        BigDecimal confirmedAmount = amountOf(amounts, PaymentStatusEnum.APPROVED);

        long awaitingReviewCount = countOf(counts, PaymentStatusEnum.AWAITING_REVIEW);
        BigDecimal awaitingReviewAmount = amountOf(amounts, PaymentStatusEnum.AWAITING_REVIEW);

        long pendingCount = countOf(counts, PaymentStatusEnum.PENDING);
        BigDecimal pendingAmount = amountOf(amounts, PaymentStatusEnum.PENDING);

        long disputedCount = countOf(counts, PaymentStatusEnum.DISPUTED);
        BigDecimal disputedAmount = amountOf(amounts, PaymentStatusEnum.DISPUTED);

        long rejectedCount = countOf(counts, PaymentStatusEnum.REJECTED);
        BigDecimal rejectedAmount = amountOf(amounts, PaymentStatusEnum.REJECTED);

        long cancelledCount = countOf(counts, PaymentStatusEnum.CANCELLED);
        BigDecimal cancelledAmount = amountOf(amounts, PaymentStatusEnum.CANCELLED);

        long outstandingCount = pendingCount + awaitingReviewCount + disputedCount;
        BigDecimal outstandingAmount = pendingAmount.add(awaitingReviewAmount).add(disputedAmount);

        return FinancialSummaryDTO.builder()
                .year(resolvedYear)
                .month(resolvedMonth)
                .confirmedCount(confirmedCount)
                .confirmedAmount(confirmedAmount)
                .awaitingReviewCount(awaitingReviewCount)
                .awaitingReviewAmount(awaitingReviewAmount)
                .pendingCount(pendingCount)
                .pendingAmount(pendingAmount)
                .disputedCount(disputedCount)
                .disputedAmount(disputedAmount)
                .rejectedCount(rejectedCount)
                .rejectedAmount(rejectedAmount)
                .cancelledCount(cancelledCount)
                .cancelledAmount(cancelledAmount)
                .outstandingCount(outstandingCount)
                .outstandingAmount(outstandingAmount)
                .totalExpectedAmount(confirmedAmount.add(outstandingAmount))
                .build();
    }

    private long countOf(Map<PaymentStatusEnum, long[]> counts, PaymentStatusEnum status) {
        long[] value = counts.get(status);
        return value != null ? value[0] : 0L;
    }

    private BigDecimal amountOf(Map<PaymentStatusEnum, BigDecimal> amounts, PaymentStatusEnum status) {
        return amounts.getOrDefault(status, BigDecimal.ZERO);
    }

    private void validateOwnership(User user, Payment payment) {
        TreatmentLink treatmentLink = payment.getTreatmentLink();
        boolean isPsychologist = treatmentLink.getPsychologist().getUser().getId().equals(user.getId());
        boolean isPatient = treatmentLink.getPatient().getUser().getId().equals(user.getId());

        if (!isPsychologist && !isPatient) {
            throw new BusinessException("payment", "Você não tem permissão para acessar este pagamento.");
        }
    }

    private void validatePsychologistOwnership(User psychologistUser, Payment payment) {
        if (!payment.getTreatmentLink().getPsychologist().getUser().getId().equals(psychologistUser.getId())) {
            throw new BusinessException("payment", "Você não tem permissão para revisar este pagamento.");
        }
    }

    private Payment findPaymentOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException(Payment.class, paymentId));
    }
}
