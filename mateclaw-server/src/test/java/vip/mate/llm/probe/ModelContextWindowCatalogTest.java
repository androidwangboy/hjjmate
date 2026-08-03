package vip.mate.llm.probe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ModelContextWindowCatalog} — prefix matching, vendor
 * segments, and the "unknown stays unknown" contract.
 */
class ModelContextWindowCatalogTest {

    @Test
    @DisplayName("longest prefix wins — v4 does not inherit the v3 window")
    void longestPrefixWins() {
        assertEquals(1_000_000, ModelContextWindowCatalog.lookup("deepseek-v4-flash"));
        assertEquals(1_000_000, ModelContextWindowCatalog.lookup("deepseek-v4-pro"));
        assertEquals(128_000, ModelContextWindowCatalog.lookup("deepseek-v3-2-251201"));
        assertEquals(128_000, ModelContextWindowCatalog.lookup("deepseek-chat"));
    }

    @Test
    @DisplayName("matching is case-insensitive and ignores the vendor segment")
    void vendorSegmentIsStripped() {
        assertEquals(200_000, ModelContextWindowCatalog.lookup("anthropic/claude-opus-4-8"));
        assertEquals(1_048_576, ModelContextWindowCatalog.lookup("google/gemini-2.5-flash:free"));
        assertEquals(128_000, ModelContextWindowCatalog.lookup("Pro/deepseek-ai/DeepSeek-V3"));
        assertEquals(1_048_576, ModelContextWindowCatalog.lookup("meta-llama/llama-4-maverick"));
    }

    @Test
    @DisplayName("models outside the table return null so the caller keeps its default")
    void unknownModelsReturnNull() {
        assertNull(ModelContextWindowCatalog.lookup("acme-llm-1"));
        assertNull(ModelContextWindowCatalog.lookup("vendor/unknown-model"));
        assertNull(ModelContextWindowCatalog.lookup(""));
        assertNull(ModelContextWindowCatalog.lookup(null));
    }
}
