package com.psiconet.model.entities.access;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_alteracao")
public class ChangeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User user;

    @Column(name = "tabela")
    private String tableName;

    @Column(name = "acao_reealizada")
    private String actionPerformed;

    @Column(name = "data_hora")
    private LocalDateTime dateTime;
}