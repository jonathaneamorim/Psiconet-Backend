package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.SessionNote;
import com.psiconet.model.enums.clinical.SessionNoteStatusEnum;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionNoteRepository extends JpaRepository<SessionNote, UUID> {

    @EntityGraph(attributePaths = {"appointment"})
    List<SessionNote> findByMedicalRecordOrderByCreatedAtDesc(MedicalRecord record);

    @EntityGraph(attributePaths = {"appointment"})
    List<SessionNote> findByMedicalRecordAndTypeAndStatusOrderByFinalizedAtAsc(
            MedicalRecord record, SessionNoteTypeEnum type, SessionNoteStatusEnum status);

    long countByMedicalRecord(MedicalRecord record);

    // Legado: anotações antigas sem prontuário/conteúdo novo (texto puro a migrar)
    List<SessionNote> findByContentEncryptedIsNullAndSummaryIsNotNull();
    List<SessionNote> findByContentEncryptedIsNullAndMentalStateIsNotNull();
    List<SessionNote> findByContentEncryptedIsNullAndHomeworkIsNotNull();
}
