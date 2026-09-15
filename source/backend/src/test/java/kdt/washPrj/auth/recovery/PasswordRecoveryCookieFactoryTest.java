package kdt.washPrj.auth.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordRecoveryCookieFactoryTest {

    @Test
    void defaultCookieIsHttpOnlyStrictAndScopedToRecoveryApi() {
        PasswordRecoveryCookieFactory factory =
                new PasswordRecoveryCookieFactory(
                        PasswordRecoveryTestFixtures.properties()
                );

        String cookie = factory.create("opaque-token").toString();

        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains(
                "Path=/api/auth/password-recovery"
        ));
    }

    @Test
    void crossSiteCookieRequiresSecureTransport() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties("None", false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                properties::requireReady
        );

        assertEquals(503, exception.getStatusCode().value());
    }

    @Test
    void secureCrossSiteCookieIsAccepted() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties("none", true);

        properties.requireReady();

        assertEquals("None", properties.getCookieSameSite());
    }
}
