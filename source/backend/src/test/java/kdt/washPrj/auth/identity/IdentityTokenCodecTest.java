package kdt.washPrj.auth.identity;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityTokenCodecTest {

    private static final Instant NOW =
            Instant.parse("2026-08-05T00:00:00Z");
    private static final String ID =
            "iv0123456789abcdef0123456789abcdef";

    @Test
    void stateIsBoundToIdAndRejectsTampering() {
        IdentityTokenCodec codec = codecAt(NOW);
        IdentityTokenCodec.IssuedToken issued = codec.issueState(ID);

        codec.validateState(ID, issued.token());
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.validateState(
                        "ivabcdef0123456789abcdef0123456789",
                        issued.token()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.validateState(ID, mutate(issued.token()))
        );
    }

    @Test
    void stateExpiresWithoutAStoredDatabaseRecord() {
        IdentityTokenCodec.IssuedToken issued =
                codecAt(NOW).issueState(ID);

        assertThrows(
                IllegalArgumentException.class,
                () -> codecAt(NOW.plusSeconds(301))
                        .validateState(ID, issued.token())
        );
    }

    @Test
    void encryptedProofRoundTripsAndRejectsTampering() {
        IdentityTokenCodec codec = codecAt(NOW);
        VerifiedIdentityClaims original = new VerifiedIdentityClaims(
                ID,
                "홍길동",
                LocalDate.of(1995, 1, 2),
                "01012345678",
                "M",
                codec.hmacDi("provider-di-value"),
                codec.bindStateToken("signed-state-token"),
                "TEST",
                OffsetDateTime.ofInstant(
                        NOW.minusSeconds(30),
                        ZoneOffset.UTC
                ),
                OffsetDateTime.ofInstant(
                        NOW.plusSeconds(600),
                        ZoneOffset.UTC
                )
        );

        String proof = codec.issueProof(original);
        VerifiedIdentityClaims decoded = codec.readProof(
                ID,
                proof,
                "signed-state-token"
        );

        assertEquals(original, decoded);
        assertNotEquals("provider-di-value", decoded.diHmac());
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.readProof(
                        ID,
                        mutate(proof),
                        "signed-state-token"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.readProof(
                        ID,
                        proof,
                        "different-state-token"
                )
        );
    }

    @Test
    void encryptedProofPreservesMissingProviderGenderSentinel() {
        IdentityTokenCodec codec = codecAt(NOW);
        VerifiedIdentityClaims original = new VerifiedIdentityClaims(
                ID,
                "홍길동",
                LocalDate.of(1995, 1, 2),
                "01012345678",
                "U",
                codec.hmacDi("provider-di-value"),
                codec.bindStateToken("signed-state-token"),
                "TEST",
                OffsetDateTime.ofInstant(
                        NOW.minusSeconds(30),
                        ZoneOffset.UTC
                ),
                OffsetDateTime.ofInstant(
                        NOW.plusSeconds(600),
                        ZoneOffset.UTC
                )
        );

        String proof = codec.issueProof(original);
        VerifiedIdentityClaims decoded = codec.readProof(
                ID,
                proof,
                "signed-state-token"
        );

        assertEquals("U", decoded.gender());
        assertEquals(original, decoded);
    }

    private IdentityTokenCodec codecAt(Instant instant) {
        return new IdentityTokenCodec(
                properties(),
                Clock.fixed(instant, ZoneOffset.UTC),
                new SecureRandom()
        );
    }

    private PortOneIdentityProperties properties() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        return new PortOneIdentityProperties(
                true,
                "https://api.portone.io",
                "test-api-secret",
                "store-test",
                "channel-key-test",
                Base64.getEncoder().encodeToString(key),
                Base64.getEncoder().encodeToString(key),
                300,
                600,
                false,
                true
        );
    }

    private String mutate(String value) {
        int index = value.length() / 2;
        char replacement = value.charAt(index) == 'A' ? 'B' : 'A';
        return value.substring(0, index)
                + replacement
                + value.substring(index + 1);
    }
}
