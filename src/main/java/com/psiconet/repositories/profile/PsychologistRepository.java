package com.psiconet.repositories.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PsychologistRepository extends JpaRepository<Psychologist, UUID> {
    Optional<Psychologist> findByUser(User user);
}