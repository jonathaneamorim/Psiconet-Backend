package com.psiconet.repositories.access;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    boolean existsByAccessRoleEnum(RoleEnum accessRoleEnum);
}