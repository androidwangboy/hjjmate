package vip.mate.agent.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiRequest;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiResponse;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.api.service.AgentApiExecutionService;
import vip.mate.agent.api.service.AgentApiKeyService;
import vip.mate.agent.api.service.AgentApiRequestAuthenticator;
import vip.mate.agent.model.AgentEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleControllerTest {

    @Test
    void mapsOpenAiUserMessageToExpertRequestAndReturnsCompletionShape() {
        AgentApiRequestAuthenticator authenticator = mock(AgentApiRequestAuthenticator.class);
        AgentApiExecutionService executionService = mock(AgentApiExecutionService.class);
        OpenAiCompatibleController controller = new OpenAiCompatibleController(authenticator, executionService);
        AgentApiRequestAuthenticator.Context context = context();
        when(authenticator.authenticate(any(HttpServletRequest.class))).thenReturn(context);
        when(executionService.execute(any(), eq(context.publication()), any(ChatRequest.class), eq("openai")))
                .thenReturn(new AgentApiExecutionService.ExecutionResult(
                        "req_1", "conversation-1", "user-1", "answer", 10, 4,
                        "model-x", "provider-x", 15, "openai", "sync"));

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", "hello");
        messages.add(user);

        Object result = controller.chatCompletions(
                mock(HttpServletRequest.class),
                new OpenAiRequest("expert", false, "user-1", "conversation-1", messages));

        OpenAiResponse response = (OpenAiResponse) result;
        assertEquals("chat.completion", response.object());
        assertEquals("answer", response.choices().getFirst().message().content());
        assertEquals(14, response.usage().totalTokens());
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(executionService).execute(any(), eq(context.publication()), request.capture(), eq("openai"));
        assertEquals("hello", request.getValue().message());
        assertEquals("user-1", request.getValue().endUserId());
    }

    private static AgentApiRequestAuthenticator.Context context() {
        AgentApiKeyEntity key = new AgentApiKeyEntity();
        key.setId(1L);
        AgentApiKeyService.AgentApiPrincipal principal =
                new AgentApiKeyService.AgentApiPrincipal(1L, 2L, 3L, 4L, key);
        AgentApiPublicationEntity publication = new AgentApiPublicationEntity();
        publication.setId(2L);
        publication.setAgentId(3L);
        publication.setWorkspaceId(4L);
        publication.setEnabled(true);
        AgentEntity agent = new AgentEntity();
        agent.setId(3L);
        agent.setEnabled(true);
        agent.setWorkspaceId(4L);
        return new AgentApiRequestAuthenticator.Context(principal, publication, agent);
    }
}
