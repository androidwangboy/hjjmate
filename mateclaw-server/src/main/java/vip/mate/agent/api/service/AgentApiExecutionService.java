package vip.mate.agent.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import org.springframework.util.StringUtils;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.model.MessageContentPart;
import vip.mate.workspace.conversation.model.MessageEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Converts public API requests into the existing expert runtime contract. */
@Service
@RequiredArgsConstructor
public class AgentApiExecutionService {

    private final AgentService agentService;
    private final ConversationService conversationService;
    private final AgentApiPublicationService publicationService;
    private final AgentApiRateLimiter rateLimiter;
    private final AgentApiKeyService keyService;

    @Autowired(required = false)
    private AgentApiLogService logService;

    public ExecutionResult execute(AgentApiKeyService.AgentApiPrincipal principal,
                                   AgentApiPublicationEntity publication,
                                   ChatRequest request,
                                   String protocol) {
        return executeInternal(principal, publication, request, protocol, true);
    }

    public Flux<AgentService.StreamDelta> stream(AgentApiKeyService.AgentApiPrincipal principal,
                                                   AgentApiPublicationEntity publication,
                                                   ChatRequest request,
                                                   String protocol) {
        validateRequest(request);
        publicationService.requirePublished(publication);
        AgentEntity agent = agentService.getAgent(principal.agentId());
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw new MateClawException("err.agent.disabled", 403,
                    "Agent 已禁用: " + agent.getName());
        }

