package com.psiconet.controllers.admin;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.admin.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.services.interfaces.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Área administrativa. Protegida em duas camadas: regra de rota {@code /admin/**} no SecurityConfig
 * e {@code @PreAuthorize} aqui. Nenhum endpoint altera papel (role) de usuário.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<Page<UsersToListAdminDTO>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleEnum role,
            @RequestParam(required = false) UserStatusEnum status,
            @PageableDefault(size = 10, sort = "fullName") Pageable pageable
    ) {
        return ResponseEntity.ok(adminService.listUsers(search, role, status, pageable));
    }

    @PatchMapping("/users/{id}/status")
    @RateLimited("admin-user-write")
    public ResponseEntity<Void> updateUserStatus(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusUpdateDTO statusDto
    ) {
        adminService.updateUserStatus(actor, id, statusDto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}")
    @RateLimited("admin-user-write")
    public ResponseEntity<Void> updateUser(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateAdminDTO updateDto
    ) {
        adminService.updateUser(actor, id, updateDto);
        return ResponseEntity.noContent().build();
    }

    /** Cria um NOVO administrador (conta nova, convite por e-mail, troca de senha obrigatória). */
    @PostMapping("/admins")
    @RateLimited("admin-user-write")
    public ResponseEntity<UsersToListAdminDTO> createAdmin(
            @AuthenticationPrincipal User actor,
            @Valid @RequestBody AdminCreateDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createAdmin(actor, dto));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> dashboard() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    /** Comunicado para um usuário: notificação no site + e-mail. */
    @PostMapping("/notifications")
    @RateLimited("admin-announcement")
    public ResponseEntity<Void> notifyUser(
            @AuthenticationPrincipal User actor,
            @Valid @RequestBody AdminNotifyUserDTO dto
    ) {
        adminService.notifyUser(actor, dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
