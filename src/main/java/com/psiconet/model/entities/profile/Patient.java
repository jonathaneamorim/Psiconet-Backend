package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "paciente")
@Getter
@Setter
// Só o id no toString: chave Pix, endereço e o User (lazy) não podem ir para log (SEC-10).
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private User user;
}