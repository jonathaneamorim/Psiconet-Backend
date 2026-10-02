package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.SessionNote;
import com.psiconet.model.entities.clinical.SessionNoteAddendum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SessionNoteAddendumRepository extends JpaRepository<SessionNoteAddendum, UUID> {
    List<SessionNoteAddendum> findByNoteOrderBySequenceAsc(SessionNote note);
    List<SessionNoteAddendum> findByNoteInOrderBySequenceAsc(Collection<SessionNote> notes);
    long countByNote(SessionNote note);
}
