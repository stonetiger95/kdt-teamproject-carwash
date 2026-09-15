package kdt.washPrj.auth.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class IdentityTokenCodec {

    private static final String PURPOSE = "SIGNUP";
    private static final String STATE_VERSION = "S1";
    private static final byte PROOF_VERSION = 2;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] PROOF_AAD =
            "straffic:portone:identity-proof:v2"
                    .getBytes(StandardCharsets.UTF_8);

    private static final String STATE_KEY_DOMAIN =
            "straffic:portone:state:v1";
    private static final String PROOF_KEY_DOMAIN =
            "straffic:portone:proof:v2";
    private static final String DI_KEY_DOMAIN =
            "straffic:portone:di:v1";
    private static final String CI_KEY_DOMAIN =
            "straffic:portone:ci:v1";
    private static final String STATE_BINDING_KEY_DOMAIN =
            "straffic:portone:state-binding:v1";

    private final PortOneIdentityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public IdentityTokenCodec(PortOneIdentityProperties properties) {
        this(properties, Clock.systemUTC(), new SecureRandom());
    }

    IdentityTokenCodec(
            PortOneIdentityProperties properties,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public IssuedToken issueState(String identityVerificationId) {
        properties.requireReady();
        requireIdentityVerificationId(identityVerificationId);

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(
                properties.getStateTtlSeconds()
        );
        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);

        String payload = String.join(
                "|",
                STATE_VERSION,
                PURPOSE,
                identityVerificationId,
                Long.toString(issuedAt.getEpochSecond()),
                Long.toString(expiresAt.getEpochSecond()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
        );
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signature = hmac(
                derivedKey(STATE_KEY_DOMAIN),
                payloadBytes
        );

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payloadBytes)
                + "."
                + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(signature);

        return new IssuedToken(token, atUtc(expiresAt));
    }

    public void validateState(
            String expectedIdentityVerificationId,
            String token
    ) {
        properties.requireReady();
        requireIdentityVerificationId(expectedIdentityVerificationId);

        if (token == null || token.isBlank() || token.length() > 2048) {
            throw invalidState();
        }

        String[] tokenParts = token.split("\\.", -1);
        if (tokenParts.length != 2) {
            throw invalidState();
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokenParts[0]);
            byte[] suppliedSignature =
                    Base64.getUrlDecoder().decode(tokenParts[1]);
            byte[] expectedSignature = hmac(
                    derivedKey(STATE_KEY_DOMAIN),
                    payloadBytes
            );

            if (!MessageDigest.isEqual(
                    expectedSignature,
                    suppliedSignature
            )) {
                throw invalidState();
            }

            String[] fields = new String(
                    payloadBytes,
                    StandardCharsets.UTF_8
            ).split("\\|", -1);
            if (fields.length != 6
                    || !STATE_VERSION.equals(fields[0])
                    || !PURPOSE.equals(fields[1])
                    || !expectedIdentityVerificationId.equals(fields[2])
                    || fields[5].isBlank()) {
                throw invalidState();
            }

            long issuedAtEpoch = Long.parseLong(fields[3]);
            long expiresAtEpoch = Long.parseLong(fields[4]);
            long nowEpoch = clock.instant().getEpochSecond();

            if (issuedAtEpoch > nowEpoch + 60
                    || expiresAtEpoch <= nowEpoch
                    || expiresAtEpoch <= issuedAtEpoch
                    || expiresAtEpoch - issuedAtEpoch
                    > properties.getStateTtlSeconds()) {
                throw invalidState();
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "본인인증 상태값"
            )) {
                throw exception;
            }
            throw invalidState();
        }
    }

    public String hmacDi(String di) {
        properties.requireProviderReady();
        if (di == null || di.isBlank() || di.length() > 2048) {
            throw new IllegalArgumentException(
                    "본인인증 중복가입 확인정보가 없습니다."
            );
        }
        return HexFormat.of().formatHex(hmac(
                derivedDiKey(),
                di.getBytes(StandardCharsets.UTF_8)
        ));
    }

    public String hmacCi(String ci) {
        properties.requireProviderReady();
        if (ci == null || ci.isBlank()) {
            return null;
        }
        if (ci.length() > 2048) {
            throw new IllegalArgumentException(
                    "본인인증 연계정보 형식을 확인해주세요."
            );
        }
        return HexFormat.of().formatHex(hmac(
                derivedKey(CI_KEY_DOMAIN),
                ci.getBytes(StandardCharsets.UTF_8)
        ));
    }

    public String bindStateToken(String stateToken) {
        properties.requireReady();
        if (stateToken == null
                || stateToken.isBlank()
                || stateToken.length() > 2048) {
            throw invalidState();
        }
        return HexFormat.of().formatHex(hmac(
                derivedKey(STATE_BINDING_KEY_DOMAIN),
                stateToken.getBytes(StandardCharsets.UTF_8)
        ));
    }

    public String issueProof(VerifiedIdentityClaims claims) {
        properties.requireReady();
        validateClaims(claims, false);

        try {
            ByteArrayOutputStream plaintextBytes =
                    new ByteArrayOutputStream();
            try (DataOutputStream output =
                         new DataOutputStream(plaintextBytes)) {
                output.writeUTF(claims.identityVerificationId());
                output.writeUTF(claims.name());
                output.writeUTF(claims.birthDate().toString());
                output.writeUTF(claims.phone());
                output.writeUTF(claims.gender());
                output.writeUTF(claims.diHmac());
                output.writeUTF(claims.stateBinding());
                output.writeUTF(claims.verificationMode());
                output.writeUTF(claims.verifiedAt().toString());
                output.writeUTF(claims.proofExpiresAt().toString());
            }

            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(
                            derivedKey(PROOF_KEY_DOMAIN),
                            "AES"
                    ),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            cipher.updateAAD(PROOF_AAD);
            byte[] ciphertext = cipher.doFinal(
                    plaintextBytes.toByteArray()
            );

            ByteBuffer token = ByteBuffer.allocate(
                    1 + iv.length + ciphertext.length
            );
            token.put(PROOF_VERSION);
            token.put(iv);
            token.put(ciphertext);

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(token.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "본인인증 증명 생성에 실패했습니다."
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "본인인증 결과 형식을 확인해주세요."
            );
        }
    }

    public VerifiedIdentityClaims readProof(
            String expectedIdentityVerificationId,
            String identityProof,
            String expectedStateToken
    ) {
        properties.requireReady();
        requireIdentityVerificationId(expectedIdentityVerificationId);

        if (identityProof == null
                || identityProof.isBlank()
                || identityProof.length() > 4096) {
            throw invalidProof();
        }

        try {
            byte[] tokenBytes = Base64.getUrlDecoder().decode(identityProof);
            if (tokenBytes.length <= 1 + GCM_IV_BYTES + 16) {
                throw invalidProof();
            }

            ByteBuffer token = ByteBuffer.wrap(tokenBytes);
            if (token.get() != PROOF_VERSION) {
                throw invalidProof();
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            token.get(iv);
            byte[] ciphertext = new byte[token.remaining()];
            token.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(
                            derivedKey(PROOF_KEY_DOMAIN),
                            "AES"
                    ),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            cipher.updateAAD(PROOF_AAD);
            byte[] plaintext = cipher.doFinal(ciphertext);

            VerifiedIdentityClaims claims;
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(plaintext)
            )) {
                claims = new VerifiedIdentityClaims(
                        input.readUTF(),
                        input.readUTF(),
                        java.time.LocalDate.parse(input.readUTF()),
                        input.readUTF(),
                        input.readUTF(),
                        input.readUTF(),
                        input.readUTF(),
                        input.readUTF(),
                        OffsetDateTime.parse(input.readUTF()),
                        OffsetDateTime.parse(input.readUTF())
                );
                if (input.available() != 0) {
                    throw invalidProof();
                }
            }

            validateClaims(claims, true);
            if (!expectedIdentityVerificationId.equals(
                    claims.identityVerificationId()
            )) {
                throw invalidProof();
            }
            byte[] expectedBinding = bindStateToken(
                    expectedStateToken
            ).getBytes(StandardCharsets.UTF_8);
            byte[] proofBinding = claims.stateBinding()
                    .getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(
                    expectedBinding,
                    proofBinding
            )) {
                throw invalidProof();
            }
            return claims;
        } catch (AEADBadTagException exception) {
            throw invalidProof();
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "본인인증 증명"
            )) {
                throw exception;
            }
            throw invalidProof();
        } catch (Exception exception) {
            throw invalidProof();
        }
    }

    private void validateClaims(
            VerifiedIdentityClaims claims,
            boolean requireNotExpired
    ) {
        if (claims == null) {
            throw invalidProof();
        }
        requireIdentityVerificationId(claims.identityVerificationId());

        if (claims.name() == null
                || claims.name().isBlank()
                || claims.name().length() > 50
                || claims.birthDate() == null
                || !claims.birthDate().isBefore(
                java.time.LocalDate.now(clock)
        )
                || claims.phone() == null
                || !claims.phone().matches("^01[016789][0-9]{7,8}$")
                || claims.gender() == null
                || !claims.gender().matches("^(M|F|N|U)$")
                || claims.diHmac() == null
                || !claims.diHmac().matches("^[0-9a-f]{64}$")
                || claims.stateBinding() == null
                || !claims.stateBinding().matches("^[0-9a-f]{64}$")
                || claims.verificationMode() == null
                || !claims.verificationMode().matches("^(TEST|LIVE)$")
                || claims.verifiedAt() == null
                || claims.proofExpiresAt() == null) {
            throw invalidProof();
        }

        Instant now = clock.instant();
        Instant expiresAt = claims.proofExpiresAt().toInstant();
        if (claims.verifiedAt().toInstant().isAfter(now.plusSeconds(60))
                || !expiresAt.isAfter(claims.verifiedAt().toInstant())
                || expiresAt.isAfter(
                now.plusSeconds(properties.getProofTtlSeconds() + 60L)
        )
                || (requireNotExpired && !expiresAt.isAfter(now))) {
            throw invalidProof();
        }
    }

    private byte[] derivedKey(String domain) {
        return hmac(
                properties.decodedMasterKey(),
                domain.getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] derivedDiKey() {
        return hmac(
                properties.decodedDiHmacKey(),
                DI_KEY_DOMAIN.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Identity verification cryptography is unavailable."
            );
        }
    }

    private static void requireIdentityVerificationId(String value) {
        if (value == null
                || !value.matches("^[A-Za-z0-9]{16,40}$")) {
            throw new IllegalArgumentException(
                    "본인인증 요청번호 형식을 확인해주세요."
            );
        }
    }

    private static IllegalArgumentException invalidState() {
        return new IllegalArgumentException(
                "본인인증 상태값이 유효하지 않거나 만료되었습니다."
        );
    }

    private static IllegalArgumentException invalidProof() {
        return new IllegalArgumentException(
                "본인인증 증명이 유효하지 않거나 만료되었습니다."
        );
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record IssuedToken(
            String token,
            OffsetDateTime expiresAt
    ) {
    }
}
