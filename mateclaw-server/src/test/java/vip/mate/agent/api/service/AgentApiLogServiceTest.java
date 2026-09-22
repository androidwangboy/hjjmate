package vip.mate.agent.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.api.model.AgentApiRequestLogEntity;
import vip.mate.agent.api.repository.AgentApiRequestLogMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApiLogServiceTest {

    @Mock
    private AgentApiRequestLogMapper mapper;

    @InjectMocks
    private AgentApiLogService service;

    @Test
    void statsCalculatesSuccessRateAndAverageLatency() {
        AgentApiRequestLogEntity ok = row("completed", 200, 100L, 10, 20);
        AgentApiRequestLogEntity failed = row("failed", 502, 300L, 0, 0);
        when(mapper.selectList(any())).thenReturn(List.of(ok, failed));

        var stats = service.stats(42L);

        assertEquals(2, stats.totalCalls());
        assertEquals(1, stats.successfulCalls());
        assertEquals(1, stats.failedCalls());
        assertEquals(50D, stats.successRate());
        assertEquals(200L, stats.averageLatencyMs());
        assertEquals(10L, stats.inputTokens());
        assertEquals(20L, stats.outputTokens());
    }

    private static AgentApiRequestLogEntity row(String status, int httpStatus,
                                                 long latency, int input, int output) {
        AgentApiRequestLogEntity row = new AgentApiRequestLogEntity();
        row.setAgentId(42L);
        row.setStatus(status);
        row.setHttpStatus(httpStatus);
        row.setLatencyMs(latency);
        row.setInputTokens(input);
        row.setOutputTokens(output);
        row.setCreateTime(LocalDateTime.now());
        return row;
    }
}
