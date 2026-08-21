package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
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
}