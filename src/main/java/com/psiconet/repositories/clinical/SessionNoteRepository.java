package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.SessionNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SessionNoteRepository extends JpaRepository<SessionNote, UUID> {}