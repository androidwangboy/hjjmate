package vip.mate.wiki.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import vip.mate.wiki.service.HybridRetriever;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.wiki.service.WikiPageService;
import vip.mate.wiki.service.WikiPageTypePermissionService;
import vip.mate.wiki.service.WikiRawMaterialService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WikiToolIdSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("wiki tools publish agentId as a string parameter so LLM tool calls preserve precision")
    void wikiToolAgentIdSchemasAreString() throws Exception {
        WikiTool tool = new WikiTool(
                mock(WikiPageService.class),
                mock(WikiKnowledgeBaseService.class),
                mock(WikiRawMaterialService.class),
                mock(HybridRetriever.class),
                new ObjectMapper(),
                mock(WikiPageTypePermissionService.class));

        int checked = 0;
        for (ToolCallback callback : ToolCallbacks.from(tool)) {
            JsonNode root = MAPPER.readTree(callback.getToolDefinition().inputSchema());
            JsonNode agentId = root.at("/properties/agentId/type");
            if (!agentId.isMissingNode()) {
                assertThat(agentId.asText())
                        .as(callback.getToolDefinition().name())
                        .isEqualTo("string");
                checked++;
            }
        }

        assertThat(checked).isGreaterThan(0);
    }
}
