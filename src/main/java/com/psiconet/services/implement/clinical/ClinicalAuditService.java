package com.psiconet.services.implement.clinical;

import com.psiconet.infra.web.ClientIpResolver;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.ClinicalAuditLog;
import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;
import com.psiconet.repositories.clinical.ClinicalAuditLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Auditoria clínica append-only com encadeamento de hash.
 * <ul>
 *   <li>Transação própria ({@code REQUIRES_NEW}): o registro de acesso persiste mesmo se a operação de
 *       negócio falhar depois (ex.: tentativa negada continua auditada).</li>
 *   <li>Encadeamento: hash = SHA-256(hash anterior + campos da linha). Alterar/remover qualquer
 *       linha quebra a cadeia ({@link #verifyChain()}).</li>
 *   <li>Numeração sequencial: o lock em memória envolve a transação INTEIRA (leitura do último +
 *       insert + commit). Antes o lock era liberado antes do commit e duas requisições simultâneas
 *       (ex.: a tela do prontuário carrega registro e anotações em paralelo) liam o mesmo "último" e
 *       a segunda falhava por sequência duplicada. Com várias instâncias da API, a UNIQUE da
 *       sequência + nova tentativa resolvem a corrida.</li>
 * </ul>
 * Nunca recebe conteúdo clínico — só ids, ação, papel, IP e horário.
 */
@Service
@RequiredArgsConstructor
public class ClinicalAuditService {

    private static final int MAX_ATTEMPTS = 5;

    private final ClinicalAuditLogRepository repository;
    private final ClientIpResolver clientIpResolver;
    private final PlatformTransactionManager transactionManager;
    private final ReentrantLock lock = new ReentrantLock();
    private TransactionTemplate newTransaction;

    @PostConstruct
    void init() {
        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(User actor, ClinicalAuditActionEnum action, UUID medicalRecordId, Object targetId) {
        // IP resolvido fora da transação (depende da requisição HTTP corrente)
        String ip = clientIpResolver.currentRequestIp();
        for (int attempt = 1; ; attempt++) {
            lock.lock();
            try {
                newTransaction.executeWithoutResult(status -> insertNext(actor, action, medicalRecordId, targetId, ip));
                return;
            } catch (DataIntegrityViolationException e) {
                // Outra instância gravou a mesma sequência: relê o último e tenta de novo
                if (attempt >= MAX_ATTEMPTS) throw e;
            } finally {
                lock.unlock();
            }
        }
    }

    private void insertNext(User actor, ClinicalAuditActionEnum action, UUID medicalRecordId, Object targetId, String ip) {
        ClinicalAuditLog last = repository.findTopByOrderBySequenceDesc().orElse(null);

        ClinicalAuditLog entry = new ClinicalAuditLog();
        entry.setSequence(last == null ? 1 : last.getSequence() + 1);
        entry.setMedicalRecordId(medicalRecordId);
        entry.setActorId(actor == null ? null : actor.getId());
        entry.setActorRole(actor == null ? null : actor.getRole());
        entry.setAction(action);
        entry.setTargetId(targetId == null ? null : targetId.toString());
        entry.setIpAddress(ip);
        // Truncado em micros: o valor relido do banco precisa reproduzir o mesmo hash
        entry.setOccurredAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        entry.setPreviousHash(last == null ? null : last.getHash());
        entry.setHash(hashOf(entry));
        repository.saveAndFlush(entry);
    }

    /** Recalcula toda a cadeia; false se alguma linha foi adulterada, removida ou reordenada. */
    @Transactional(readOnly = true)
    public boolean verifyChain() {
        List<ClinicalAuditLog> all = repository.findAllByOrderBySequenceAsc();
        String previous = null;
        long expectedSeq = 1;
        for (ClinicalAuditLog entry : all) {
            if (entry.getSequence() != expectedSeq++) return false;
            if (!Objects.equals(previous, entry.getPreviousHash())) return false;
            if (!hashOf(entry).equals(entry.getHash())) return false;
            previous = entry.getHash();
        }
        return true;
    }

    static String hashOf(ClinicalAuditLog e) {
        String payload = String.join("|",
                String.valueOf(e.getPreviousHash()),
                String.valueOf(e.getSequence()),
                String.valueOf(e.getMedicalRecordId()),
                String.valueOf(e.getActorId()),
                String.valueOf(e.getActorRole()),
                String.valueOf(e.getAction()),
                String.valueOf(e.getTargetId()),
                String.valueOf(e.getIpAddress()),
                String.valueOf(e.getOccurredAt()));
        return sha256(payload);
    }

    public static String sha256Bytes(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
