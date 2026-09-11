package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.treatmentLink.psychologist = :psychologist
          AND a.status <> com.psiconet.model.enums.clinical.AppointmentStatusEnum.CANCELLED
          AND a.startDateTime < :end
          AND a.endDateTime > :start
    """)
    List<Appointment> findConflicting(
            @Param("psychologist") Psychologist psychologist,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    Page<Appointment> findByTreatmentLink_PsychologistOrderByStartDateTimeAsc(Psychologist psychologist, Pageable pageable);
    Page<Appointment> findByTreatmentLink_PatientOrderByStartDateTimeAsc(Patient patient, Pageable pageable);

    List<Appointment> findByRecurrenceRuleAndStartDateTimeAfterAndStatusIn(
            RecurrenceRule recurrenceRule,
            LocalDateTime threshold,
            List<AppointmentStatusEnum> statuses
    );

    boolean existsByRecurrenceRuleAndStatusIn(RecurrenceRule recurrenceRule, List<AppointmentStatusEnum> statuses);

    List<Appointment> findByTreatmentLink_PsychologistAndStartDateTimeBetween(
            Psychologist psychologist, LocalDateTime start, LocalDateTime end
    );

    List<Appointment> findByTreatmentLink_PatientAndStartDateTimeBetween(
            Patient patient, LocalDateTime start, LocalDateTime end
    );

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status = com.psiconet.model.enums.clinical.AppointmentStatusEnum.ACCEPTED
          AND a.endDateTime <= :now
    """)
    List<Appointment> findAcceptedCompletable(@Param("now") LocalDateTime now);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status = com.psiconet.model.enums.clinical.AppointmentStatusEnum.COMPLETED
          AND a.price IS NOT NULL AND a.price > 0
          AND (a.treatmentLink.psychologist.paymentTiming IS NULL
               OR a.treatmentLink.psychologist.paymentTiming = com.psiconet.model.enums.financial.PaymentTimingEnum.AFTER_APPOINTMENT)
          AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.appointment = a)
    """)
    List<Appointment> findCompletedChargeableWithoutPayment();
}