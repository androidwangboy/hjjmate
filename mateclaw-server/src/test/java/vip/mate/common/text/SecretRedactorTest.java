package vip.mate.common.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for credential masking applied to text that routine mining copies into
 * a new table, an admin screen, and a model prompt.
 */
class SecretRedactorTest {

    @Test
    @DisplayName("null and empty input pass through")
    void handlesEmpty() {
        assertEquals(null, SecretRedactor.redact(null));
        assertEquals("", SecretRedactor.redact(""));
    }

    @Test
    @DisplayName("OpenAI-style keys are masked, including project keys")
    void masksOpenAiKeys() {
        String out = SecretRedactor.redact("use sk-proj-abcdef1234567890abcdef1234567890 now");
        assertFalse(out.contains("abcdef1234567890"), out);
        assertTrue(out.contains(SecretRedactor.MASK), out);
        assertTrue(out.startsWith("use ") && out.endsWith(" now"), out);
    }

    @Test
    @DisplayName("an assignment keeps the field name and masks only the value")
    void masksAssignmentValueOnly() {
        String out = SecretRedactor.redact("api_key = \"sk-proj-abcdef1234567890abcdef\"");
        assertTrue(out.contains("api_key"), "the field name is what makes the text readable: " + out);
        assertFalse(out.contains("abcdef"), out);
    }

    @Test
    @DisplayName("bearer headers, GitHub, Slack, Google and AWS keys are masked")
    void masksCommonProviderShapes() {
        for (String secret : new String[]{
                "Bearer eyJhbGciOiJIUzI1NiJ9.abcdefghijklmnop",
                "ghp_abcdefghijklmnopqrstuvwxyz012345",
                "xoxb-123456789012-abcdefghijklmno",
                "AIzaSyABCDEFGHIJKLMNOPQRSTUVWXYZ01234",
                "AKIAIOSFODNN7EXAMPLE",
        }) {
            String out = SecretRedactor.redact("prefix " + secret + " suffix");
            assertTrue(out.contains(SecretRedactor.MASK), "not masked: " + secret + " -> " + out);
        }
    }

    @Test
    @DisplayName("ordinary request text is left intact")
    void leavesNormalTextAlone() {
        String text = "帮我生成今天的运维日报，重点看错误率";
        assertEquals(text, SecretRedactor.redact(text));

        String english = "generate the weekly oncall digest for the team";
        assertEquals(english, SecretRedactor.redact(english));
    }

    @Test
    @DisplayName("words that merely mention a secret are not mangled")
    void doesNotOverMatchProse() {
        // No assignment and no key shape — masking here would destroy the very
        // words that distinguish one routine from another.
        String text = "remind me to rotate the password next week";
        assertEquals(text, SecretRedactor.redact(text));
    }
}
