package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.api.dto.AgentApiDtos.AsyncChatRequest;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.api.model.AgentApiTaskEntity;
import vip.mate.agent.api.repository.AgentApiTaskMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApiTaskServiceTest {

    @Mock
    private AgentApiTaskMapper mapper;

    @Mock
    private AgentApiExecutionService executionService;

    @InjectMocks
    private AgentApiTaskService service;

    @Test
    void submitRequiresHttpsCallbackOrLocalDevelopmentHost() {
        AgentApiKeyEntity key = new AgentApiKeyEntity();
        key.setId(8L);
        AgentApiKeyService.AgentApiPrincipal principal =
                new AgentApiKeyService.AgentApiPrincipal(8L, 9L, 42L, 7L, key);
        AgentApiPublicationEntity publication = new AgentApiPublicationEntity();
        publication.setId(9L);
        publication.setEnabled(true);

        assertThrows(IllegalArgumentException.class, () -> service.submit(
                principal, publication,
                new AsyncChatRequest("c1", "u1", "hello", List.of(), "http://example.com/callback")));
    }

    @Test
    void taskViewReflectsPersistedTerminalResult() {
        AgentApiTaskEntity task = new AgentApiTaskEntity();
        task.setTaskId("task_1");
        task.setStatus("completed");
        task.setResultText("done");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        var result = service.getForKey("task_1", 8L, 42L);

        assertEquals("task_1", result.taskId());
        assertEquals("completed", result.status());
        assertEquals("done", result.result());
    }
}
