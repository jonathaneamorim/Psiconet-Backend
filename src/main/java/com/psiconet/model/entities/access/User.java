package com.psiconet.model.entities.access;

import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
// Só campos não sensíveis no toString (nunca senha/CPF em log).
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "usuario")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String cpf;

    @Column(name = "telefone")
    private String phone;

    @Column(name = "nome")
    private String fullName;

    @Column(name = "data_nascimento")
    private LocalDate birthDate;

    @Column(name = "foto")
    private String photoUrl;

    @Embedded
    private Location location;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @ToString.Include
    private RoleEnum role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatusEnum status = UserStatusEnum.PENDING_VERIFICATION;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Versão das credenciais/sessões: vai no JWT (claim "ver") e é conferida a cada requisição.
    // Incrementar invalida todos os tokens já emitidos (logout, troca/redefinição de senha, inativação).
    @ColumnDefault("0")
    @Column(name = "versao_token", nullable = false)
    private int tokenVersion = 0;

    // Senha temporária (ex.: admin recém-criado): enquanto true, só é permitido trocar a senha.
    @ColumnDefault("false")
    @Column(name = "troca_senha_obrigatoria", nullable = false)
    private boolean mustChangePassword = false;

    // Administrador base criado pelo DataInitializer: não pode ser inativado nem removido.
    @ColumnDefault("false")
    @Column(name = "admin_base", nullable = false)
    private boolean baseAdmin = false;

    // Quem criou a conta (preenchido para administradores criados por outro administrador).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private User createdBy;

    @Column(name = "ultimo_login_em")
    private Instant lastLoginAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null) return Collections.emptyList();

        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return status != UserStatusEnum.BLOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return UserStatusEnum.ACTIVE.equals(this.status);
    }
}