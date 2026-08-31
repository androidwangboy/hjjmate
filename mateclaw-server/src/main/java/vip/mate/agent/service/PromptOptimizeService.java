package vip.mate.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import vip.mate.agent.model.AgentEntity;
import vip.mate.llm.chatmodel.ProviderChatModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a chat draft into a clearer, better-structured prompt before the
 * user sends it ("提示词优化" button in the chat input box).
 *
 * <p>Design mirrors the one-shot LLM call pattern used by
 * {@link vip.mate.goal.service.GoalEvaluationService}: resolve the system
 * default chat model, build the model through
 * {@link ProviderChatModelFactory} with a single-attempt retry template,
 * call it synchronously and post-process the text. No persistence — the
 * user's draft is never stored server-side.
 *
 * <p>When the caller passes the current agent, the agent's persona
 * (name + system prompt, truncated) is injected into the system prompt so
 * the rewrite targets that expert's capabilities instead of being generic.
 *
 * <p>Failures surface as {@link IllegalStateException} so the controller can
 * map them to a clear error instead of silently returning the original text
 * (the UI distinguishes "optimized" from "untouched" states).
 */
@Slf4j
@Service
public class PromptOptimizeService {

    /** Hard cap on the user draft we are willing to feed the model. */
    static final int MAX_INPUT_CHARS = 8_000;
    /** Persona (agent system prompt) is truncated before injection. */
    static final int MAX_PERSONA_CHARS = 800;
    /** Output budget — rewrites are short text, never huge documents. */
    private static final int MAX_OUTPUT_TOKENS = 2_000;

    /** Skip-retry template — the controller has its own try/catch. */
    private static final RetryTemplate ONESHOT = RetryTemplate.builder().maxAttempts(1).build();

    static final String SYSTEM_PROMPT = """
            你是提示词优化助手。用户会给你一段他准备发送给 AI 专家（数字员工）的消息草稿，你的任务是把它改写成一条更清晰、更具体、更容易得到高质量回复的提示词。

            # 改写规则

            1. 完整保留用户的原始意图、立场与要求，不得增删实质性诉求，不得替用户做决定。
            2. 保持用户草稿所使用的语言（中文草稿输出中文，英文草稿输出英文）。
            3. 补全缺失的关键要素：任务目标、必要背景、输入材料、期望的输出格式/结构、约束条件。只补"显然被默认省略"的部分，不得编造用户没提到的事实或数据。
            4. 消除歧义与口语冗余，必要时用分点、小标题等结构组织，但保持与任务复杂度相称——简单问题不要过度包装。
            5. 如果提供了目标专家的人设信息，让改写贴合该专家的职责与能力（例如让措辞符合专家擅长的工作方式），但不得引入人设中不存在的承诺。

            # 输出格式

            只输出优化后的提示词正文本身。不要任何前言、解释、道歉、引号包裹、Markdown 代码块、"优化后："之类的标签。
            """;

    private final ModelConfigService modelConfigService;
    private final ProviderChatModelFactory chatModelFactory;

    public PromptOptimizeService(ModelConfigService modelConfigService,
                                 ProviderChatModelFactory chatModelFactory) {
        this.modelConfigService = modelConfigService;
        this.chatModelFactory = chatModelFactory;
    }

    /**
     * Optimize a user draft, optionally targeting a specific agent's persona.
     *
     * @param text  the user's draft message; must not be blank
     * @param agent the current chat agent, or {@code null} for a generic rewrite
     * @return the optimized prompt text
     * @throws IllegalArgumentException if the draft is blank
     * @throws IllegalStateException    if no default model is configured or the
     *                                  model call fails / returns nothing
     */
    public String optimize(String text, AgentEntity agent) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be empty");
        }
        String draft = text.length() > MAX_INPUT_CHARS
                ? text.substring(0, MAX_INPUT_CHARS)
                : text;

        ModelConfigEntity model = modelConfigService.getDefaultModel();
        if (model == null) {
            throw new IllegalStateException(
                    "No default chat model configured; cannot optimize prompt");
        }

        long start = System.currentTimeMillis();
        try {
            ChatModel chatModel = chatModelFactory.buildFor(model, ONESHOT);

            List<Message> messages = new ArrayList<>(2);
            messages.add(new SystemMessage(buildSystemPrompt(agent)));
            messages.add(new UserMessage(draft));

            ChatOptions options = ChatOptions.builder()
                    .temperature(0.3)
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .build();

            ChatResponse response = chatModel.call(new Prompt(messages, options));
            String body = extractText(response);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Prompt optimizer returned empty content");
            }
            String optimized = stripFences(body).strip();
            if (optimized.isEmpty()) {
                throw new IllegalStateException("Prompt optimizer returned empty content");
            }
            log.info("[PromptOptimize] optimized draft ({} -> {} chars) via model={} in {}ms",
                    draft.length(), optimized.length(), model.getModelName(),
                    System.currentTimeMillis() - start);
            return optimized;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Log the full cause chain — the controller only surfaces
            // getMessage(), so provider-side failures need the stack here.
            log.error("[PromptOptimize] chat call failed", e);
            throw new IllegalStateException(
                    "Prompt optimization failed: " + e.getMessage(), e);
        }
    }

    /** System prompt + optional persona block for the target agent. */
    String buildSystemPrompt(AgentEntity agent) {
        if (agent == null) {
            return SYSTEM_PROMPT;
        }
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT.length() + MAX_PERSONA_CHARS + 128);
        sb.append(SYSTEM_PROMPT).append("\n\n# 目标专家人设（改写时贴合该专家）\n");
        if (agent.getName() != null && !agent.getName().isBlank()) {
            sb.append("专家名称：").append(agent.getName().strip()).append('\n');
        }
        String persona = agent.getSystemPrompt();
        if (persona != null && !persona.isBlank()) {
            persona = persona.strip();
            if (persona.length() > MAX_PERSONA_CHARS) {
                persona = persona.substring(0, MAX_PERSONA_CHARS) + "…[截断]";
            }
            sb.append("专家设定：\n").append(persona).append('\n');
        }
        return sb.toString();
    }

    /** Strip ``` fences the model may add despite instructions. */
    static String stripFences(String body) {
        String t = body.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        var output = response.getResult().getOutput();
        String text = output.getText();
        if (text != null && !text.isBlank()) {
            return text;
        }
        // Fallback for reasoning models that emit everything as reasoningContent.
        var metadata = output.getMetadata();
        if (metadata != null) {
            Object rc = metadata.get("reasoningContent");
            if (rc instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return text;
    }
}
