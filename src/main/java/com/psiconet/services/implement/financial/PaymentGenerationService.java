package com.psiconet.services.implement.financial;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.financial.Payment;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.financial.PaymentRepository;
import com.psiconet.services.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria os {@link Payment} das consultas com preço definido. A criação principal acontece na hora
 * ({@link #generatePaymentIfNeeded}, chamado assim que a consulta é aceita/agendada — ver
 * AppointmentServiceImpl e RecurrenceGenerationService), para que o valor já conte como "a receber"
 * imediatamente, sem depender da consulta já ter acontecido. O job agendado abaixo é só uma rede de
 * segurança para consultas antigas ou que por algum motivo ficaram sem pagamento gerado.
 */
@Service
@RequiredArgsConstructor
public class PaymentGenerationService {

    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedRateString = "${psiconet.payment.generation-fixed-rate:300000}")
    @Transactional
    public void generatePendingPayments() {
        appointmentRepository.findCompletedChargeableWithoutPayment().forEach(this::createPaymentIfAbsent);
    }

    // Chamado no momento em que a consulta é criada/aceita (avulsa ou recorrente): gera a cobrança de
    // imediato, desde que haja um preço definido — sem isso, o valor só entraria em "a receber" depois
    // que a consulta já tivesse acontecido, mesmo já sendo um compromisso firmado com o paciente.
    @Transactional
    public void generatePaymentIfNeeded(Appointment appointment) {
        if (appointment.getPrice() == null || appointment.getPrice().signum() <= 0) {
            return;
        }

        createPaymentIfAbsent(appointment);
    }

    private void createPaymentIfAbsent(Appointment appointment) {
        if (paymentRepository.existsByAppointment(appointment)) {
            return;
        }

        Payment payment = new Payment();
        payment.setTreatmentLink(appointment.getTreatmentLink());
        payment.setAppointment(appointment);
        payment.setAmount(appointment.getPrice());
        payment.setStatus(PaymentStatusEnum.PENDING);
        payment = paymentRepository.save(payment);

        Psychologist psychologist = appointment.getTreatmentLink().getPsychologist();

        notificationService.create(
                appointment.getTreatmentLink().getPatient().getUser(),
                psychologist.getUser(),
                NotificationType.PAYMENT_PENDING,
                "Pagamento Pendente",
                "Existe um pagamento pendente para sua consulta com " + psychologist.getUser().getFullName() + ".",
                "PAYMENT",
                payment.getId()
        );
    }
}
