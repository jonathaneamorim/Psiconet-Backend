package com.psiconet.model.entities.financial;

import com.psiconet.model.entities.profile.Psychologist;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "metodo_pagamento")
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "psicologo_id")
    private Psychologist psychologist;

    private String name;
}