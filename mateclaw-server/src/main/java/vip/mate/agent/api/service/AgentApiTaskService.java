package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.api.dto.AgentApiDtos.AsyncChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.TaskView;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.api.model.AgentApiTaskEntity;
import vip.mate.agent.api.repository.AgentApiTaskMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persists and executes long-running external expert requests. */
@Service
@RequiredArgsConstructor
public class AgentApiTaskService {

    private final AgentApiTaskMapper mapper;
    private final AgentApiExecutionService executionService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    public TaskView submit(AgentApiKeyService.AgentApiPrincipal principal,
                           AgentApiPublicationEntity publication,
                           AsyncChatRequest request) {
        AgentApiExecutionService.validateRequest(new ChatRequest(
                request == null ? null : request.conversationId(),
                request == null ? null : request.endUserId(),
                request == null ? null : request.message(),
                request == null ? null : request.contentParts()));
        validateCallbackUrl(request == null ? null : request.callbackUrl());

        String taskId = "task_" + UUID.randomUUID();
        AgentApiTaskEntity task = new AgentApiTaskEntity();
        task.setTaskId(taskId);
        task.setRequestId("req_" + UUID.randomUUID());
        task.setPublicationId(principal.publicationId());
        task.setApiKeyId(principal.keyId());
        task.setAgentId(principal.agentId());
        task.setWorkspaceId(principal.workspaceId());
        task.setConversationId(request.conversationId());
        task.setEndUserId(request.endUserId());
        task.setStatus("queued");
        task.setCallbackUrl(request.callbackUrl());
        task.setCallbackStatus("not_sent");
        task.setDeleted(0);
        task.setExpiresAt(LocalDateTime.now().plusHours(24));
        mapper.insert(task);

        executor.execute(() -> run(task, principal, publication, request));
        return toView(task);
    }

    public TaskView getForKey(String taskId, Long keyId, Long agentId) {
        AgentApiTaskEntity task = mapper.selectOne(new LambdaQueryWrapper<AgentApiTaskEntity>()
                .eq(AgentApiTaskEntity::getTaskId, taskId)
                .eq(AgentApiTaskEntity::getApiKeyId, keyId)
                .eq(AgentApiTaskEntity::getAgentId, agentId)
                .eq(AgentApiTaskEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (task == null) {
            throw new AgentApiException(404, "agent_api_task_not_found", "Task not found");
        }
        return toView(task);
    }

    public TaskView cancel(String taskId, Long keyId, Long agentId) {
        AgentApiTaskEntity task = mapper.selectOne(new LambdaQueryWrapper<AgentApiTaskEntity>()
                .eq(AgentApiTaskEntity::getTaskId, taskId)
                .eq(AgentApiTaskEntity::getApiKeyId, keyId)
                .eq(AgentApiTaskEntity::getAgentId, agentId)
                .eq(AgentApiTaskEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (task == null) {
            throw new AgentApiException(404, "agent_api_task_not_found", "Task not found");
        }
        if (!isTerminal(task.getStatus())) {
            task.setStatus("canceled");
            task.setCompletedAt(LocalDateTime.now());
            mapper.updateById(task);
        }
        return toView(task);
    }

    private void run(AgentApiTaskEntity task,
                     AgentApiKeyService.AgentApiPrincipal principal,
                     AgentApiPublicationEntity publication,
                     AsyncChatRequest request) {
        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        mapper.updateById(task);
        try {
            AgentApiExecutionService.ExecutionResult result = executionService.execute(
                    principal,
                    publication,
                    new ChatRequest(request.conversationId(), request.endUserId(),
                            request.message(), request.contentParts()),
                    "rest-async");
            AgentApiTaskEntity current = findRaw(task.getTaskId());
            if (current != null && "canceled".equals(current.getStatus())) return;
            task.setStatus("completed");
            task.setResultText(result.content());
            task.setCompletedAt(LocalDateTime.now());
            mapper.updateById(task);
            sendCallback(task);
        } catch (Exception e) {
            task.setStatus("failed");
            task.setErrorMessage(e.getMessage() == null ? "expert execution failed" : e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            mapper.updateById(task);
            sendCallback(task);
        }
    }

    private void sendCallback(AgentApiTaskEntity task) {
        if (!StringUtils.hasText(task.getCallbackUrl())) return;
        try {
            String body = callbackBody(task);
            HttpRequest request = HttpRequest.newBuilder(URI.create(task.getCallbackUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            task.setCallbackStatus(response.statusCode() >= 200 && response.statusCode() < 300
                    ? "sent" : "failed");
        } catch (Exception e) {
            task.setCallbackStatus("failed");
        }
        mapper.updateById(task);
    }

    private String callbackBody(AgentApiTaskEntity task) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("taskId", task.getTaskId());
        body.put("status", task.getStatus());
        body.put("result", task.getResultText());
        body.put("error", task.getErrorMessage());
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        return mapper.writeValueAsString(body);
    }

    private AgentApiTaskEntity findRaw(String taskId) {
        return mapper.selectOne(new LambdaQueryWrapper<AgentApiTaskEntity>()
                .eq(AgentApiTaskEntity::getTaskId, taskId)
                .eq(AgentApiTaskEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private static void validateCallbackUrl(String callbackUrl) {
        if (!StringUtils.hasText(callbackUrl)) return;
        URI uri;
        try {
            uri = URI.create(callbackUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("callbackUrl is invalid", e);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()));
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) {
            throw new IllegalArgumentException("callbackUrl must use HTTPS");
        }
    }

    private static boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "canceled".equals(status);
    }

    private static TaskView toView(AgentApiTaskEntity task) {
        return new TaskView(task.getTaskId(), task.getStatus(), task.getResultText(),
                task.getErrorMessage(), task.getCreateTime(), task.getStartedAt(), task.getCompletedAt());
    }
}
