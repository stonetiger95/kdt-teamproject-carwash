package kdt.washPrj.chatbot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatbotRateLimiterTest {

    @Test
    void rejectsRequestsAboveConfiguredWindowLimit() {
        ChatbotRateLimiter limiter = new ChatbotRateLimiter(
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
                2,
                60
        );

        assertTrue(limiter.allow("customer"));
        assertTrue(limiter.allow("customer"));
        assertFalse(limiter.allow("customer"));
    }
}
