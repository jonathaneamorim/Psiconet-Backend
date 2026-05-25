package com.psiconet.repositories.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    
    @EntityGraph(attributePaths = {"user"})
    Optional<Patient> findByUser(User user);

    @EntityGraph(attributePaths = {"user"})
    Optional<Patient> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Page<Patient> findByUser_FullNameContainingIgnoreCase(String fullName, Pageable pageable);
}