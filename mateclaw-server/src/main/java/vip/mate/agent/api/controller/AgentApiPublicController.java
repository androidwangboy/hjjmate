package vip.mate.agent.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.AsyncChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.RestChatResponse;
import vip.mate.agent.api.dto.AgentApiDtos.TaskView;
import vip.mate.agent.api.service.AgentApiException;
import vip.mate.agent.api.service.AgentApiExecutionService;
import vip.mate.agent.api.service.AgentApiRequestAuthenticator;
import vip.mate.agent.api.service.AgentApiTaskService;
import vip.mate.channel.web.Utf8SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public REST contract for a published expert. The API key fixes the expert. */
@RestController
@RequestMapping("/api/v1/open/agent")
@RequiredArgsConstructor
public class AgentApiPublicController {

    private final AgentApiRequestAuthenticator authenticator;
    private final AgentApiExecutionService executionService;
    private final AgentApiTaskService taskService;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping("/chat")
    public RestChatResponse chat(HttpServletRequest httpRequest,
                                 @RequestBody ChatRequest request) {
        AgentApiRequestAuthenticator.Context context = authenticator.authenticate(httpRequest);
        try {
            AgentApiExecutionService.ExecutionResult result = executionService.execute(
                    context.principal(), context.publication(), request, "rest");
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", result.promptTokens());
            usage.put("completionTokens", result.completionTokens());
            usage.put("totalTokens", result.promptTokens() + result.completionTokens());
            if (result.runtimeModel() != null) usage.put("model", result.runtimeModel());
            return new RestChatResponse(
                    result.requestId(), result.conversationId(), result.endUserId(),
                    result.content(), "completed", usage);
        } catch (IllegalArgumentException e) {
            throw AgentApiException.invalid(e.getMessage());
        } catch (AgentApiException e) {
            throw e;
        } catch (Exception e) {
            throw AgentApiException.upstream(e.getMessage() == null ? "expert execution failed" : e.getMessage());
        }
    }

    @PostMapping("/tasks")
    public TaskView submitTask(HttpServletRequest httpRequest,
                               @RequestBody AsyncChatRequest request) {
        AgentApiRequestAuthenticator.Context context = authenticator.authenticate(httpRequest);
        try {
            return taskService.submit(context.principal(), context.publication(), request);
        } catch (IllegalArgumentException e) {
            throw AgentApiException.invalid(e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}")
    public TaskView getTask(HttpServletRequest httpRequest,
                            @PathVariable String taskId) {
        AgentApiRequestAuthenticator.Context context = authenticator.authenticate(httpRequest);
        return taskService.getForKey(taskId, context.principal().keyId(), context.principal().agentId());
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public TaskView cancelTask(HttpServletRequest httpRequest,
                               @PathVariable String taskId) {
        AgentApiRequestAuthenticator.Context context = authenticator.authenticate(httpRequest);
        return taskService.cancel(taskId, context.principal().keyId(), context.principal().agentId());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest httpRequest,
                             @RequestBody ChatRequest request) {
        AgentApiRequestAuthenticator.Context context = authenticator.authenticate(httpRequest);
        SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        streamExecutor.execute(() -> {
            try {
                executionService.stream(context.principal(), context.publication(), request, "rest")
                        .subscribe(
                                delta -> sendDelta(emitter, delta),
                                error -> finishWithError(emitter, error),
                                () -> finish(emitter));
            } catch (Exception e) {
                finishWithError(emitter, e);
            }
        });
        return emitter;
    }

    private void sendDelta(SseEmitter emitter, AgentService.StreamDelta delta) {
        try {
            if (delta.isEvent()) {
                emitter.send(SseEmitter.event()
                        .name("event")
                        .data(Map.of(
                                "type", delta.eventType(),
                                "data", delta.eventData() == null ? Map.of() : delta.eventData()),
                                MediaType.APPLICATION_JSON));
            } else if (delta.content() != null) {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(Map.of("delta", delta.content()), MediaType.APPLICATION_JSON));
            } else if (delta.thinking() != null) {
                emitter.send(SseEmitter.event()
                        .name("thinking")
                        .data(Map.of("delta", delta.thinking()), MediaType.APPLICATION_JSON));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void finish(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done")
                    .data(Map.of("status", "completed"), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void finishWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("message", error.getMessage() == null ? "expert execution failed" : error.getMessage()),
                            MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            // The client may already have disconnected.
        }
        emitter.completeWithError(error);
    }
}
