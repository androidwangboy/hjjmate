package vip.mate.agent.api.model;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentApiEntityMappingTest {

    @Test
    void publicationMapsToAgentApiPublicationTable() {
        TableName annotation = AgentApiPublicationEntity.class.getAnnotation(TableName.class);
        assertNotNull(annotation);
        assertEquals("mate_agent_api_publication", annotation.value());
    }

    @Test
    void keyMapsToAgentApiKeyTable() {
        TableName annotation = AgentApiKeyEntity.class.getAnnotation(TableName.class);
        assertNotNull(annotation);
        assertEquals("mate_agent_api_key", annotation.value());
    }

    @Test
    void requestLogAndTaskMapToDedicatedTables() {
        assertEquals("mate_agent_api_request_log",
                AgentApiRequestLogEntity.class.getAnnotation(TableName.class).value());
        assertEquals("mate_agent_api_task",
                AgentApiTaskEntity.class.getAnnotation(TableName.class).value());
    }
}
