package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
    Page<RecurrenceRule> findByTreatmentLinkOrderByStartDateDesc(TreatmentLink treatmentLink, Pageable pageable);
    List<RecurrenceRule> findByIsActiveTrue();

    // Trava a regra até o fim da transação: só uma geração de ocorrência por vez (job × ação do usuário).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RecurrenceRule r WHERE r.id = :id")
    Optional<RecurrenceRule> findByIdForUpdate(@Param("id") UUID id);
}
