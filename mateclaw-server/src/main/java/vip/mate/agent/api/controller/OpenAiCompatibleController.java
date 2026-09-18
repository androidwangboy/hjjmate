package vip.mate.agent.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.agent.AgentService;
import vip.mate.agent.api.dto.AgentApiDtos.ChatRequest;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiChoice;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiMessage;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiRequest;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiResponse;
import vip.mate.agent.api.dto.AgentApiDtos.OpenAiUsage;
import vip.mate.agent.api.service.AgentApiException;
import vip.mate.agent.api.service.AgentApiExecutionService;
import vip.mate.agent.api.service.AgentApiRequestAuthenticator;
import vip.mate.channel.web.Utf8SseEmitter;
import vip.mate.workspace.conversation.model.MessageContentPart;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal OpenAI Chat Completions compatibility adapter. */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAiCompatibleController {

    private final AgentApiRequestAuthenticator authenticator;
    private final AgentApiExecutionService executionService;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping(value = "/chat/completions", produces = {
            MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chatCompletions(HttpServletRequest httpRequest,
                                  @RequestBody OpenAiRequest request) {
        if (request == null || !"expert".equals(request.model())) {
            throw AgentApiException.invalid("model must be expert");
        }
        ChatRequest chatRequest;
        try {
            chatRequest = toChatRequest(request);
        } catch (IllegalArgumentException e) {
            throw AgentApiException.invalid(e.getMessage());
        }

        AgentApiRequestAuthenticator.Context context = authenticator.authenticate(httpRequest);
        if (Boolean.TRUE.equals(request.stream())) {
            return stream(context, chatRequest);
        }
        try {
            AgentApiExecutionService.ExecutionResult result = executionService.execute(
                    context.principal(), context.publication(), chatRequest, "openai");
            return new OpenAiResponse(
                    "chatcmpl_" + result.requestId(),
                    "chat.completion",
                    Instant.now().getEpochSecond(),
                    "expert",
                    List.of(new OpenAiChoice(
                            0,
                            new OpenAiMessage("assistant", result.content()),
                            "stop")),
                    new OpenAiUsage(
                            result.promptTokens(),
                            result.completionTokens(),
                            result.promptTokens() + result.completionTokens()));
        } catch (IllegalArgumentException e) {
            throw AgentApiException.invalid(e.getMessage());
        } catch (AgentApiException e) {
            throw e;
        } catch (Exception e) {
            throw AgentApiException.upstream(e.getMessage() == null ? "expert execution failed" : e.getMessage());
        }
    }

    private SseEmitter stream(AgentApiRequestAuthenticator.Context context, ChatRequest request) {
        SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        String responseId = "chatcmpl_" + UUID.randomUUID();
        streamExecutor.execute(() -> {
            try {
                executionService.stream(context.principal(), context.publication(), request, "openai")
                        .subscribe(
                                delta -> sendChunk(emitter, responseId, delta),
                                error -> finishWithError(emitter, error),
                                () -> finish(emitter, responseId));
            } catch (Exception e) {
                finishWithError(emitter, e);
            }
        });
        return emitter;
    }

    private void sendChunk(SseEmitter emitter, String responseId, AgentService.StreamDelta delta) {
        if (delta.content() == null || delta.content().isEmpty()) return;
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", Map.of("role", "assistant", "content", delta.content()));
        choice.put("finish_reason", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", responseId);
        body.put("object", "chat.completion.chunk");
        body.put("created", Instant.now().getEpochSecond());
        body.put("model", "expert");
        body.put("choices", List.of(choice));
        sendData(emitter, body);
    }

    private void finish(SseEmitter emitter, String responseId) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", Map.of());
        choice.put("finish_reason", "stop");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", responseId);
        body.put("object", "chat.completion.chunk");
        body.put("created", Instant.now().getEpochSecond());
        body.put("model", "expert");
        body.put("choices", List.of(choice));
        try {
            emitter.send(SseEmitter.event().data(body));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void finishWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.send(SseEmitter.event().data(Map.of(
                    "error", Map.of("message", error.getMessage() == null ? "expert execution failed" : error.getMessage()))));
        } catch (IOException ignored) {
            // Client may already have disconnected.
        }
        emitter.completeWithError(error);
    }

    private void sendData(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private static ChatRequest toChatRequest(OpenAiRequest request) {
        if (!StringUtils.hasText(request.user())) {
            throw new IllegalArgumentException("user is required");
        }
        if (!StringUtils.hasText(request.conversationId())) {
            throw new IllegalArgumentException("conversation_id is required");
        }
        if (request.messages() == null || !request.messages().isArray()) {
            throw new IllegalArgumentException("messages must be an array");
        }

        String text = "";
        List<MessageContentPart> parts = new ArrayList<>();
        for (JsonNode message : request.messages()) {
            if (!"user".equals(message.path("role").asText())) continue;
            ParsedContent parsed = parseContent(message.get("content"));
            if (!parsed.text().isBlank()) text = parsed.text();
            if (!parsed.parts().isEmpty()) parts = parsed.parts();
        }
        if (text.isBlank() && parts.isEmpty()) {
            throw new IllegalArgumentException("a user message is required");
        }
        return new ChatRequest(request.conversationId(), request.user(), text, parts);
    }

    private static ParsedContent parseContent(JsonNode content) {
        if (content == null || content.isNull()) return new ParsedContent("", List.of());
        if (content.isTextual()) return new ParsedContent(content.asText(), List.of());
        if (!content.isArray()) throw new IllegalArgumentException("message content must be text or an array");

        StringBuilder text = new StringBuilder();
        List<MessageContentPart> parts = new ArrayList<>();
        for (JsonNode item : content) {
            String type = item.path("type").asText();
            if ("text".equals(type)) {
                String value = item.path("text").asText("");
                if (!value.isBlank()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(value);
                    parts.add(MessageContentPart.text(value));
                }
            } else if ("image_url".equals(type)) {
                String url = item.path("image_url").path("url").asText("");
                parts.add(MessageContentPart.image(null, url));
            } else {
                throw new IllegalArgumentException("unsupported content part: " + type);
            }
        }
        return new ParsedContent(text.toString(), parts);
    }

    private record ParsedContent(String text, List<MessageContentPart> parts) {
    }
}
