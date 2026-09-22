package vip.mate.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import vip.mate.workspace.conversation.model.MessageContentPart;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Wire DTOs for management and public expert API endpoints. */
public final class AgentApiDtos {

    private AgentApiDtos() {
    }

    public record PublicationUpdateRequest(
            Boolean enabled,
            String modelAlias,
            Integer requestsPerMinute,
            Integer concurrentLimit,
            Integer dailyQuota,
            Integer timeoutSeconds,
            Boolean webhookEnabled) {
    }

    public record PublicationView(
            Long id,
            Long agentId,
            Long workspaceId,
            Boolean enabled,
            String modelAlias,
            Integer requestsPerMinute,
            Integer concurrentLimit,
            Integer dailyQuota,
            Integer timeoutSeconds,
            Boolean webhookEnabled,
            long keyCount,
            long todayCalls,
            double successRate,
            long averageLatencyMs) {
    }

    public record CreateKeyRequest(
            String name,
            Integer requestsPerMinuteOverride,
            Integer concurrentLimitOverride,
            Integer dailyQuotaOverride) {
    }

    public record ApiKeyView(
            Long id,
            Long publicationId,
            Long agentId,
            String name,
            String keyPrefix,
            Boolean enabled,
            Integer requestsPerMinuteOverride,
            Integer concurrentLimitOverride,
            Integer dailyQuotaOverride,
            LocalDateTime lastUsedAt,
            LocalDateTime revokedAt,
            LocalDateTime createTime) {
    }

    public record CreatedKeyView(ApiKeyView key, String plaintext) {
    }

    public record ChatRequest(
            String conversationId,
            String endUserId,
            String message,
            List<MessageContentPart> contentParts) {
    }

    public record AsyncChatRequest(
            String conversationId,
            String endUserId,
            String message,
            List<MessageContentPart> contentParts,
            String callbackUrl) {
    }

    public record TaskView(
            String taskId,
            String status,
            String result,
            String error,
            LocalDateTime createTime,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {
    }

    public record ApiLogView(
            String requestId,
            String taskId,
            String protocol,
            String mode,
            String conversationId,
            String endUserId,
            String status,
            Integer httpStatus,
            String errorCode,
            Long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            LocalDateTime createTime) {
    }

    public record ApiStatsView(
            long totalCalls,
            long successfulCalls,
            long failedCalls,
            double successRate,
            long averageLatencyMs,
            long inputTokens,
            long outputTokens) {
    }

    public record OpenAiRequest(
            String model,
            Boolean stream,
            String user,
            @JsonProperty("conversation_id") String conversationId,
            JsonNode messages) {
    }

    public record OpenAiResponse(
            String id,
            String object,
            long created,
            String model,
            List<OpenAiChoice> choices,
            OpenAiUsage usage) {
    }

    public record OpenAiChoice(
            int index,
            OpenAiMessage message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    public record OpenAiMessage(String role, String content) {
    }

    public record OpenAiUsage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }

    public record RestChatResponse(
            String requestId,
            String conversationId,
            String endUserId,
            String content,
            String status,
            Map<String, Object> usage) {
    }

    public record ErrorBody(String code, String message, Map<String, Object> details) {
    }
}
