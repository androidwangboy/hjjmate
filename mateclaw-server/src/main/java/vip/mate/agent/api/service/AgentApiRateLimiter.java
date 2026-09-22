package vip.mate.agent.api.service;

import org.springframework.stereotype.Service;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** In-process request, quota and concurrency guard for public expert APIs. */
@Service
public class AgentApiRateLimiter {

    private final ConcurrentMap<String, AtomicInteger> requestWindows = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Semaphore> concurrency = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> dailyWindows = new ConcurrentHashMap<>();

    public Permit acquire(AgentApiKeyService.AgentApiPrincipal principal,
                          AgentApiPublicationEntity publication) {
        AgentApiKeyEntity key = principal.key();
        int rpm = overrideOrDefault(key.getRequestsPerMinuteOverride(), publication.getRequestsPerMinute());
        int concurrentLimit = overrideOrDefault(key.getConcurrentLimitOverride(), publication.getConcurrentLimit());
        int dailyQuota = overrideOrDefault(key.getDailyQuotaOverride(), publication.getDailyQuota());

        String concurrencyKey = principal.keyId() + ":" + principal.publicationId();
        Semaphore semaphore = concurrency.computeIfAbsent(
                concurrencyKey, ignored -> new Semaphore(concurrentLimit));
        if (!semaphore.tryAcquire()) {
            throw rateLimited("concurrent limit reached", 1);
        }

        boolean accepted = false;
        try {
            String minuteKey = concurrencyKey + ":m:" + (Instant.now().getEpochSecond() / 60);
            if (increment(requestWindows, minuteKey) > rpm) {
                throw rateLimited("requests per minute limit reached", 60);
            }
            String dayKey = concurrencyKey + ":d:" + LocalDate.now();
            if (increment(dailyWindows, dayKey) > dailyQuota) {
                throw rateLimited("daily quota reached", 3600);
            }
            accepted = true;
            return new Permit(semaphore);
        } finally {
            if (!accepted) semaphore.release();
        }
    }

    private static int increment(ConcurrentMap<String, AtomicInteger> map, String key) {
        return map.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private static int overrideOrDefault(Integer override, Integer defaultValue) {
        int value = override != null ? override : defaultValue == null ? 1 : defaultValue;
        return Math.max(value, 1);
    }

    private static AgentApiException rateLimited(String reason, int retryAfterSeconds) {
        return new AgentApiException(429, "agent_api_rate_limited",
                reason + "; retry after " + retryAfterSeconds + " seconds",
                java.util.Map.of("retryAfterSeconds", retryAfterSeconds));
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            semaphore.release();
        }
    }
}
