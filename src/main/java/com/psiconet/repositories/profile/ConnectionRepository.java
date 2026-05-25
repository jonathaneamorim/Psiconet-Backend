package com.psiconet.repositories.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.conection.Connection;
import com.psiconet.model.enums.conection.ConectionStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    @Query("SELECT c FROM Connection c WHERE " +
           "((c.sender = :user1 AND c.receiver = :user2) OR (c.sender = :user2 AND c.receiver = :user1)) " +
           "AND c.status <> com.psiconet.model.enums.conection.ConectionStatusEnum.REMOVED")
    Optional<Connection> findActiveOrPendingBetween(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT c FROM Connection c WHERE " +
           "(c.sender = :currentUser OR c.receiver = :currentUser) " +
           "AND (c.sender IN :users OR c.receiver IN :users) " +
           "AND c.status <> com.psiconet.model.enums.conection.ConectionStatusEnum.REMOVED")
    List<Connection> findAllActiveOrPendingBetweenUsers(@Param("currentUser") User currentUser, @Param("users") List<User> users);

    Page<Connection> findByReceiverAndStatus(User receiver, ConectionStatusEnum status, Pageable pageable);

    @Query("SELECT c FROM Connection c WHERE (c.sender = :user OR c.receiver = :user) AND c.status = :status")
    Page<Connection> findByUserAndStatus(@Param("user") User user, @Param("status") ConectionStatusEnum status, Pageable pageable);
}
