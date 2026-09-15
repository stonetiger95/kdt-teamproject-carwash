package kdt.washPrj.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LineLinkCodeStore {

    private static final int CODE_BYTES = 16;
    private static final int MAX_PENDING_CODES = 10_000;

    private final LineMessagingProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Map<String, PendingLink> pendingByCode = new ConcurrentHashMap<>();
    private final Map<String, String> codeHashByLoginId = new ConcurrentHashMap<>();

    @Autowired
    public LineLinkCodeStore(LineMessagingProperties properties) {
        this(properties, new SecureRandom(), Clock.systemUTC());
    }

    LineLinkCodeStore(
            LineMessagingProperties properties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public IssuedCode issue(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        cleanupExpired();
        if (pendingByCode.size() >= MAX_PENDING_CODES) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LINE 연결 요청이 많습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        long ttlSeconds = validatedTtlSeconds();
        String normalizedLoginId = loginId.trim();
        String previousHash = codeHashByLoginId.remove(normalizedLoginId);
        if (previousHash != null) {
            pendingByCode.remove(previousHash);
        }

        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        String codeHash = hash(code);
        pendingByCode.put(
                codeHash,
                new PendingLink(
                        normalizedLoginId,
                        clock.instant().plusSeconds(ttlSeconds)
                )
        );
        codeHashByLoginId.put(normalizedLoginId, codeHash);
        return new IssuedCode(code, ttlSeconds);
    }

    public String resolve(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        PendingLink pending = pendingByCode.get(hash(code.trim()));
        if (pending == null) {
            return null;
        }
        if (!pending.expiresAt().isAfter(clock.instant())) {
            invalidate(code);
            return null;
        }
        return pending.loginId();
    }

    public void invalidate(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        String codeHash = hash(code.trim());
        PendingLink pending = pendingByCode.remove(codeHash);
        if (pending != null) {
            codeHashByLoginId.remove(pending.loginId(), codeHash);
        }
    }

    public long getTtlSeconds() {
        return validatedTtlSeconds();
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        pendingByCode.entrySet().removeIf(entry -> {
            PendingLink pending = entry.getValue();
            if (pending.expiresAt().isAfter(now)) {
                return false;
            }
            codeHashByLoginId.remove(pending.loginId(), entry.getKey());
            return true;
        });
    }

    private long validatedTtlSeconds() {
        long ttlSeconds = properties.getLinkCodeTtlSeconds();
        if (ttlSeconds < 60 || ttlSeconds > 1_800) {
            throw new IllegalStateException(
                    "LINE link-code-ttl-seconds must be between 60 and 1800."
            );
        }
        return ttlSeconds;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record IssuedCode(String code, long expiresInSeconds) {
    }

    private record PendingLink(String loginId, Instant expiresAt) {
    }
}
