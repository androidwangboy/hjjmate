package vip.mate.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.support.RetryTemplate;
import vip.mate.agent.model.AgentEntity;
import vip.mate.llm.chatmodel.ProviderChatModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the prompt-optimization one-shot call: persona injection into the
 * system prompt, input validation/truncation, output cleanup (markdown
 * fences) and the failure paths the controller maps to 4xx/503.
 */
@ExtendWith(MockitoExtension.class)
class PromptOptimizeServiceTest {

    @Mock private ModelConfigService modelConfigService;
    @Mock private ProviderChatModelFactory chatModelFactory;
    @Mock private ChatModel chatModel;

    private PromptOptimizeService svc;

    @BeforeEach
    void setUp() {
        svc = new PromptOptimizeService(modelConfigService, chatModelFactory);
    }

    private ModelConfigEntity model(String name) {
        ModelConfigEntity m = new ModelConfigEntity();
        m.setProvider("dashscope");
        m.setModelName(name);
        return m;
    }

    private void stubChatResponse(String body) {
        when(modelConfigService.getDefaultModel()).thenReturn(model("qwen-turbo"));
        when(chatModelFactory.buildFor(any(ModelConfigEntity.class), any(RetryTemplate.class)))
                .thenReturn(chatModel);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private Prompt capturedPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue();
    }

    private static String textOf(Prompt prompt, MessageType type) {
        return prompt.getInstructions().stream()
                .map(m -> (Message) m)
                .filter(m -> m.getMessageType() == type)
                .map(Message::getText)
                .findFirst()
                .orElseThrow();
    }

    private static AgentEntity agent(String name, String systemPrompt) {
        AgentEntity a = new AgentEntity();
        a.setName(name);
        a.setSystemPrompt(systemPrompt);
        return a;
    }

    @Test
    void optimizeWithoutAgentReturnsCleanedText() {
        stubChatResponse("  请总结这份周报的亮点，输出 3 条要点。  ");

        String out = svc.optimize("帮我看看周报亮点", null);

        assertEquals("请总结这份周报的亮点，输出 3 条要点。", out);
        Prompt prompt = capturedPrompt();
        assertEquals(PromptOptimizeService.SYSTEM_PROMPT, textOf(prompt, MessageType.SYSTEM));
        assertEquals("帮我看看周报亮点", textOf(prompt, MessageType.USER));
    }

    @Test
    void optimizeInjectsAgentPersonaIntoSystemPrompt() {
        stubChatResponse("优化后的文本");

        svc.optimize("草稿", agent("法务助理", "你是公司法务，擅长合同审查。"));

        String system = textOf(capturedPrompt(), MessageType.SYSTEM);
        assertTrue(system.contains("法务助理"));
        assertTrue(system.contains("你是公司法务，擅长合同审查。"));
        assertTrue(system.startsWith(PromptOptimizeService.SYSTEM_PROMPT));
    }

    @Test
    void personaIsTruncatedToCap() {
        stubChatResponse("优化后的文本");
        String longPersona = "x".repeat(PromptOptimizeService.MAX_PERSONA_CHARS + 500);

        svc.optimize("草稿", agent("助理", longPersona));

        String system = textOf(capturedPrompt(), MessageType.SYSTEM);
        assertFalse(system.contains("x".repeat(PromptOptimizeService.MAX_PERSONA_CHARS + 500)));
        assertTrue(system.contains("…[截断]"));
    }

    @Test
    void agentWithBlankPersonaStillAddsName() {
        stubChatResponse("优化后的文本");

        svc.optimize("草稿", agent("翻译官", "  "));

        String system = textOf(capturedPrompt(), MessageType.SYSTEM);
        assertTrue(system.contains("翻译官"));
        assertFalse(system.contains("专家设定"));
    }

    @Test
    void longInputIsTruncatedBeforeCallingModel() {
        stubChatResponse("优化后的文本");
        String oversized = "长".repeat(PromptOptimizeService.MAX_INPUT_CHARS + 2_000);

        svc.optimize(oversized, null);

        assertEquals(PromptOptimizeService.MAX_INPUT_CHARS,
                textOf(capturedPrompt(), MessageType.USER).length());
    }

    @Test
    void blankInputIsRejectedWithoutModelCall() {
        assertThrows(IllegalArgumentException.class, () -> svc.optimize("   ", null));
        assertThrows(IllegalArgumentException.class, () -> svc.optimize(null, null));
    }

    @Test
    void missingDefaultModelThrowsIllegalState() {
        when(modelConfigService.getDefaultModel()).thenReturn(null);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.optimize("草稿", null));
        assertTrue(e.getMessage().contains("No default chat model"));
    }

    @Test
    void emptyModelReplyThrowsIllegalState() {
        stubChatResponse("   ");

        assertThrows(IllegalStateException.class, () -> svc.optimize("草稿", null));
    }

    @Test
    void providerFailureIsWrappedInIllegalState() {
        when(modelConfigService.getDefaultModel()).thenReturn(model("qwen-turbo"));
        when(chatModelFactory.buildFor(any(ModelConfigEntity.class), any(RetryTemplate.class)))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream 500"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.optimize("草稿", null));
        assertTrue(e.getMessage().contains("upstream 500"));
    }

    @Test
    void stripFencesRemovesMarkdownCodeBlocks() {
        stubChatResponse("```markdown\n请列出三个方案。\n```");

        assertEquals("请列出三个方案。", svc.optimize("给我方案", null));
    }

    @Test
    void stripFencesHandlesBareFenceWithoutLanguage() {
        assertEquals("正文", PromptOptimizeService.stripFences("```\n正文\n```"));
        assertEquals("正文", PromptOptimizeService.stripFences("正文"));
    }
}
