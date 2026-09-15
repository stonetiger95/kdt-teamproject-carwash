package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.NaverProfileDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NaverLoginSessionStore {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_SESSIONS = 10_000;

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final long stateTtlSeconds;
    private final long registrationTtlSeconds;
    private final Map<String, Instant> states = new ConcurrentHashMap<>();
    private final Map<String, PendingRegistration> registrations = new ConcurrentHashMap<>();

    @Autowired
    public NaverLoginSessionStore(
            @Value("${naver.login.state-ttl-seconds:300}") long stateTtlSeconds,
            @Value("${naver.login.registration-ttl-seconds:600}") long registrationTtlSeconds
    ) {
        this(stateTtlSeconds, registrationTtlSeconds, new SecureRandom(), Clock.systemUTC());
    }

    NaverLoginSessionStore(
            long stateTtlSeconds,
            long registrationTtlSeconds,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.stateTtlSeconds = stateTtlSeconds;
        this.registrationTtlSeconds = registrationTtlSeconds;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public String issueState() {
        cleanupExpired();
        enforceCapacity(states);
        String state = randomToken();
        states.put(hash(state), clock.instant().plusSeconds(stateTtlSeconds));
        return state;
    }

    public void consumeState(String state) {
        if (state == null || state.isBlank()) {
            throw invalidState();
        }

        Instant expiresAt = states.remove(hash(state));
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw invalidState();
        }
    }

    public String issueRegistration(NaverProfileDTO profile) {
        cleanupExpired();
        enforceCapacity(registrations);
        String token = randomToken();
        registrations.put(
                hash(token),
                new PendingRegistration(
                        profile,
                        clock.instant().plusSeconds(registrationTtlSeconds)
                )
        );
        return token;
    }

    public NaverProfileDTO consumeRegistration(String token) {
        if (token == null || token.isBlank()) {
            throw expiredRegistration();
        }

        PendingRegistration pending = registrations.remove(hash(token));
        if (pending == null || !pending.expiresAt().isAfter(clock.instant())) {
            throw expiredRegistration();
        }
        return pending.profile();
    }

    public long getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        states.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        registrations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void enforceCapacity(Map<?, ?> sessions) {
        if (sessions.size() >= MAX_SESSIONS) {
            throw new NaverOAuthException(
                    "NAVER_LOGIN_BUSY",
                    "네이버 로그인 요청이 많습니다. 잠시 후 다시 시도해주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private NaverOAuthException invalidState() {
        return new NaverOAuthException(
                "NAVER_STATE_INVALID",
                "네이버 로그인 요청이 만료되었거나 이미 사용되었습니다.",
                HttpStatus.BAD_REQUEST
        );
    }

    private NaverOAuthException expiredRegistration() {
        return new NaverOAuthException(
                "NAVER_REGISTRATION_EXPIRED",
                "네이버 회원가입 시간이 만료되었습니다. 다시 로그인해주세요.",
                HttpStatus.BAD_REQUEST
        );
    }

    private record PendingRegistration(
            NaverProfileDTO profile,
            Instant expiresAt
    ) {
    }
}
