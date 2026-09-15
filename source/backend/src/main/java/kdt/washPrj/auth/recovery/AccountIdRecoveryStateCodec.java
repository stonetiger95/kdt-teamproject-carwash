package kdt.washPrj.auth.recovery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Component
public class AccountIdRecoveryStateCodec {

    private static final String VERSION = "I1";
    private static final String PURPOSE = "ACCOUNT_ID_RECOVERY";

    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryCrypto crypto;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public AccountIdRecoveryStateCodec(
            PasswordRecoveryProperties properties,
            PasswordRecoveryCrypto crypto
    ) {
        this(properties, crypto, Clock.systemUTC(), new SecureRandom());
    }

    AccountIdRecoveryStateCodec(
            PasswordRecoveryProperties properties,
            PasswordRecoveryCrypto crypto,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.crypto = crypto;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public IssuedState issue(
            UUID recoveryId,
            PasswordRecoveryMethod method,
            String identityVerificationId
    ) {
        properties.requireReady();
        validateInputs(recoveryId, method, identityVerificationId);

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(
                properties.getChallengeTtlSeconds()
        );
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);

        String payload = String.join(
                "|",
                VERSION,
                PURPOSE,
                recoveryId.toString(),
                method.name(),
                identityVerificationId,
                Long.toString(issuedAt.getEpochSecond()),
                Long.toString(expiresAt.getEpochSecond()),
                Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(nonce)
        );
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadBytes)
                + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(crypto.signAccountIdState(payloadBytes));

        return new IssuedState(
                token,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
    }

    public void validate(
            UUID recoveryId,
            PasswordRecoveryMethod method,
            String identityVerificationId,
            String stateToken
    ) {
        properties.requireReady();
        validateInputs(recoveryId, method, identityVerificationId);
        if (stateToken == null
                || stateToken.isBlank()
                || stateToken.length() > 2048) {
            throw invalidState();
        }

        try {
            String[] tokenParts = stateToken.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw invalidState();
            }

            byte[] payloadBytes = Base64.getUrlDecoder()
                    .decode(tokenParts[0]);
            byte[] suppliedSignature = Base64.getUrlDecoder()
                    .decode(tokenParts[1]);
            if (!MessageDigest.isEqual(
                    crypto.signAccountIdState(payloadBytes),
                    suppliedSignature
            )) {
                throw invalidState();
            }

            String[] fields = new String(
                    payloadBytes,
                    StandardCharsets.UTF_8
            ).split("\\|", -1);
            if (fields.length != 8
                    || !VERSION.equals(fields[0])
                    || !PURPOSE.equals(fields[1])
                    || !recoveryId.toString().equals(fields[2])
                    || !method.name().equals(fields[3])
                    || !identityVerificationId.equals(fields[4])
                    || fields[7].isBlank()) {
                throw invalidState();
            }

            long issuedAt = Long.parseLong(fields[5]);
            long expiresAt = Long.parseLong(fields[6]);
            long now = clock.instant().getEpochSecond();
            if (issuedAt > now + 60
                    || expiresAt <= now
                    || expiresAt <= issuedAt
                    || expiresAt - issuedAt
                    > properties.getChallengeTtlSeconds()) {
                throw invalidState();
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("아이디 찾기 상태값")) {
                throw exception;
            }
            throw invalidState();
        }
    }

    private static void validateInputs(
            UUID recoveryId,
            PasswordRecoveryMethod method,
            String identityVerificationId
    ) {
        if (recoveryId == null
                || method == null
                || !method.isIdentityMethod()
                || identityVerificationId == null
                || !identityVerificationId.matches("^[A-Za-z0-9]{16,40}$")) {
            throw invalidState();
        }
    }

    private static IllegalArgumentException invalidState() {
        return new IllegalArgumentException(
                "아이디 찾기 상태값이 유효하지 않거나 만료되었습니다."
        );
    }

    public record IssuedState(String token, OffsetDateTime expiresAt) {
    }
}
