package vip.mate.llm.probe;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in context-window table for hosted models, keyed by lowercase
 * model-name prefix; longest match wins.
 *
 * <p>Why this exists: {@code mate_model_config.max_input_tokens} ships as 0 for
 * every catalog row and cloud endpoints are deliberately never probed, so
 * without this table every hosted model — a 1M-window one included — budgets
 * and reports against the 128k global default. That both compacts history far
 * too early on large-window models and hides the real window in the chat
 * context-usage chip.
 *
 * <p>Values are <b>input</b> windows in tokens (not max output). Entries are
 * limited to models whose window is documented by the vendor or by this
 * repository's own model catalog; a family that is not listed simply falls
 * through to the caller's global default, which is the pre-existing behavior.
 * Per-model {@code maxInputTokens} in the database always overrides this table,
 * so an operator can correct any entry without a code change.
 *
 * <p>Names carrying a vendor segment ({@code google/gemini-2.5-pro},
 * {@code Pro/deepseek-ai/DeepSeek-V3}) are retried against the segment after
 * the last slash, so aggregator providers reuse the same entries.
 */
final class ModelContextWindowCatalog {

    private static final Map<String, Integer> WINDOWS;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();

        // ===== DeepSeek =====
        // V4 ships a 1M window; the V3 line and the chat/reasoner aliases are 128k.
        m.put("deepseek-v4", 1_000_000);
        m.put("deepseek-v3", 128_000);
        m.put("deepseek-r1", 128_000);
        m.put("deepseek-chat", 128_000);
        m.put("deepseek-reasoner", 128_000);

        // ===== Anthropic Claude =====
        // 200k across the line; the 1M variants are opt-in per request, so the
        // conservative default is the one that always holds.
        m.put("claude-", 200_000);

        // ===== Google Gemini =====
        m.put("gemini-2.0", 1_048_576);
        m.put("gemini-2.5", 1_048_576);
        m.put("gemini-3", 1_048_576);

        // ===== OpenAI =====
        // gpt-5's 400k total budget splits into 272k input + 128k output.
        m.put("gpt-5", 272_000);
        m.put("gpt-4.1", 1_047_576);
        m.put("gpt-4o", 128_000);
        m.put("o3", 200_000);
        m.put("o4-mini", 200_000);

        // ===== Alibaba Qwen =====
        m.put("qwen3-max", 262_144);
        m.put("qwen-long", 10_000_000);

        // ===== Moonshot Kimi =====
        m.put("kimi-k2", 262_144);

        // ===== Zhipu GLM =====
        m.put("glm-4.7", 204_800);
        m.put("glm-4-7", 204_800);
        m.put("glm-5.2", 1_000_000);

        // ===== Volcengine Doubao / Ark =====
        m.put("doubao-seed-1-8", 262_144);
        m.put("doubao-seed-code", 262_144);
        m.put("ark-code-latest", 262_144);

        // ===== xAI Grok =====
        m.put("grok-3", 131_072);
        m.put("grok-4", 256_000);

        // ===== Meta Llama =====
        m.put("llama-4-maverick", 1_048_576);

        WINDOWS = Map.copyOf(m);
    }

    private ModelContextWindowCatalog() {
    }

    /**
     * @return the known input window for {@code modelName}, or {@code null}
     *         when the model is not in the table
     */
    static Integer lookup(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        String lowered = modelName.trim().toLowerCase();
        Integer direct = matchPrefix(lowered);
        if (direct != null) {
            return direct;
        }
        int slash = lowered.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < lowered.length()) {
            return matchPrefix(lowered.substring(slash + 1));
        }
        return null;
    }

    private static Integer matchPrefix(String loweredName) {
        return WINDOWS.entrySet().stream()
                .filter(e -> loweredName.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
