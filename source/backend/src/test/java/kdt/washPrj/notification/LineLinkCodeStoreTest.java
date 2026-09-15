package kdt.washPrj.notification;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class LineLinkCodeStoreTest {

    @Test
    void storesOnlyResolvableOneTimeCodeUntilInvalidated() {
        LineMessagingProperties properties = new LineMessagingProperties();
        properties.setLinkCodeTtlSeconds(600);
        SecureRandom random = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] generated = invocation.getArgument(0);
            Arrays.fill(generated, (byte) 7);
            return null;
        }).when(random).nextBytes(org.mockito.ArgumentMatchers.any(byte[].class));
        LineLinkCodeStore store = new LineLinkCodeStore(
                properties,
                random,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
        );

        LineLinkCodeStore.IssuedCode issued = store.issue("customer1");

        assertEquals("customer1", store.resolve(issued.code()));
        assertEquals(600, issued.expiresInSeconds());
        store.invalidate(issued.code());
        assertNull(store.resolve(issued.code()));
    }
}
