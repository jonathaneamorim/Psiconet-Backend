package com.psiconet.model.entities.profile;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "local")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "psicologo_id")
    private Psychologist psychologist;

    private String cep;

    @Column(name = "logradouro")
    private String street;

    @Column(name = "numero")
    private String number;

    @Column(name = "bairro")
    private String neighborhood;

    @Column(name = "cidade")
    private String city;

    @Column(name = "estado")
    private String state;

    @Column(name = "complemento")
    private String complement;

    @Column(name = "tipo_endereco")
    private String addressType;

    @Column(name = "ativo")
    private Boolean isActive;
}