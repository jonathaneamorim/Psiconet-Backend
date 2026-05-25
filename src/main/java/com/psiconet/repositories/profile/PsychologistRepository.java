package com.psiconet.repositories.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PsychologistRepository extends JpaRepository<Psychologist, UUID> {
    
    @EntityGraph(attributePaths = {"user", "specialties"})
    Optional<Psychologist> findByUser(User user);

    @EntityGraph(attributePaths = {"user", "specialties"})
    Optional<Psychologist> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Page<Psychologist> findByUser_FullNameContainingIgnoreCase(String fullName, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "specialties"})
    List<Psychologist> findByUserIn(Collection<User> users);

    Optional<Psychologist> findByCrp(String crp);
    
    boolean existsByCrp(String crp);
}
