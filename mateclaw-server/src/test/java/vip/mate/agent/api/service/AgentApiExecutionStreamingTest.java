package vip.mate.agent.api.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.workspace.conversation.ConversationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AgentApiExecutionStreamingTest {

    @Test
    void streamUsesStructuredAgentDeltas() {
        AgentService agentService = Mockito.mock(AgentService.class);
        ConversationService conversationService = Mockito.mock(ConversationService.class);
        AgentApiPublicationService publicationService = Mockito.mock(AgentApiPublicationService.class);
        AgentApiRateLimiter rateLimiter = new AgentApiRateLimiter();
        AgentApiKeyService keyService = Mockito.mock(AgentApiKeyService.class);
        AgentApiExecutionService service = new AgentApiExecutionService(
                agentService, conversationService, publicationService, rateLimiter, keyService);

        AgentApiKeyEntity key = new AgentApiKeyEntity();
        AgentApiKeyService.AgentApiPrincipal principal =
                new AgentApiKeyService.AgentApiPrincipal(1L, 2L, 3L, 4L, key);
        AgentApiPublicationEntity publication = new AgentApiPublicationEntity();
        publication.setId(2L);
        publication.setAgentId(3L);
        publication.setWorkspaceId(4L);
        publication.setEnabled(true);
        when(agentService.getAgent(3L)).thenReturn(enabledAgent());
        when(agentService.chatStructuredStream(eq(3L), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(new AgentService.StreamDelta("hello", null),
                        new AgentService.StreamDelta(" world", null)));

        service.stream(principal, publication,
                        new ChatRequest("c1", "u1", "hello", List.of()), "rest")
                .blockLast();

        Mockito.verify(agentService).chatStructuredStream(eq(3L), any(), any(), any(), any(), any());
    }

    private static vip.mate.agent.model.AgentEntity enabledAgent() {
        vip.mate.agent.model.AgentEntity agent = new vip.mate.agent.model.AgentEntity();
        agent.setId(3L);
        agent.setEnabled(true);
        agent.setWorkspaceId(4L);
        return agent;
    }
}
