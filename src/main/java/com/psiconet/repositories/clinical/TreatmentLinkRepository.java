package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.TreatmentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TreatmentLinkRepository extends JpaRepository<TreatmentLink, UUID> {}