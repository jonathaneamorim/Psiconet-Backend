package com.psiconet.controllers.clinical;

import com.psiconet.model.dtos.clinical.AppointmentCancelDTO;
import com.psiconet.model.dtos.clinical.AppointmentCreateDTO;
import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.dtos.clinical.AppointmentStatsDTO;
import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.video.VideoJoinResponseDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.clinical.AppointmentService;
import com.psiconet.services.interfaces.video.VideoSessionService;
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
    private final VideoSessionService videoSessionService;

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

    // Detalhe de uma consulta (só psicólogo ou paciente do vínculo; demais recebem 404).
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentDTO> getById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(appointmentService.getById(user, id));
    }

    // Credencial de entrada na sala de vídeo integrada (ADR 016). Token curto — guardar só em memória.
    @PostMapping("/{id}/video/join")
    @RateLimited("video-join")
    public ResponseEntity<VideoJoinResponseDTO> joinVideo(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(videoSessionService.join(user, id));
    }

    // Psicólogo encerra a sessão para todos (desconecta os participantes).
    @PostMapping("/{id}/video/end")
    @RateLimited("video-join")
    public ResponseEntity<Void> endVideo(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        videoSessionService.end(user, id);
        return ResponseEntity.noContent().build();
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