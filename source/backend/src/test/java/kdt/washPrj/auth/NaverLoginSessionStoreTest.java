package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.NaverProfileDTO;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NaverLoginSessionStoreTest {

    @Test
    void stateCanBeConsumedOnlyOnce() {
        NaverLoginSessionStore store = store(new MutableClock());
        String state = store.issueState();

        store.consumeState(state);

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                () -> store.consumeState(state)
        );
        assertEquals("NAVER_STATE_INVALID", exception.getCode());
    }

    @Test
    void expiredStateIsRejected() {
        MutableClock clock = new MutableClock();
        NaverLoginSessionStore store = store(clock);
        String state = store.issueState();

        clock.advanceSeconds(301);

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                () -> store.consumeState(state)
        );
        assertEquals("NAVER_STATE_INVALID", exception.getCode());
    }

    @Test
    void registrationTokenIsOneTimeAndReturnsStoredProfile() {
        NaverLoginSessionStore store = store(new MutableClock());
        NaverProfileDTO profile = profile();
        String token = store.issueRegistration(profile);

        assertEquals(profile, store.consumeRegistration(token));

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                () -> store.consumeRegistration(token)
        );
        assertEquals("NAVER_REGISTRATION_EXPIRED", exception.getCode());
    }

    @Test
    void generatedTokensAreNotReused() {
        NaverLoginSessionStore store = store(new MutableClock());

        assertNotEquals(store.issueState(), store.issueState());
        assertNotEquals(
                store.issueRegistration(profile()),
                store.issueRegistration(profile())
        );
    }

    private NaverLoginSessionStore store(MutableClock clock) {
        return new NaverLoginSessionStore(300, 600, new SecureRandom(), clock);
    }

    private NaverProfileDTO profile() {
        return new NaverProfileDTO(
                "naver-user-1",
                "김민준",
                "minjun@example.com",
                "01012345678",
                "M",
                LocalDate.of(1995, 3, 12)
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-08-10T00:00:00Z");

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
