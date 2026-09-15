package kdt.washPrj.auth.social;

import kdt.washPrj.auth.dto.SocialProfileDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

@Component
public class SocialLoginTokenCodec {

    private static final String STATE_VERSION = "S1";
    private static final byte REGISTRATION_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] REGISTRATION_AAD =
            "straffic:social-login:registration:v1"
                    .getBytes(StandardCharsets.UTF_8);
    private static final String STATE_KEY_DOMAIN =
            "straffic:social-login:state:v1";
    private static final String REGISTRATION_KEY_DOMAIN =
            "straffic:social-login:registration:v1";

    private final SocialLoginProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public SocialLoginTokenCodec(SocialLoginProperties properties) {
        this(properties, Clock.systemUTC(), new SecureRandom());
    }

    SocialLoginTokenCodec(
            SocialLoginProperties properties,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public IssuedState issueState(SocialProvider provider) {
        properties.requireReady();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(properties.getStateTtlSeconds());
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        String payload = String.join(
                "|",
                STATE_VERSION,
                provider.name(),
                Long.toString(issuedAt.getEpochSecond()),
                Long.toString(expiresAt.getEpochSecond()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
        );
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return new IssuedState(
                Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                        + "."
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(
                        hmac(derivedKey(STATE_KEY_DOMAIN), payloadBytes)
                ),
                properties.getStateTtlSeconds()
        );
    }

    public boolean isConfigured() {
        return properties.isReady();
    }

    public void validateState(SocialProvider provider, String state) {
        properties.requireReady();
        if (state == null || state.isBlank() || state.length() > 2048) {
            throw invalidState();
        }
        try {
            String[] tokenParts = state.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw invalidState();
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokenParts[0]);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            if (!MessageDigest.isEqual(
                    hmac(derivedKey(STATE_KEY_DOMAIN), payloadBytes),
                    suppliedSignature
            )) {
                throw invalidState();
            }

            String[] fields = new String(
                    payloadBytes,
                    StandardCharsets.UTF_8
            ).split("\\|", -1);
            if (fields.length != 5) {
                throw invalidState();
            }
            long issuedAt = Long.parseLong(fields[2]);
            long expiresAt = Long.parseLong(fields[3]);
            long now = clock.instant().getEpochSecond();
            if (!STATE_VERSION.equals(fields[0])
                    || !provider.name().equals(fields[1])
                    || fields[4].isBlank()
                    || issuedAt > now + 60
                    || expiresAt <= now
                    || expiresAt <= issuedAt
                    || expiresAt - issuedAt > properties.getStateTtlSeconds()) {
                throw invalidState();
            }
        } catch (IndexOutOfBoundsException | NumberFormatException exception) {
            throw invalidState();
        } catch (IllegalArgumentException exception) {
            throw invalidState();
        }
    }

    public String issueRegistration(
            SocialProvider provider,
            SocialProfileDTO profile
    ) {
        properties.requireReady();
        if (profile == null
                || profile.providerUserId() == null
                || profile.providerUserId().isBlank()
                || profile.providerUserId().length() > 255) {
            throw invalidRegistration();
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(
                properties.getRegistrationTtlSeconds()
        );
        try {
            ByteArrayOutputStream plaintextBytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(plaintextBytes)) {
                output.writeUTF(provider.name());
                output.writeUTF(profile.providerUserId());
                output.writeUTF(nullable(profile.name()));
                output.writeUTF(nullable(profile.emailAddress()));
                output.writeUTF(nullable(profile.phone()));
                output.writeUTF(nullable(profile.gender()));
                output.writeUTF(profile.birthDate() == null
                        ? ""
                        : profile.birthDate().toString());
                output.writeLong(issuedAt.getEpochSecond());
                output.writeLong(expiresAt.getEpochSecond());
            }

            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(
                            derivedKey(REGISTRATION_KEY_DOMAIN),
                            "AES"
                    ),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            cipher.updateAAD(REGISTRATION_AAD);
            byte[] encrypted = cipher.doFinal(plaintextBytes.toByteArray());
            ByteBuffer token = ByteBuffer.allocate(1 + IV_BYTES + encrypted.length);
            token.put(REGISTRATION_VERSION);
            token.put(iv);
            token.put(encrypted);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(token.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "소셜 가입 세션을 생성하지 못했습니다.",
                    exception
            );
        } catch (Exception exception) {
            throw invalidRegistration();
        }
    }

    public SocialProfileDTO readRegistration(
            SocialProvider expectedProvider,
            String registrationToken
    ) {
        properties.requireReady();
        if (registrationToken == null
                || registrationToken.isBlank()
                || registrationToken.length() > 4096) {
            throw invalidRegistration();
        }
        try {
            byte[] tokenBytes = Base64.getUrlDecoder().decode(registrationToken);
            if (tokenBytes.length <= 1 + IV_BYTES + 16) {
                throw invalidRegistration();
            }
            ByteBuffer token = ByteBuffer.wrap(tokenBytes);
            if (token.get() != REGISTRATION_VERSION) {
                throw invalidRegistration();
            }
            byte[] iv = new byte[IV_BYTES];
            token.get(iv);
            byte[] ciphertext = new byte[token.remaining()];
            token.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(
                            derivedKey(REGISTRATION_KEY_DOMAIN),
                            "AES"
                    ),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            cipher.updateAAD(REGISTRATION_AAD);
            byte[] plaintext = cipher.doFinal(ciphertext);

            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(plaintext)
            )) {
                SocialProvider provider = SocialProvider.valueOf(input.readUTF());
                String providerUserId = input.readUTF();
                String name = emptyToNull(input.readUTF());
                String email = emptyToNull(input.readUTF());
                String phone = emptyToNull(input.readUTF());
                String gender = emptyToNull(input.readUTF());
                String birthDateText = emptyToNull(input.readUTF());
                long issuedAt = input.readLong();
                long expiresAt = input.readLong();
                long now = clock.instant().getEpochSecond();
                if (input.available() != 0
                        || provider != expectedProvider
                        || providerUserId.isBlank()
                        || providerUserId.length() > 255
                        || issuedAt > now + 60
                        || expiresAt <= now
                        || expiresAt <= issuedAt
                        || expiresAt - issuedAt
                        > properties.getRegistrationTtlSeconds()) {
                    throw invalidRegistration();
                }
                return new SocialProfileDTO(
                        providerUserId,
                        name,
                        email,
                        phone,
                        gender == null ? "N" : gender,
                        birthDateText == null ? null : LocalDate.parse(birthDateText)
                );
            }
        } catch (SocialOAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidRegistration();
        }
    }

    private byte[] derivedKey(String domain) {
        return hmac(
                properties.decodedMasterKey(),
                domain.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "소셜 로그인 암호화 기능을 사용할 수 없습니다.",
                    exception
            );
        }
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static SocialOAuthException invalidState() {
        return new SocialOAuthException(
                "SOCIAL_STATE_INVALID",
                "소셜 로그인 요청이 유효하지 않거나 만료되었습니다.",
                HttpStatus.BAD_REQUEST
        );
    }

    private static SocialOAuthException invalidRegistration() {
        return new SocialOAuthException(
                "SOCIAL_REGISTRATION_EXPIRED",
                "소셜 회원가입 시간이 만료되었습니다. 다시 로그인해주세요.",
                HttpStatus.BAD_REQUEST
        );
    }

    public record IssuedState(String token, long expiresInSeconds) {
    }
}
