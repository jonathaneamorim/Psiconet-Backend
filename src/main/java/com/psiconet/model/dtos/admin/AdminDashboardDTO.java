package com.psiconet.model.dtos.admin;

import java.time.LocalDateTime;

/** Indicadores agregados do painel administrativo (sem nenhum dado pessoal ou clínico). */
public record AdminDashboardDTO(
        long totalUsers,
        long activeUsers,
        long inactiveUsers,
        long activePsychologists,
        long activePatients,
        long totalPsychologists,
        long totalPatients,
        long totalAdmins,
        TicketCounts tickets,
        LocalDateTime generatedAt
) {
    /**
     * @param pending  abertos = aguardando (OPEN) ou em atendimento (IN_PROGRESS)
     * @param finished resolvidos = RESOLVED ou CLOSED
     */
    public record TicketCounts(long open, long inProgress, long resolved, long closed, long pending, long finished) {
        public static TicketCounts of(long open, long inProgress, long resolved, long closed) {
            return new TicketCounts(open, inProgress, resolved, closed, open + inProgress, resolved + closed);
        }
    }
}
