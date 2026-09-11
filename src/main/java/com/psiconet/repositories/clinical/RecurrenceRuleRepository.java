package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
    Page<RecurrenceRule> findByTreatmentLinkOrderByStartDateDesc(TreatmentLink treatmentLink, Pageable pageable);
    List<RecurrenceRule> findByIsActiveTrue();
}
