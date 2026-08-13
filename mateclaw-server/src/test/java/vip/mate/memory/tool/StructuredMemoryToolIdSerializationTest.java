package vip.mate.memory.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.memory.MemoryProperties;
import vip.mate.memory.identity.MemoryOwnerResolver;
import vip.mate.memory.service.StructuredMemoryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuredMemoryToolIdSerializationTest {

    @Test
    @DisplayName("recall_structured returns agentId as a JSON string to preserve snowflake precision")
    void recallStructuredSerializesAgentIdAsString() {
        StructuredMemoryService service = mock(StructuredMemoryService.class);
        when(service.recall(anyLong(), nullable(String.class), nullable(String.class), anyString()))
                .thenReturn(List.of());
        StructuredMemoryTool tool = new StructuredMemoryTool(
                service,
                new MemoryOwnerResolver(),
                new MemoryProperties());

        String json = tool.recall_structured(2079862124134313986L, "reference", "meeting", null);

        assertThat(json).contains("\"agentId\": \"2079862124134313986\"");
        assertThat(json).doesNotContain("\"agentId\": 2079862124134313986");
    }
}
