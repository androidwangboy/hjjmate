package vip.mate.agent.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.ApiKeyView;
import vip.mate.agent.api.dto.AgentApiDtos.CreatedKeyView;
import vip.mate.agent.api.dto.AgentApiDtos.ApiLogView;
import vip.mate.agent.api.dto.AgentApiDtos.ApiStatsView;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.CreateKeyRequest;
import vip.mate.agent.api.dto.AgentApiDtos.PublicationUpdateRequest;
import vip.mate.agent.api.dto.AgentApiDtos.PublicationView;
import vip.mate.agent.api.dto.AgentApiDtos.RestChatResponse;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.api.service.AgentApiExecutionService;
import vip.mate.agent.api.service.AgentApiKeyService;
import vip.mate.agent.api.service.AgentApiLogService;
import vip.mate.agent.api.service.AgentApiPublicationService;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;

import java.util.List;
import java.util.Map;

/** Authenticated management endpoints for one expert's public API. */
@Tag(name = "Agent API管理")
@RestController
@RequestMapping("/api/v1/agents/{agentId}/api")
@RequiredArgsConstructor
public class AgentApiManagementController {

    private final AgentService agentService;
    private final AgentApiPublicationService publicationService;
    private final AgentApiKeyService keyService;
    private final AgentApiLogService logService;
    private final AgentApiExecutionService executionService;

    @Operation(summary = "获取专家 API 发布配置")
    @GetMapping
    @RequireWorkspaceRole("viewer")
    public R<PublicationView> getPublication(
            @PathVariable Long agentId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        AgentApiPublicationEntity publication = publicationService.getOrCreate(agentId, wsId);
        return R.ok(toPublicationView(publication));
    }

    @Operation(summary = "更新专家 API 发布配置")
    @PutMapping
    @RequireWorkspaceRole("member")
    public R<PublicationView> updatePublication(
            @PathVariable Long agentId,
            @RequestBody PublicationUpdateRequest request,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        return R.ok(toPublicationView(publicationService.update(agentId, wsId, request)));
    }

    @Operation(summary = "获取专家 API Key 列表")
    @GetMapping("/keys")
    @RequireWorkspaceRole("viewer")
    public R<List<ApiKeyView>> listKeys(
            @PathVariable Long agentId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        return R.ok(keyService.listByAgent(agentId).stream().map(this::toKeyView).toList());
    }

    @Operation(summary = "创建专家 API Key")
    @PostMapping("/keys")
    @RequireWorkspaceRole("member")
    public R<CreatedKeyView> createKey(
            @PathVariable Long agentId,
            @RequestBody CreateKeyRequest request,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        AgentApiPublicationEntity publication = publicationService.getOrCreate(agentId, wsId);
        AgentApiKeyService.CreatedKey created = keyService.create(
                publication.getId(), agentId, wsId,
                request == null ? null : request.name(),
                request == null ? null : request.requestsPerMinuteOverride(),
                request == null ? null : request.concurrentLimitOverride(),
                request == null ? null : request.dailyQuotaOverride());
        return R.ok(new CreatedKeyView(toKeyView(created.entity()), created.plaintext()));
    }

    @Operation(summary = "获取专家 API 调用日志")
    @GetMapping("/logs")
    @RequireWorkspaceRole("viewer")
    public R<List<ApiLogView>> logs(
            @PathVariable Long agentId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String protocol) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        return R.ok(logService.list(agentId, status, protocol, page, size));
    }

    @Operation(summary = "获取专家 API 调用统计")
    @GetMapping("/stats")
    @RequireWorkspaceRole("viewer")
    public R<ApiStatsView> stats(
            @PathVariable Long agentId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        return R.ok(logService.stats(agentId));
    }

    @Operation(summary = "使用当前登录身份测试专家 API")
    @PostMapping("/test")
    @RequireWorkspaceRole("viewer")
    public R<RestChatResponse> test(
            @PathVariable Long agentId,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        AgentApiExecutionService.ExecutionResult result =
                executionService.executeForManagement(agentId, wsId, request, "management-test");
        return R.ok(new RestChatResponse(
                result.requestId(), result.conversationId(), result.endUserId(), result.content(),
                "completed", Map.of(
                        "promptTokens", result.promptTokens(),
                        "completionTokens", result.completionTokens(),
                        "totalTokens", result.promptTokens() + result.completionTokens())));
    }

    @Operation(summary = "撤销专家 API Key")
    @DeleteMapping("/keys/{keyId}")
    @RequireWorkspaceRole("member")
    public R<Void> revokeKey(
            @PathVariable Long agentId,
            @PathVariable Long keyId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        verifyAgentWorkspace(agentId, wsId);
        keyService.revoke(agentId, keyId);
        return R.ok();
    }

    private void verifyAgentWorkspace(Long agentId, long workspaceId) {
        var agent = agentService.getAgent(agentId);
        if (agent.getWorkspaceId() != null && !agent.getWorkspaceId().equals(workspaceId)) {
            throw new MateClawException("err.common.wrong_workspace", 403,
                    "资源不属于当前工作区");
        }
    }

    private PublicationView toPublicationView(AgentApiPublicationEntity publication) {
        long keyCount = keyService.listByAgent(publication.getAgentId()).size();
        ApiStatsView stats = logService.stats(publication.getAgentId());
        return new PublicationView(
                publication.getId(),
                publication.getAgentId(),
                publication.getWorkspaceId(),
                publication.getEnabled(),
                publication.getModelAlias(),
                publication.getRequestsPerMinute(),
                publication.getConcurrentLimit(),
                publication.getDailyQuota(),
                publication.getTimeoutSeconds(),
                publication.getWebhookEnabled(),
                keyCount,
                stats.totalCalls(),
                stats.successRate(),
                stats.averageLatencyMs());
    }

    private ApiKeyView toKeyView(AgentApiKeyEntity key) {
        return new ApiKeyView(
                key.getId(), key.getPublicationId(), key.getAgentId(), key.getName(),
                key.getKeyPrefix(), key.getEnabled(),
                key.getRequestsPerMinuteOverride(), key.getConcurrentLimitOverride(),
                key.getDailyQuotaOverride(), key.getLastUsedAt(), key.getRevokedAt(),
                key.getCreateTime());
    }
}
