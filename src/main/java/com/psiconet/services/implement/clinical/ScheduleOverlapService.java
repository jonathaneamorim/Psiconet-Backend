package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.ScheduleOverlapException;
import com.psiconet.model.dtos.clinical.AppointmentDTO;
import com.psiconet.model.dtos.clinical.OverlapSummaryDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sobreposição de horário na agenda do psicólogo (ADR 019).
 * <ul>
 *   <li><b>Criar</b>: conflito com outra consulta não cancelada exige confirmação ({@code acknowledgeOverlap});
 *       conflito com consulta do MESMO paciente é duplicidade e continua bloqueado.</li>
 *   <li><b>Sinalizar</b>: calculado a cada leitura (nunca armazenado), só entre consultas ACCEPTED ainda não
 *       terminadas, e só para o psicólogo dono — cancelar uma das consultas apaga o alerta sozinho.</li>
 * </ul>
 * Intervalos abertos: terminar às 11h e começar às 11h não é sobreposição.
 */
@Service
@RequiredArgsConstructor
public class ScheduleOverlapService {

    private final AppointmentRepository appointmentRepository;

    /** Consultas não canceladas do psicólogo que cruzam o intervalo (opcionalmente ignorando uma delas). */
    public List<Appointment> conflictsFor(Psychologist psychologist, LocalDateTime start, LocalDateTime end, UUID excludeId) {
        return appointmentRepository.findConflicting(psychologist, start, end).stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .sorted(Comparator.comparing(Appointment::getStartDateTime))
                .toList();
    }

    /** Mesmo paciente no mesmo horário: bloqueio independentemente de confirmação. */
    public boolean includesPatient(List<Appointment> conflicts, UUID patientUserId) {
        return conflicts.stream().anyMatch(a -> a.getTreatmentLink().getPatient().getUser().getId().equals(patientUserId));
    }

    /** Regra de criação comum à consulta avulsa e à 1ª ocorrência de uma série. */
    public void requireCreatable(List<Appointment> conflicts, UUID patientUserId, boolean acknowledged, String field) {
        if (includesPatient(conflicts, patientUserId)) {
            throw new BusinessException(field, "Este paciente já tem consulta neste horário.");
        }
        if (!conflicts.isEmpty() && !acknowledged) {
            throw new ScheduleOverlapException(summarize(conflicts));
        }
    }

    public List<OverlapSummaryDTO> summarize(Collection<Appointment> appointments) {
        return appointments.stream().map(ScheduleOverlapService::summary).toList();
    }

    static OverlapSummaryDTO summary(Appointment a) {
        return new OverlapSummaryDTO(a.getId(), a.getStartDateTime(), a.getEndDateTime(), a.getTitle(),
                a.getTreatmentLink().getPatient().getUser().getFullName());
    }

    /**
     * Preenche hasOverlap/overlappingAppointments nos DTOs se (e só se) o leitor for o psicólogo da consulta.
     * Uma consulta ao banco por janela de datas (mínimo início × máximo fim) + varredura em memória.
     */
    public void decorate(User viewer, Collection<Appointment> appointments, Map<UUID, AppointmentDTO> dtos) {
        Map<UUID, Psychologist> byPsychologist = new HashMap<>();
        Map<UUID, List<Appointment>> owned = new HashMap<>();
        for (Appointment a : appointments) {
            Psychologist psychologist = a.getTreatmentLink().getPsychologist();
            if (!psychologist.getUser().getId().equals(viewer.getId())) continue;
            byPsychologist.putIfAbsent(psychologist.getId(), psychologist);
            owned.computeIfAbsent(psychologist.getId(), k -> new ArrayList<>()).add(a);
        }
        owned.forEach((psychologistId, list) -> {
            Map<UUID, List<OverlapSummaryDTO>> flags = overlapsWithin(byPsychologist.get(psychologistId), list);
            for (Appointment a : list) {
                AppointmentDTO dto = dtos.get(a.getId());
                if (dto == null) continue;
                List<OverlapSummaryDTO> overlaps = flags.getOrDefault(a.getId(), List.of());
                dto.setHasOverlap(!overlaps.isEmpty());
                dto.setOverlappingAppointments(overlaps);
            }
        });
    }

    public void decorate(User viewer, Appointment appointment, AppointmentDTO dto) {
        decorate(viewer, List.of(appointment), Map.of(appointment.getId(), dto));
    }

    private Map<UUID, List<OverlapSummaryDTO>> overlapsWithin(Psychologist psychologist, List<Appointment> targets) {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> alertable = targets.stream()
                .filter(a -> a.getStatus() == AppointmentStatusEnum.ACCEPTED && a.getEndDateTime().isAfter(now))
                .toList();
        if (alertable.isEmpty()) return Map.of();

        LocalDateTime from = alertable.stream().map(Appointment::getStartDateTime).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime to = alertable.stream().map(Appointment::getEndDateTime).max(Comparator.naturalOrder()).orElseThrow();
        // Ordenada por início: para cada alvo, para de procurar quando a próxima começa depois do fim dele.
        List<Appointment> window = appointmentRepository.findAcceptedInWindow(psychologist, from, to).stream()
                .filter(a -> a.getEndDateTime().isAfter(now))
                .toList();

        Map<UUID, List<OverlapSummaryDTO>> result = new HashMap<>();
        for (Appointment target : alertable) {
            List<OverlapSummaryDTO> overlaps = new ArrayList<>();
            for (Appointment other : window) {
                if (!other.getStartDateTime().isBefore(target.getEndDateTime())) break;
                if (other.getId().equals(target.getId())) continue;
                if (other.getEndDateTime().isAfter(target.getStartDateTime())) overlaps.add(summary(other));
            }
            if (!overlaps.isEmpty()) result.put(target.getId(), overlaps);
        }
        return result;
    }
}
