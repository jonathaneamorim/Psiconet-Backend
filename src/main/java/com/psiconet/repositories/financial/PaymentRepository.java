package com.psiconet.repositories.financial;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.financial.Payment;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByAppointment(Appointment appointment);

    Optional<Payment> findByAppointment(Appointment appointment);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.treatmentLink.psychologist = :psychologist
          AND (:status IS NULL OR p.status = :status)
        ORDER BY p.appointment.startDateTime DESC
    """)
    Page<Payment> findByPsychologist(
            @Param("psychologist") Psychologist psychologist,
            @Param("status") PaymentStatusEnum status,
            Pageable pageable
    );

    @Query("""
        SELECT p FROM Payment p
        WHERE p.treatmentLink.patient = :patient
          AND (:status IS NULL OR p.status = :status)
        ORDER BY p.appointment.startDateTime DESC
    """)
    Page<Payment> findByPatient(
            @Param("patient") Patient patient,
            @Param("status") PaymentStatusEnum status,
            Pageable pageable
    );

    // Agrega por status (contagem + soma) os pagamentos referentes a consultas dentro de [start, end).
    // Usado no resumo financeiro mensal do dashboard.
    @Query("""
        SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.treatmentLink.psychologist = :psychologist
          AND p.appointment.startDateTime >= :start AND p.appointment.startDateTime < :end
        GROUP BY p.status
    """)
    List<Object[]> aggregateByPsychologistAndAppointmentMonth(
            @Param("psychologist") Psychologist psychologist,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.treatmentLink.patient = :patient
          AND p.appointment.startDateTime >= :start AND p.appointment.startDateTime < :end
        GROUP BY p.status
    """)
    List<Object[]> aggregateByPatientAndAppointmentMonth(
            @Param("patient") Patient patient,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
