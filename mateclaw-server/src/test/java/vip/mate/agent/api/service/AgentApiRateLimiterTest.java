package vip.mate.agent.api.service;

import org.junit.jupiter.api.Test;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentApiRateLimiterTest {

    @Test
    void keyOverrideTakesPrecedenceOverPublicationDefault() {
        AgentApiPublicationEntity publication = publication(100, 1);
        AgentApiKeyEntity key = key(9, 1);
        key.setConcurrentLimitOverride(1);
        AgentApiKeyService.AgentApiPrincipal principal =
                new AgentApiKeyService.AgentApiPrincipal(9L, 1L, 42L, 7L, key);
        AgentApiRateLimiter limiter = new AgentApiRateLimiter();

        AgentApiRateLimiter.Permit first = limiter.acquire(principal, publication);
        assertThrows(AgentApiException.class, () -> limiter.acquire(principal, publication));
        first.close();
        assertDoesNotThrow(() -> limiter.acquire(principal, publication).close());
    }

    @Test
    void publicationRateLimitRejectsAfterConfiguredWindowCount() {
        AgentApiPublicationEntity publication = publication(1, 1);
        publication.setRequestsPerMinute(1);
        AgentApiKeyService.AgentApiPrincipal principal =
                new AgentApiKeyService.AgentApiPrincipal(9L, 1L, 42L, 7L, key(9, 1));
        AgentApiRateLimiter limiter = new AgentApiRateLimiter();

        AgentApiRateLimiter.Permit first = limiter.acquire(principal, publication);
        first.close();
        assertThrows(AgentApiException.class, () -> limiter.acquire(principal, publication));
    }

    private static AgentApiPublicationEntity publication(int rpm, int concurrent) {
        AgentApiPublicationEntity publication = new AgentApiPublicationEntity();
        publication.setId(1L);
        publication.setAgentId(42L);
        publication.setRequestsPerMinute(rpm);
        publication.setConcurrentLimit(concurrent);
        publication.setDailyQuota(100);
        return publication;
    }

    private static AgentApiKeyEntity key(long id, long publicationId) {
        AgentApiKeyEntity key = new AgentApiKeyEntity();
        key.setId(id);
        key.setPublicationId(publicationId);
        key.setAgentId(42L);
        key.setWorkspaceId(7L);
        key.setEnabled(true);
        return key;
    }
}
