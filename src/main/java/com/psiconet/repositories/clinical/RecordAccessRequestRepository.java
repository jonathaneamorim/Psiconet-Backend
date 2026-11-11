package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.RecordAccessRequest;
import com.psiconet.model.enums.clinical.RecordAccessStatusEnum;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordAccessRequestRepository extends JpaRepository<RecordAccessRequest, UUID> {

    @EntityGraph(attributePaths = {"patient", "medicalRecord", "medicalRecord.treatmentLink"})
    List<RecordAccessRequest> findByPsychologistOrderByRequestedAtDesc(User psychologist);

    @EntityGraph(attributePaths = {"psychologist", "medicalRecord", "medicalRecord.treatmentLink"})
    List<RecordAccessRequest> findByPatientOrderByRequestedAtDesc(User patient);

    // Anti-IDOR: sempre restrito ao dono da solicitação/decisão
    Optional<RecordAccessRequest> findByIdAndPatient(UUID id, User patient);
    Optional<RecordAccessRequest> findByIdAndPsychologist(UUID id, User psychologist);

    boolean existsByMedicalRecordAndPatientAndStatus(MedicalRecord record, User patient, RecordAccessStatusEnum status);

    long countByMedicalRecordAndStatus(MedicalRecord record, RecordAccessStatusEnum status);
}
