package kdt.washPrj.auth.social;

import kdt.washPrj.auth.dto.SocialProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialLoginTokenCodecTest {

    private MutableClock clock;
    private SocialLoginTokenCodec codec;

    @BeforeEach
    void setUp() {
        byte[] masterKey = new byte[32];
        for (int index = 0; index < masterKey.length; index++) {
            masterKey[index] = (byte) (index + 1);
        }
        SocialLoginProperties properties = new SocialLoginProperties(
                Base64.getEncoder().encodeToString(masterKey),
                300,
                600
        );
        clock = new MutableClock(Instant.parse("2026-08-11T00:00:00Z"));
        codec = new SocialLoginTokenCodec(
                properties,
                clock,
                new SecureRandom()
        );
    }

    @Test
    void stateIsSignedBoundToProviderAndExpires() {
        SocialLoginTokenCodec.IssuedState state = codec.issueState(
                SocialProvider.GOOGLE
        );

        assertDoesNotThrow(() -> codec.validateState(
                SocialProvider.GOOGLE,
                state.token()
        ));
        assertThrows(SocialOAuthException.class, () -> codec.validateState(
                SocialProvider.KAKAO,
                state.token()
        ));

        clock.advance(Duration.ofSeconds(301));
        assertThrows(SocialOAuthException.class, () -> codec.validateState(
                SocialProvider.GOOGLE,
                state.token()
        ));
    }

    @Test
    void registrationProfileIsEncryptedBoundToProviderAndExpires() {
        SocialProfileDTO expected = new SocialProfileDTO(
                "google-sub-123",
                "황태호",
                "user@example.com",
                null,
                "N",
                LocalDate.of(2001, 1, 1)
        );
        String registrationToken = codec.issueRegistration(
                SocialProvider.GOOGLE,
                expected
        );

        assertEquals(
                expected,
                codec.readRegistration(SocialProvider.GOOGLE, registrationToken)
        );
        assertThrows(SocialOAuthException.class, () -> codec.readRegistration(
                SocialProvider.KAKAO,
                registrationToken
        ));

        clock.advance(Duration.ofSeconds(601));
        assertThrows(SocialOAuthException.class, () -> codec.readRegistration(
                SocialProvider.GOOGLE,
                registrationToken
        ));
    }

    @Test
    void tamperedTokensAreRejected() {
        String state = codec.issueState(SocialProvider.KAKAO).token();
        assertThrows(SocialOAuthException.class, () -> codec.validateState(
                SocialProvider.KAKAO,
                state + "x"
        ));

        String registrationToken = codec.issueRegistration(
                SocialProvider.KAKAO,
                new SocialProfileDTO(
                        "123456789",
                        null,
                        null,
                        null,
                        "N",
                        null
                )
        );
        assertThrows(SocialOAuthException.class, () -> codec.readRegistration(
                SocialProvider.KAKAO,
                registrationToken + "x"
        ));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
