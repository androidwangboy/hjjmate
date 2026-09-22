package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.agent.api.dto.AgentApiDtos.ApiLogView;
import vip.mate.agent.api.dto.AgentApiDtos.ApiStatsView;
import vip.mate.agent.api.model.AgentApiRequestLogEntity;
import vip.mate.agent.api.repository.AgentApiRequestLogMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Persists metadata-only external API logs and calculates dashboard metrics. */
@Service
@RequiredArgsConstructor
public class AgentApiLogService {

    private final AgentApiRequestLogMapper mapper;

    public void record(AgentApiKeyService.AgentApiPrincipal principal,
                       AgentApiExecutionService.ExecutionResult result,
                       int httpStatus,
                       String status,
                       String errorCode,
                       String taskId) {
        AgentApiRequestLogEntity row = new AgentApiRequestLogEntity();
        row.setRequestId(result.requestId());
        row.setTaskId(taskId);
        row.setPublicationId(principal.publicationId());
        row.setApiKeyId(principal.keyId());
        row.setAgentId(principal.agentId());
        row.setWorkspaceId(principal.workspaceId());
        row.setProtocol(result.protocol());
        row.setMode(result.mode());
        row.setConversationId(result.conversationId());
        row.setEndUserId(result.endUserId());
        row.setStatus(status);
        row.setHttpStatus(httpStatus);
        row.setErrorCode(errorCode);
        row.setLatencyMs(result.latencyMs());
        row.setInputTokens(result.promptTokens());
        row.setOutputTokens(result.completionTokens());
        mapper.insert(row);
    }

    public List<ApiLogView> list(Long agentId, String status, String protocol, int page, int size) {
        List<AgentApiRequestLogEntity> rows = mapper.selectList(new LambdaQueryWrapper<AgentApiRequestLogEntity>()
                .eq(AgentApiRequestLogEntity::getAgentId, agentId)
                .eq(status != null && !status.isBlank(), AgentApiRequestLogEntity::getStatus, status)
                .eq(protocol != null && !protocol.isBlank(), AgentApiRequestLogEntity::getProtocol, protocol)
                .orderByDesc(AgentApiRequestLogEntity::getCreateTime));
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        return rows.subList(from, to).stream().map(AgentApiLogService::toView).toList();
    }

    public ApiStatsView stats(Long agentId) {
        List<AgentApiRequestLogEntity> rows = mapper.selectList(new LambdaQueryWrapper<AgentApiRequestLogEntity>()
                .eq(AgentApiRequestLogEntity::getAgentId, agentId)
                .ge(AgentApiRequestLogEntity::getCreateTime, LocalDate.now().atStartOfDay()));
        long total = rows.size();
        long successful = rows.stream().filter(row -> "completed".equals(row.getStatus())).count();
        long failed = total - successful;
        long latencyTotal = rows.stream().mapToLong(row -> row.getLatencyMs() == null ? 0L : row.getLatencyMs()).sum();
        long input = rows.stream().mapToLong(row -> row.getInputTokens() == null ? 0L : row.getInputTokens()).sum();
        long output = rows.stream().mapToLong(row -> row.getOutputTokens() == null ? 0L : row.getOutputTokens()).sum();
        double successRate = total == 0 ? 0D : successful * 100D / total;
        return new ApiStatsView(total, successful, failed, successRate,
                total == 0 ? 0L : latencyTotal / total, input, output);
    }

    private static ApiLogView toView(AgentApiRequestLogEntity row) {
        return new ApiLogView(row.getRequestId(), row.getTaskId(), row.getProtocol(), row.getMode(),
                row.getConversationId(), row.getEndUserId(), row.getStatus(), row.getHttpStatus(),
                row.getErrorCode(), row.getLatencyMs(), row.getInputTokens(), row.getOutputTokens(),
                row.getCreateTime());
    }
}
