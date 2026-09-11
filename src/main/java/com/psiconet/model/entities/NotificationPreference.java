package com.psiconet.model.entities;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "preferencia_notificacao")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private User user;

    @Column(name = "notificar_agendamento", nullable = false)
    private boolean appointmentEnabled;

    @Column(name = "notificar_conexao", nullable = false)
    private boolean connectionEnabled;

    @Column(name = "notificar_pagamento", nullable = false)
    private boolean paymentEnabled;
}
