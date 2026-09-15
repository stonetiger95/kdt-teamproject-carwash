package kdt.washPrj.auth.recovery;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PasswordRecoveryCookieFactory {

    public static final String COOKIE_PATH =
            "/api/auth/password-recovery";

    private final PasswordRecoveryProperties properties;

    public PasswordRecoveryCookieFactory(
            PasswordRecoveryProperties properties
    ) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return ResponseCookie.from(properties.getCookieName(), token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(
                        properties.getResetTtlSeconds()
                ))
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
