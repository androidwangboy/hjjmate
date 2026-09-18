package vip.mate.agent.api.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.model.AgentEntity;

/** Resolves an external API key into a fixed expert execution context. */
@Service
@RequiredArgsConstructor
public class AgentApiRequestAuthenticator {

    private final AgentApiKeyService keyService;
    private final AgentApiPublicationService publicationService;
    private final AgentService agentService;

    public Context authenticate(HttpServletRequest request) {
        String token = extractToken(request);
        AgentApiKeyService.AgentApiPrincipal principal = keyService.authenticate(token)
                .orElseThrow(AgentApiException::unauthorized);
        AgentApiPublicationEntity publication = publicationService.getExisting(
                principal.agentId(), principal.workspaceId());
        if (publication == null || !Boolean.TRUE.equals(publication.getEnabled())) {
            throw AgentApiException.notPublished();
        }
        AgentEntity agent;
        try {
            agent = agentService.getAgent(principal.agentId());
        } catch (RuntimeException e) {
            throw AgentApiException.notPublished();
        }
        if (!Boolean.TRUE.equals(agent.getEnabled())
                || (agent.getWorkspaceId() != null
                && !agent.getWorkspaceId().equals(principal.workspaceId()))) {
            throw AgentApiException.notPublished();
        }
        return new Context(principal, publication, agent);
    }

    static String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring("Bearer ".length()).trim();
        }
        String apiKey = request.getHeader("X-API-Key");
        return StringUtils.hasText(apiKey) ? apiKey.trim() : null;
    }

    public record Context(AgentApiKeyService.AgentApiPrincipal principal,
                          AgentApiPublicationEntity publication,
                          AgentEntity agent) {
    }
}
