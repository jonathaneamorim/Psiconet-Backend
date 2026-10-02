package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.ClinicalAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalAuditLogRepository extends JpaRepository<ClinicalAuditLog, UUID> {
    Optional<ClinicalAuditLog> findTopByOrderBySequenceDesc();
    Page<ClinicalAuditLog> findByMedicalRecordIdOrderBySequenceDesc(UUID medicalRecordId, Pageable pageable);
    List<ClinicalAuditLog> findAllByOrderBySequenceAsc();
}
