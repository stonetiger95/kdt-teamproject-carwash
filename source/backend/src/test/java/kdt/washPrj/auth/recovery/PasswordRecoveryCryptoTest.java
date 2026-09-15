package kdt.washPrj.auth.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordRecoveryCryptoTest {

    private PasswordRecoveryCrypto crypto;

    @BeforeEach
    void setUp() {
        crypto = new PasswordRecoveryCrypto(
                PasswordRecoveryTestFixtures.properties()
        );
    }

    @Test
    void otpIsAlwaysSixDigits() {
        assertTrue(crypto.generateOtp().matches("^[0-9]{6}$"));
    }

    @Test
    void resetTokensAreRandomAndTheirHmacIsStable() {
        String first = crypto.generateResetToken();
        String second = crypto.generateResetToken();

        assertNotEquals(first, second);
        assertEquals(
                crypto.hmacResetToken(first),
                crypto.hmacResetToken(first)
        );
        assertNotEquals(
                crypto.hmacResetToken(first),
                crypto.hmacResetToken(second)
        );
    }

    @Test
    void otpHmacIsBoundToChallenge() {
        String first = crypto.hmacOtp(UUID.randomUUID(), "123456");
        String second = crypto.hmacOtp(UUID.randomUUID(), "123456");

        assertNotEquals(first, second);
    }
}
