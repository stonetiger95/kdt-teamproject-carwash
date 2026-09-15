package kdt.washPrj.auth.identity;

import kdt.washPrj.auth.dto.IdentityVerificationCompleteRequestDTO;
import kdt.washPrj.auth.dto.IdentityVerificationCompleteResponseDTO;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortOneIdentityServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-06T05:10:00Z");
    private static final String ID =
            "iv0123456789abcdef0123456789abcdef";
    private static final String CHANNEL_KEY = "channel-key-test";

    @Test
    void testChannelRequiresSelectionWhenKcpOmitsGender() {
        TestFixture fixture = fixture(true);
        IdentityTokenCodec.IssuedToken state =
                fixture.codec().issueState(ID);
        when(fixture.client().getVerification(ID))
                .thenReturn(verification("TEST", null));

        IdentityVerificationCompleteResponseDTO response =
                fixture.service().complete(request(state.token()));
        VerifiedIdentityClaims claims = fixture.codec().readProof(
                ID,
                response.identityProof(),
                state.token()
        );

        assertEquals("U", claims.gender());
        assertNull(response.verifiedGender());
        assertTrue(response.genderSelectionRequired());
    }

    @Test
    void liveChannelAlsoRequiresSelectionWhenKcpOmitsGender() {
        TestFixture fixture = fixture(false);
        IdentityTokenCodec.IssuedToken state =
                fixture.codec().issueState(ID);
        when(fixture.client().getVerification(ID))
                .thenReturn(verification("LIVE", null));

        IdentityVerificationCompleteResponseDTO response =
                fixture.service().complete(request(state.token()));
        VerifiedIdentityClaims claims = fixture.codec().readProof(
                ID,
                response.identityProof(),
                state.token()
        );

        assertEquals("U", claims.gender());
        assertTrue(response.genderSelectionRequired());
    }

    @Test
    void providerGenderCannotBeMistakenForMissingGender() {
        TestFixture fixture = fixture(true);
        IdentityTokenCodec.IssuedToken state =
                fixture.codec().issueState(ID);
        when(fixture.client().getVerification(ID))
                .thenReturn(verification("TEST", "MALE"));

        IdentityVerificationCompleteResponseDTO response =
                fixture.service().complete(request(state.token()));

        assertEquals("M", response.verifiedGender());
        assertFalse(response.genderSelectionRequired());
    }

    @Test
    void testChannelAllowsMissingDi() {
        TestFixture fixture = fixture(true);
        IdentityTokenCodec.IssuedToken state =
                fixture.codec().issueState(ID);
        when(fixture.client().getVerification(ID))
                .thenReturn(verificationWithoutDi("TEST"));

        IdentityVerificationCompleteResponseDTO response =
                fixture.service().complete(request(state.token()));
        VerifiedIdentityClaims claims = fixture.codec().readProof(
                ID,
                response.identityProof(),
                state.token()
        );

        assertTrue(claims.diHmac().matches("^[0-9a-f]{64}$"));
    }

    @Test
    void liveChannelStillRejectsMissingDi() {
        TestFixture fixture = fixture(false);
        IdentityTokenCodec.IssuedToken state =
                fixture.codec().issueState(ID);
        when(fixture.client().getVerification(ID))
                .thenReturn(verificationWithoutDi("LIVE"));

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().complete(request(state.token()))
        );
    }

    private TestFixture fixture(boolean testMode) {
        PortOneIdentityProperties properties = properties(testMode);
        PortOneIdentityClient client = mock(PortOneIdentityClient.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        IdentityTokenCodec codec = new IdentityTokenCodec(
                properties,
                clock,
                new SecureRandom()
        );
        PortOneIdentityService service = new PortOneIdentityService(
                properties,
                client,
                codec,
                clock
        );
        return new TestFixture(service, client, codec);
    }

    private IdentityVerificationCompleteRequestDTO request(
            String stateToken
    ) {
        IdentityVerificationCompleteRequestDTO request =
                new IdentityVerificationCompleteRequestDTO();
        request.setIdentityVerificationId(ID);
        request.setStateToken(stateToken);
        return request;
    }

    private PortOneIdentityClient.PortOneVerification verification(
            String channelType,
            String gender
    ) {
        return new PortOneIdentityClient.PortOneVerification(
                "VERIFIED",
                ID,
                new PortOneIdentityClient.SelectedChannel(
                        channelType,
                        CHANNEL_KEY
                ),
                new PortOneIdentityClient.VerifiedCustomer(
                        "홍길동",
                        "2001-01-01",
                        "01012345678",
                        gender,
                        "provider-di",
                        "provider-ci"
                ),
                NOW.minusSeconds(10).toString(),
                "V2"
        );
    }

    private PortOneIdentityClient.PortOneVerification verificationWithoutDi(
            String channelType
    ) {
        return new PortOneIdentityClient.PortOneVerification(
                "VERIFIED",
                ID,
                new PortOneIdentityClient.SelectedChannel(
                        channelType,
                        CHANNEL_KEY
                ),
                new PortOneIdentityClient.VerifiedCustomer(
                        "홍길동",
                        "2001-01-01",
                        "01012345678",
                        null,
                        null,
                        null
                ),
                NOW.minusSeconds(10).toString(),
                "V2"
        );
    }

    private PortOneIdentityProperties properties(boolean testMode) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        return new PortOneIdentityProperties(
                true,
                "https://api.portone.io",
                "test-api-secret",
                "store-test",
                CHANNEL_KEY,
                Base64.getEncoder().encodeToString(key),
                Base64.getEncoder().encodeToString(key),
                300,
                600,
                !testMode,
                testMode
        );
    }

    private record TestFixture(
            PortOneIdentityService service,
            PortOneIdentityClient client,
            IdentityTokenCodec codec
    ) {
    }
}
