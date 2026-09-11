package com.psiconet.repositories.access;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    boolean existsByRole(RoleEnum role);
    boolean existsByCpf(String cpf);

    // Trava a linha do usuário (SELECT ... FOR UPDATE) pelo resto da transação atual. Usado como
    // mutex para serializar fluxos de "checar-depois-inserir" (ex.: conflito de horário de agenda,
    // pedido de conexão duplicado) que, sem isso, são vulneráveis a race condition/phantom read sob
    // concorrência: duas transações podem ler "nenhum conflito" antes de qualquer uma commitar.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}