        AgentApiRateLimiter.Permit permit = rateLimiter.acquire(principal, publication);
        String requestId = "req_" + UUID.randomUUID();
        long started = System.nanoTime();
        try {
            keyService.recordUse(principal.key());
            String internalConversationId = internalConversationId(
                    principal.agentId(), request.endUserId(), request.conversationId());
            String ownerKey = "api:" + shortHash(request.endUserId());
            List<MessageContentPart> parts = request.contentParts() == null
                    ? List.of() : request.contentParts();
            String prompt = buildPromptText(request.message(), parts);
            conversationService.getOrCreateConversation(
                    internalConversationId, principal.agentId(), ownerKey, principal.workspaceId());
            MessageEntity userMessage = conversationService.saveMessage(
                    internalConversationId, "user", prompt, parts);
            ChatOrigin origin = ChatOrigin.web(
                            internalConversationId, request.endUserId(), principal.workspaceId(), null)
                    .withAgent(principal.agentId())
                    .withSender(null, "api", null)
                    .withOriginMessageId(userMessage == null ? null : userMessage.getId());

            StringBuilder content = new StringBuilder();
            final int[] promptTokens = {0};
            final int[] completionTokens = {0};
            return Flux.defer(() -> agentService.chatStructuredStream(
                            principal.agentId(), prompt, internalConversationId, "", null, origin))
                    .doOnNext(delta -> {
                        if (delta.content() != null) content.append(delta.content());
                        if (delta.isEvent() && "_usage_final".equals(delta.eventType())
                                && delta.eventData() != null) {
                            Object p = delta.eventData().get("promptTokens");
                            Object c = delta.eventData().get("completionTokens");
                            if (p instanceof Number n) promptTokens[0] = n.intValue();
                            if (c instanceof Number n) completionTokens[0] = n.intValue();
                        }
                    })
                    .doOnComplete(() -> {
                        conversationService.saveMessage(
                                internalConversationId, "assistant", content.toString(), null,
                                "completed", promptTokens[0], completionTokens[0], null, null);
                        if (logService != null) {
                            logService.record(principal, new ExecutionResult(
                                    requestId, request.conversationId(), request.endUserId(),
                                    content.toString(), promptTokens[0], completionTokens[0],
                                    null, null, (System.nanoTime() - started) / 1_000_000L,
                                    protocol, "stream"), 200, "completed", null, null);
                        }
                    })
                    .doOnError(error -> {
                        if (logService != null) {
                            recordFailure(principal, request, requestId, protocol, started, "stream",
                                    error instanceof RuntimeException runtime
                                            ? runtime : new RuntimeException(error));
                        }
                    })
                    .doFinally(signal -> permit.close());
        } catch (RuntimeException e) {
            permit.close();
            throw e;
        }
    }

    public ExecutionResult executeForManagement(Long agentId,
                                                Long workspaceId,
                                                ChatRequest request,
                                                String protocol) {
        AgentApiPublicationEntity publication = publicationService.getExisting(agentId, workspaceId);
        if (publication == null) {
            throw new AgentApiException(403, "agent_api_not_published", "Agent API is not published");
        }
        AgentApiKeyService.AgentApiPrincipal principal = new AgentApiKeyService.AgentApiPrincipal(
                null, publication.getId(), agentId, workspaceId, null);
        return executeInternal(principal, publication, request, protocol, false);
    }

    private ExecutionResult executeInternal(AgentApiKeyService.AgentApiPrincipal principal,
                                            AgentApiPublicationEntity publication,
                                            ChatRequest request,
                                            String protocol,
                                            boolean governed) {
        validateRequest(request);
        publicationService.requirePublished(publication);
        AgentEntity agent = agentService.getAgent(principal.agentId());
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw new MateClawException("err.agent.disabled", 403,
                    "Agent 已禁用: " + agent.getName());
        }

        AgentApiRateLimiter.Permit permit = governed
                ? rateLimiter.acquire(principal, publication) : null;
        long started = System.nanoTime();
        String requestId = "req_" + UUID.randomUUID();
        try {
            keyService.recordUse(principal.key());
            String internalConversationId = internalConversationId(
                    principal.agentId(), request.endUserId(), request.conversationId());
            String ownerKey = "api:" + shortHash(request.endUserId());
            List<MessageContentPart> parts = request.contentParts() == null
                    ? List.of() : request.contentParts();
            String prompt = buildPromptText(request.message(), parts);

            conversationService.getOrCreateConversation(
                    internalConversationId, principal.agentId(), ownerKey, principal.workspaceId());
            MessageEntity userMessage = conversationService.saveMessage(
                    internalConversationId, "user", prompt, parts);

            ChatOrigin origin = ChatOrigin.web(
                            internalConversationId,
                            request.endUserId(),
                            principal.workspaceId(),
                            null)
                    .withAgent(principal.agentId())
                    .withSender(null, "api", null)
                    .withOriginMessageId(userMessage == null ? null : userMessage.getId());

            AgentService.ChatResult result = agentService.chatWithUsage(
                    principal.agentId(), prompt, internalConversationId, origin);
            conversationService.saveMessage(
                    internalConversationId,
                    "assistant",
                    result.content(),
                    null,
                    "completed",
                    result.promptTokens(),
                    result.completionTokens(),
                    result.runtimeModel(),
                    result.runtimeProvider());

            ExecutionResult output = new ExecutionResult(
                    requestId,
                    request.conversationId(),
                    request.endUserId(),
                    result.content(),
                    result.promptTokens(),
                    result.completionTokens(),
                    result.runtimeModel(),
                    result.runtimeProvider(),
                    (System.nanoTime() - started) / 1_000_000L,
                    protocol,
                    "sync");
            if (governed && logService != null) {
                logService.record(principal, output, 200, "completed", null, null);
            }
            return output;
        } catch (RuntimeException e) {
            if (governed && logService != null) {
                recordFailure(principal, request, requestId, protocol, started, "sync", e);
            }
            throw e;
        } finally {
            if (permit != null) permit.close();
        }
    }

    private void recordFailure(AgentApiKeyService.AgentApiPrincipal principal,
                               ChatRequest request,
                               String requestId,
                               String protocol,
                               long started,
                               String mode,
                               RuntimeException error) {
        int status = error instanceof AgentApiException api ? api.getHttpStatus() : 502;
        String code = error instanceof AgentApiException api
                ? api.getErrorCode()
                : error.getClass().getSimpleName();
        try {
            logService.record(principal, new ExecutionResult(
                    requestId,
                    request.conversationId(),
                    request.endUserId(),
                    "",
                    0,
                    0,
                    null,
                    null,
                    (System.nanoTime() - started) / 1_000_000L,
                    protocol,
                    mode), status, "failed", code, null);
        } catch (Exception ignored) {
            // A logging outage must not change the API response.
        }
    }

    public static void validateRequest(ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.conversationId())) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (!StringUtils.hasText(request.endUserId())) {
            throw new IllegalArgumentException("endUserId is required");
        }
        if (!StringUtils.hasText(request.message())
                && (request.contentParts() == null || request.contentParts().isEmpty())) {
            throw new IllegalArgumentException("message or contentParts is required");
        }
        if (request.conversationId().length() > 128) {
            throw new IllegalArgumentException("conversationId is too long");
        }
        if (request.endUserId().length() > 128) {
            throw new IllegalArgumentException("endUserId is too long");
        }
    }

    public static String internalConversationId(Long agentId, String endUserId, String conversationId) {
        return "api:" + agentId + ":" + shortHash(endUserId + "\u0000" + conversationId);
    }

    private static String buildPromptText(String message, List<MessageContentPart> parts) {
        if (parts == null || parts.isEmpty()) return message == null ? "" : message;
        StringBuilder out = new StringBuilder();
        for (MessageContentPart part : parts) {
            if (part == null) continue;
            String line = switch (part.getType() == null ? "" : part.getType()) {
                case "text", "thinking" -> part.getText();
                case "file" -> "附件: " + safe(part.getFileName()) + " (" + safe(part.getPath()) + ")";
                case "image" -> "图片附件: " + safe(part.getFileName()) + " (" + safe(part.getPath()) + ")";
                case "video" -> "视频附件: " + safe(part.getFileName()) + " (" + safe(part.getPath()) + ")";
                default -> part.getText();
            };
            if (StringUtils.hasText(line)) {
                if (!out.isEmpty()) out.append('\n');
                out.append(line);
            }
        }
        if (out.isEmpty() && StringUtils.hasText(message)) return message;
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record ExecutionResult(
            String requestId,
            String conversationId,
            String endUserId,
            String content,
            int promptTokens,
            int completionTokens,
            String runtimeModel,
            String runtimeProvider,
            long latencyMs,
            String protocol,
            String mode) {
    }
}
