package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.repository.AgentApiKeyMapper;
import vip.mate.exception.MateClawException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Creates and authenticates expert-scoped API keys. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApiKeyService {

    public static final String KEY_PREFIX = "mak_";
    private static final int KEY_BYTES = 32;
    private static final long LAST_USED_DEBOUNCE_SECONDS = 60L;

    private final AgentApiKeyMapper mapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CreatedKey create(Long publicationId,
                             Long agentId,
                             Long workspaceId,
                             String name,
                             Integer requestsPerMinuteOverride,
                             Integer concurrentLimitOverride,
                             Integer dailyQuotaOverride) {
        if (publicationId == null || agentId == null || workspaceId == null) {
            throw new MateClawException("err.agent_api.invalid_publication", 400,
                    "API publication context is required");
        }
        validateOverride(requestsPerMinuteOverride, "requestsPerMinuteOverride");
        validateOverride(concurrentLimitOverride, "concurrentLimitOverride");
        validateOverride(dailyQuotaOverride, "dailyQuotaOverride");

        String plaintext = generatePlaintext();
        AgentApiKeyEntity entity = new AgentApiKeyEntity();
        entity.setPublicationId(publicationId);
        entity.setAgentId(agentId);
        entity.setWorkspaceId(workspaceId);
        entity.setName(StringUtils.hasText(name) ? name.trim() : "未命名调用方");
        entity.setKeyPrefix(plaintext.substring(0, Math.min(12, plaintext.length())));
        entity.setTokenHash(sha256Hex(plaintext));
        entity.setEnabled(true);
        entity.setRequestsPerMinuteOverride(requestsPerMinuteOverride);
        entity.setConcurrentLimitOverride(concurrentLimitOverride);
        entity.setDailyQuotaOverride(dailyQuotaOverride);
        entity.setDeleted(0);
        mapper.insert(entity);
        return new CreatedKey(plaintext, entity);
    }

    public List<AgentApiKeyEntity> listByAgent(Long agentId) {
        if (agentId == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getAgentId, agentId)
                .eq(AgentApiKeyEntity::getDeleted, 0)
                .orderByDesc(AgentApiKeyEntity::getCreateTime));
    }

    public Optional<AgentApiPrincipal> authenticate(String plaintext) {
        if (!StringUtils.hasText(plaintext) || !plaintext.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        AgentApiKeyEntity key = mapper.selectOne(new LambdaQueryWrapper<AgentApiKeyEntity>()
                .eq(AgentApiKeyEntity::getTokenHash, sha256Hex(plaintext))
                .eq(AgentApiKeyEntity::getEnabled, true)
                .eq(AgentApiKeyEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (key == null || key.getRevokedAt() != null) {
            return Optional.empty();
        }
        return Optional.of(new AgentApiPrincipal(
                key.getId(), key.getPublicationId(), key.getAgentId(), key.getWorkspaceId(), key));
    }

    public void recordUse(AgentApiKeyEntity key) {
        if (key == null || key.getId() == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (key.getLastUsedAt() != null
                && key.getLastUsedAt().plusSeconds(LAST_USED_DEBOUNCE_SECONDS).isAfter(now)) {
            return;
        }
        try {
            AgentApiKeyEntity patch = new AgentApiKeyEntity();
            patch.setId(key.getId());
            patch.setLastUsedAt(now);
            mapper.updateById(patch);
            key.setLastUsedAt(now);
        } catch (Exception e) {
            log.debug("[AgentApi] last_used_at write failed for key {}: {}", key.getId(), e.getMessage());
        }
    }

    public void revoke(Long agentId, Long keyId) {
        AgentApiKeyEntity existing = keyId == null ? null : mapper.selectById(keyId);
        if (existing == null || !agentIdEquals(agentId, existing.getAgentId())
                || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new MateClawException("err.agent_api.key_not_found", 404,
                    "API key not found");
        }
        existing.setEnabled(false);
        existing.setDeleted(1);
        existing.setRevokedAt(LocalDateTime.now());
        mapper.updateById(existing);
    }

    static boolean agentIdEquals(Long expected, Long actual) {
        return expected != null && expected.equals(actual);
    }

    String generatePlaintext() {
        byte[] bytes = new byte[KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void validateOverride(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new MateClawException("err.agent_api.invalid_limit", 400,
                    field + " must be greater than zero");
        }
    }

    public record CreatedKey(String plaintext, AgentApiKeyEntity entity) {
    }

    public record AgentApiPrincipal(Long keyId,
                                    Long publicationId,
                                    Long agentId,
                                    Long workspaceId,
                                    AgentApiKeyEntity key) {
    }
}
