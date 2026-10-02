package com.psiconet.services.implement.audit;

import com.psiconet.infra.web.ClientIpResolver;
import com.psiconet.model.entities.access.ChangeLog;
import com.psiconet.model.entities.access.User;
import com.psiconet.repositories.access.ChangeLogRepository;
import com.psiconet.services.interfaces.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final int MAX_DETAILS = 1000;

    private final ChangeLogRepository changeLogRepository;
    private final ClientIpResolver clientIpResolver;

    // Participa da transação da ação auditada: se a ação sofrer rollback, o registro também some
    // (não auditamos o que não aconteceu); se a auditoria falhar, a ação não é concluída.
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(User actor, String action, String targetType, Object targetId, String details) {
        ChangeLog log = new ChangeLog();
        log.setUser(actor);
        log.setActionPerformed(action);
        log.setTableName(targetType);
        log.setTargetId(targetId == null ? null : targetId.toString());
        log.setIpAddress(clientIpResolver.currentRequestIp());
        log.setDetails(details == null ? null : details.substring(0, Math.min(details.length(), MAX_DETAILS)));
        log.setDateTime(LocalDateTime.now());
        changeLogRepository.save(log);
    }
}
