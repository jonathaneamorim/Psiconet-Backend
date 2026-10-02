package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.TreatmentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    Optional<MedicalRecord> findFirstByTreatmentLinkOrderByCreatedAtAsc(TreatmentLink treatmentLink);
    List<MedicalRecord> findByTreatmentLinkIn(Collection<TreatmentLink> links);
    List<MedicalRecord> findByKeyVersionNot(String keyVersion);
    List<MedicalRecord> findByWrappedDataKeyIsNull();
}
