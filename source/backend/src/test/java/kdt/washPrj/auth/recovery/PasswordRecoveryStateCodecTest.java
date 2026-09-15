package kdt.washPrj.auth.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordRecoveryStateCodecTest {

    private PasswordRecoveryStateCodec codec;

    @BeforeEach
    void setUp() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties();
        codec = new PasswordRecoveryStateCodec(
                properties,
                new PasswordRecoveryCrypto(properties)
        );
    }

    @Test
    void stateIsBoundToRecoveryMethodAndProviderId() {
        UUID recoveryId = UUID.randomUUID();
        String verificationId = "iv0123456789abcdef0123456789abcdef";
        PasswordRecoveryStateCodec.IssuedState state = codec.issue(
                recoveryId,
                PasswordRecoveryMethod.PHONE_IDENTITY,
                verificationId
        );

        assertDoesNotThrow(() -> codec.validate(
                recoveryId,
                PasswordRecoveryMethod.PHONE_IDENTITY,
                verificationId,
                state.token()
        ));
        assertThrows(IllegalArgumentException.class, () -> codec.validate(
                recoveryId,
                PasswordRecoveryMethod.EASY_AUTH,
                verificationId,
                state.token()
        ));
    }

    @Test
    void tamperedStateIsRejected() {
        UUID recoveryId = UUID.randomUUID();
        String verificationId = "iv0123456789abcdef0123456789abcdef";
        PasswordRecoveryStateCodec.IssuedState state = codec.issue(
                recoveryId,
                PasswordRecoveryMethod.PHONE_IDENTITY,
                verificationId
        );

        assertThrows(IllegalArgumentException.class, () -> codec.validate(
                recoveryId,
                PasswordRecoveryMethod.PHONE_IDENTITY,
                verificationId,
                state.token() + "x"
        ));
    }
}
