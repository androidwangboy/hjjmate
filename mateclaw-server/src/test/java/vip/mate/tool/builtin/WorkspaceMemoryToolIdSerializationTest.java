package vip.mate.tool.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.memory.MemoryProperties;
import vip.mate.memory.identity.MemoryOwnerResolver;
import vip.mate.memory.service.MemoryRecallTracker;
import vip.mate.workspace.document.WorkspaceFileService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceMemoryToolIdSerializationTest {

    @Test
    @DisplayName("search_workspace_memory returns agentId as a JSON string to preserve snowflake precision")
    void searchWorkspaceMemorySerializesAgentIdAsString() {
        WorkspaceFileService files = mock(WorkspaceFileService.class);
        when(files.searchSnippets(anyLong(), anyString(), anySet(), anyInt(), anyString()))
                .thenReturn(List.of());
        WorkspaceMemoryTool tool = new WorkspaceMemoryTool(
                files,
                mock(MemoryRecallTracker.class),
                new MemoryOwnerResolver(),
                new MemoryProperties());

        String json = tool.search_workspace_memory(2079862124134313986L, "meeting", "all", 10, null);

        assertThat(json).contains("\"agentId\": \"2079862124134313986\"");
        assertThat(json).doesNotContain("\"agentId\": 2079862124134313986");
    }
}
