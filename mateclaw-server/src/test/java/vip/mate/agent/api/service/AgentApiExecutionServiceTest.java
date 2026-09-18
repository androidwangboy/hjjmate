package vip.mate.agent.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.workspace.conversation.ConversationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AgentApiExecutionServiceTest {

    @Mock
    private AgentService agentService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private AgentApiPublicationService publicationService;
    @Mock
    private AgentApiRateLimiter rateLimiter;
    @Mock
    private AgentApiKeyService keyService;

    @Test
    void requiresConversationAndExternalUserIdentity() {
        AgentApiExecutionService service = new AgentApiExecutionService(
                agentService, conversationService, publicationService, rateLimiter, keyService);
        AgentApiKeyEntity key = new AgentApiKeyEntity();
        AgentApiKeyService.AgentApiPrincipal principal =
                new AgentApiKeyService.AgentApiPrincipal(1L, 2L, 3L, 4L, key);
        AgentApiPublicationEntity publication = new AgentApiPublicationEntity();
        publication.setEnabled(true);

        assertThrows(IllegalArgumentException.class, () -> service.execute(
                principal, publication,
                new ChatRequest("", "", "hello", List.of()), "rest"));
    }

    @Test
    void conversationKeyIsStableAndDoesNotExposeExternalIdentity() {
        String a = AgentApiExecutionService.internalConversationId(42L, "user-100", "session-1");
        String b = AgentApiExecutionService.internalConversationId(42L, "user-100", "session-1");

        org.junit.jupiter.api.Assertions.assertEquals(a, b);
        org.junit.jupiter.api.Assertions.assertTrue(a.startsWith("api:42:"));
        org.junit.jupiter.api.Assertions.assertFalse(a.contains("user-100"));
        org.junit.jupiter.api.Assertions.assertFalse(a.contains("session-1"));
    }
}
