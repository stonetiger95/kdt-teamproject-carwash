package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.PasswordResetAuthorizationDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordRecoveryServiceTest {

    private PasswordRecoveryMapper mapper;
    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties();
        mapper = mock(PasswordRecoveryMapper.class);
        service = new PasswordRecoveryService(
                properties,
                mapper,
                new PasswordRecoveryCrypto(properties)
        );
    }

    @Test
    void missingCookieHasNoAuthorizedSession() {
        assertFalse(service.getSession(null).authorized());
    }

    @Test
    void validCookieReturnsDatabaseAuthorization() {
        PasswordResetAuthorizationDTO authorization =
                new PasswordResetAuthorizationDTO();
        authorization.setAuthorized(true);
        authorization.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        authorization.setMethod("EMAIL_OTP");
        when(mapper.authorizationStatus(anyString()))
                .thenReturn(authorization);

        assertTrue(service.getSession("opaque-token").authorized());
    }

    @Test
    void resetConsumesAuthorizationExactlyThroughDatabaseResult() {
        when(mapper.consume(anyString(), anyString())).thenReturn("OK");

        service.reset(
                "opaque-token",
                new PasswordResetRequestDTO("newPassword123")
        );
    }

    @Test
    void usedOrExpiredAuthorizationIsRejected() {
        when(mapper.consume(anyString(), anyString()))
                .thenReturn("INVALID");

        assertThrows(ResponseStatusException.class, () -> service.reset(
                "opaque-token",
                new PasswordResetRequestDTO("newPassword123")
        ));
    }

    @Test
    void passwordLongerThanSeventyTwoUtf8BytesIsRejectedBeforeDatabaseCall() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.reset(
                        "opaque-token",
                        new PasswordResetRequestDTO("가".repeat(25))
                )
        );

        assertTrue(exception.getMessage().contains("72바이트"));
    }

    @Test
    void databaseInvalidPasswordResultIsReturnedAsBadRequest() {
        when(mapper.consume(anyString(), anyString()))
                .thenReturn("INVALID_PASSWORD");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.reset(
                        "opaque-token",
                        new PasswordResetRequestDTO("newPassword123")
                )
        );

        assertTrue(exception.getMessage().contains("72바이트"));
    }
}
