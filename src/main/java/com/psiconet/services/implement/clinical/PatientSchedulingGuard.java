package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.UserStatusEnum;

/**
 * Só pacientes com conta ACTIVE podem receber consultas: inativos, bloqueados ou com cadastro
 * pendente não conseguem nem autenticar, então não teriam como ver, cancelar ou pagar a consulta.
 */
public final class PatientSchedulingGuard {

    private PatientSchedulingGuard() {}

    public static boolean canBeScheduled(User patientUser) {
        return patientUser != null && patientUser.getStatus() == UserStatusEnum.ACTIVE;
    }

    public static void requireSchedulable(User patientUser) {
        if (!canBeScheduled(patientUser)) {
            throw new BusinessException(
                    "patientId",
                    "Não é possível agendar consultas com este paciente: a conta dele não está ativa."
            );
        }
    }
}
