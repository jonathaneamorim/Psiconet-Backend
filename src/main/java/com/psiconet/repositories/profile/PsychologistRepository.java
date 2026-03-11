package com.psiconet.repositories.profile;

import com.psiconet.model.entities.profile.Psychologist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PsychologistRepository extends JpaRepository<Psychologist, UUID> {}