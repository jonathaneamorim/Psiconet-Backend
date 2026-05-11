package com.psiconet.model.entities.clinical;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "anotacoes_sessao")
public class SessionNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "agendamento_id")
    private Appointment appointment;

    @Column(name = "resumo")
    private String summary;

    @Column(name = "estado_mental")
    private String mentalState;

    @Column(name = "tarefas_casa")
    private String homework;
}