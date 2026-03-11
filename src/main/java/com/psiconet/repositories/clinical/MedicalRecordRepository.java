package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {}