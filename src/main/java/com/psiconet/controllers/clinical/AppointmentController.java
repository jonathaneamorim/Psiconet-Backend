package com.psiconet.controllers.clinical;

import com.psiconet.model.dtos.clinical.AppointmentCancelDTO;
import com.psiconet.model.dtos.clinical.AppointmentCreateDTO;
import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.dtos.clinical.AppointmentStatsDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.clinical.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<AppointmentDTO> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AppointmentCreateDTO dto
    ) {
        return ResponseEntity.ok(appointmentService.create(user, dto));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AppointmentDTO> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody(required = false) AppointmentCancelDTO dto
    ) {
        return ResponseEntity.ok(appointmentService.cancel(user, id, dto));
    }

    @GetMapping
    public ResponseEntity<Page<AppointmentDTO>> listMine(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(appointmentService.listMyAppointments(user, pageable));
    }

    // Contagem por status das consultas do mês exibido no calendário (ex.: 1 pendente, 2 aceitas...).
    @GetMapping("/stats")
    public ResponseEntity<AppointmentStatsDTO> getMonthlyStats(
            @AuthenticationPrincipal User user,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(appointmentService.getMonthlyStats(user, year, month));
    }
}