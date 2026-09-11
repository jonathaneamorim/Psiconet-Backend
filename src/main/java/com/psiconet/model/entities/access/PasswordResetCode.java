package com.psiconet.model.entities.access;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "codigo_redefinicao_senha")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    // Nunca armazenar o token em texto puro — só o hash (SHA-256, suficiente para um valor
    // aleatório de 256 bits: não é uma senha de baixa entropia que precise de custo computacional
    // extra tipo BCrypt, e precisamos de busca direta por igualdade de hash).
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "usado", nullable = false)
    private boolean used;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private LocalDateTime createdAt;

    // Lock otimista: evita que duas requisições concorrentes usando o mesmo token (ex.: duplo
    // clique no link) façam lost update sobre "used" e ambas passem pela validação.
    @Version
    private Long version;
}
