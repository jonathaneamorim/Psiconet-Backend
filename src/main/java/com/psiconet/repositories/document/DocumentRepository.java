package com.psiconet.repositories.document;

import com.psiconet.model.entities.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {}