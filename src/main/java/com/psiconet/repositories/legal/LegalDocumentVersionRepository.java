package com.psiconet.repositories.legal;

import com.psiconet.infra.legal.LegalDocumentType;
import com.psiconet.model.entities.legal.LegalDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LegalDocumentVersionRepository extends JpaRepository<LegalDocumentVersion, UUID> {
    boolean existsByType(LegalDocumentType type);
    boolean existsByTypeAndVersion(LegalDocumentType type, String version);
}
