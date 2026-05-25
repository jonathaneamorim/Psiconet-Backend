package com.psiconet.controllers.admin;

import com.psiconet.model.dtos.admin.UserStatusUpdateDTO;
import com.psiconet.model.dtos.admin.UserUpdateAdminDTO;
import com.psiconet.model.dtos.admin.UsersToListAdminDTO;
import com.psiconet.services.interfaces.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<Page<UsersToListAdminDTO>> listUsers(
            @PageableDefault(size = 10, sort = "fullName") Pageable pageable
    ) {
        return ResponseEntity.ok(adminService.listUsers(pageable));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusUpdateDTO statusDto
    ) {
        adminService.updateUserStatus(id, statusDto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Void> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateAdminDTO updateDto
    ) {
        adminService.updateUser(id, updateDto);
        return ResponseEntity.noContent().build();
    }
}
