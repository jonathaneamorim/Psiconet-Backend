package com.psiconet.repositories.admin;

import com.psiconet.model.entities.access.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminRepository extends JpaRepository<User, UUID> {
}
