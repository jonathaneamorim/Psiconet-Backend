package com.psiconet.infra.ratelimit;

import com.psiconet.infra.exceptions.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Buckets em memória (um por política + chave). Adequado para uma instância da API; para várias
 * instâncias, trocar o mapa por um ProxyManager distribuído do Bucket4j (Redis/Postgres) — ver ADR 004.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final Duration IDLE_EVICTION = Duration.ofHours(25);

    private final RateLimitProperties properties;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    /**
     * Consome 1 token da política para a chave informada.
     *
     * @throws TooManyRequestsException se o limite foi excedido
     */
    public void consume(String policyName, String key) {
        if (!properties.isEnabled() || key == null || key.isBlank()) return;

        Entry entry = buckets.computeIfAbsent(policyName + "|" + key, k -> new Entry(newBucket(policyName)));
        entry.lastAccess = System.nanoTime();

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
            throw new TooManyRequestsException(retryAfter);
        }
    }

    private Bucket newBucket(String policyName) {
        RateLimitProperties.Policy policy = properties.policy(policyName);
        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.getCapacity())
                .refillGreedy(policy.getCapacity(), policy.getPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    // Remove buckets ociosos para o mapa não crescer indefinidamente.
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void evictIdle() {
        long threshold = System.nanoTime() - IDLE_EVICTION.toNanos();
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess < threshold);
    }

    /** Usado em testes. */
    public void reset() {
        buckets.clear();
    }

    private static final class Entry {
        private final Bucket bucket;
        private volatile long lastAccess = System.nanoTime();

        private Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }
}
