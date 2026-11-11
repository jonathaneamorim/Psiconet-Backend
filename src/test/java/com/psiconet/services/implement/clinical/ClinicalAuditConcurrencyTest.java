package com.psiconet.services.implement.clinical;

import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;
import com.psiconet.repositories.clinical.ClinicalAuditLogRepository;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão: a tela do prontuário dispara leituras em paralelo e cada uma grava auditoria.
 * Antes, o lock era liberado antes do commit e a segunda gravação falhava por sequência duplicada
 * ("Não foi possível concluir a operação: um registro conflitante já existe").
 *
 * <p>O H2 serializa essas transações e NÃO reproduz a corrida; o bug só aparece no Postgres. Para
 * validar contra um banco Postgres descartável:</p>
 * <pre>
 * docker exec psiconet_db psql -U psiconet_db -d psiconet -c "CREATE DATABASE psiconet_audit_test"
 * ./mvnw test -Dtest=ClinicalAuditConcurrencyTest \
 *   -Dspring.datasource.url=jdbc:postgresql://localhost:5432/psiconet_audit_test \
 *   -Dspring.datasource.username=psiconet_db -Dspring.datasource.password='${POSTGRES_PASSWORD}' \
 *   -Dspring.datasource.driver-class-name=org.postgresql.Driver \
 *   -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
 * docker exec psiconet_db psql -U psiconet_db -d psiconet -c "DROP DATABASE psiconet_audit_test"
 * </pre>
 */
class ClinicalAuditConcurrencyTest extends IntegrationTestBase {

    @Autowired ClinicalAuditService audit;
    @Autowired ClinicalAuditLogRepository repository;

    @Test
    void gravacoesSimultaneasNaoColidemNaSequencia() throws Exception {
        long before = repository.count();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        UUID recordId = UUID.randomUUID();

        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                audit.record(null, ClinicalAuditActionEnum.RECORD_VIEWED, recordId, recordId);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : results) f.get(30, TimeUnit.SECONDS); // propaga qualquer exceção
        pool.shutdown();

        assertThat(repository.count()).isEqualTo(before + threads);
        assertThat(audit.verifyChain()).isTrue();
    }
}
