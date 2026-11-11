package com.psiconet.services.interfaces.audit;

import com.psiconet.model.entities.access.User;

/** Registro de auditoria de ações administrativas e de segurança (tabela log_alteracao). */
public interface AuditService {

    /**
     * @param actor      quem executou (null = sistema)
     * @param action     código estável da ação (ex.: USER_INACTIVATED, ADMIN_CREATED)
     * @param targetType tipo do alvo (ex.: USER, TICKET)
     * @param targetId   id do alvo (pode ser null)
     * @param details    texto curto SEM dados sensíveis (sem senha, token, CPF ou conteúdo clínico)
     */
    void record(User actor, String action, String targetType, Object targetId, String details);
}
