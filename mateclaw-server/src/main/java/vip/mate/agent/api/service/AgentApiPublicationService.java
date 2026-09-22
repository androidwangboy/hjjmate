package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.api.dto.AgentApiDtos.PublicationUpdateRequest;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.api.repository.AgentApiPublicationMapper;
import vip.mate.exception.MateClawException;

/** Manages per-agent publication state and default API governance. */
@Service
@RequiredArgsConstructor
public class AgentApiPublicationService {

    public static final String DEFAULT_MODEL_ALIAS = "expert";
    public static final int DEFAULT_REQUESTS_PER_MINUTE = 60;
    public static final int DEFAULT_CONCURRENT_LIMIT = 4;
    public static final int DEFAULT_DAILY_QUOTA = 10_000;
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final AgentApiPublicationMapper mapper;

    public AgentApiPublicationEntity getOrCreate(Long agentId, Long workspaceId) {
        validateContext(agentId, workspaceId);
        AgentApiPublicationEntity existing = find(agentId);
        if (existing != null) {
            verifyWorkspace(existing, workspaceId);
            return existing;
        }
        AgentApiPublicationEntity created = defaults(agentId, workspaceId);
        mapper.insert(created);
        return created;
    }

    public AgentApiPublicationEntity getExisting(Long agentId, Long workspaceId) {
        validateContext(agentId, workspaceId);
        AgentApiPublicationEntity existing = find(agentId);
        if (existing == null) return null;
        verifyWorkspace(existing, workspaceId);
        return existing;
    }

    public AgentApiPublicationEntity update(Long agentId,
                                            Long workspaceId,
                                            PublicationUpdateRequest request) {
        AgentApiPublicationEntity entity = getOrCreate(agentId, workspaceId);
        if (request == null) {
            throw new MateClawException("err.agent_api.invalid_request", 400,
                    "Publication request is required");
        }
        if (request.requestsPerMinute() != null && request.requestsPerMinute() <= 0) {
            throw invalidLimit("requestsPerMinute");
        }
        if (request.concurrentLimit() != null && request.concurrentLimit() <= 0) {
            throw invalidLimit("concurrentLimit");
        }
        if (request.dailyQuota() != null && request.dailyQuota() <= 0) {
            throw invalidLimit("dailyQuota");
        }
        if (request.timeoutSeconds() != null && request.timeoutSeconds() <= 0) {
            throw invalidLimit("timeoutSeconds");
        }

        if (request.enabled() != null) entity.setEnabled(request.enabled());
        if (StringUtils.hasText(request.modelAlias())) {
            if (!DEFAULT_MODEL_ALIAS.equals(request.modelAlias().trim())) {
                throw new MateClawException("err.agent_api.invalid_model_alias", 400,
                        "modelAlias must be expert");
            }
            entity.setModelAlias(request.modelAlias().trim());
        }
        if (request.requestsPerMinute() != null) entity.setRequestsPerMinute(request.requestsPerMinute());
        if (request.concurrentLimit() != null) entity.setConcurrentLimit(request.concurrentLimit());
        if (request.dailyQuota() != null) entity.setDailyQuota(request.dailyQuota());
        if (request.timeoutSeconds() != null) entity.setTimeoutSeconds(request.timeoutSeconds());
        if (request.webhookEnabled() != null) entity.setWebhookEnabled(request.webhookEnabled());
        mapper.updateById(entity);
        return entity;
    }

    public void requirePublished(AgentApiPublicationEntity entity) {
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
            throw new MateClawException("err.agent_api.not_published", 403,
                    "Agent API is not published");
        }
    }

    private AgentApiPublicationEntity find(Long agentId) {
        return mapper.selectOne(new LambdaQueryWrapper<AgentApiPublicationEntity>()
                .eq(AgentApiPublicationEntity::getAgentId, agentId)
                .eq(AgentApiPublicationEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private static AgentApiPublicationEntity defaults(Long agentId, Long workspaceId) {
        AgentApiPublicationEntity entity = new AgentApiPublicationEntity();
        entity.setAgentId(agentId);
        entity.setWorkspaceId(workspaceId);
        entity.setEnabled(false);
        entity.setModelAlias(DEFAULT_MODEL_ALIAS);
        entity.setRequestsPerMinute(DEFAULT_REQUESTS_PER_MINUTE);
        entity.setConcurrentLimit(DEFAULT_CONCURRENT_LIMIT);
        entity.setDailyQuota(DEFAULT_DAILY_QUOTA);
        entity.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        entity.setWebhookEnabled(false);
        entity.setDeleted(0);
        return entity;
    }

    private static void validateContext(Long agentId, Long workspaceId) {
        if (agentId == null || workspaceId == null) {
            throw new MateClawException("err.agent_api.invalid_context", 400,
                    "agentId and workspaceId are required");
        }
    }

    private static void verifyWorkspace(AgentApiPublicationEntity entity, Long workspaceId) {
        if (entity.getWorkspaceId() != null && !entity.getWorkspaceId().equals(workspaceId)) {
            throw new MateClawException("err.common.wrong_workspace", 403,
                    "资源不属于当前工作区");
        }
    }

    private static MateClawException invalidLimit(String field) {
        return new MateClawException("err.agent_api.invalid_limit", 400,
                field + " must be greater than zero");
    }
}
