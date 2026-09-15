package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.identity.IdentityTokenCodec;
import kdt.washPrj.auth.identity.PortOneIdentityClient;
import kdt.washPrj.auth.identity.PortOneIdentityProperties;
import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryCompleteRequestDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryStartResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortOnePasswordRecoveryServiceTest {

    private PasswordRecoveryMapper mapper;
    private PortOneIdentityClient portOneClient;
    private PortOnePasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        PasswordRecoveryProperties recoveryProperties =
                PasswordRecoveryTestFixtures.properties();
        PortOneIdentityProperties identityProperties = identityProperties();
        mapper = mock(PasswordRecoveryMapper.class);
        portOneClient = mock(PortOneIdentityClient.class);
        service = new PortOnePasswordRecoveryService(
                recoveryProperties,
                identityProperties,
                mapper,
                new PasswordRecoveryStateCodec(
                        recoveryProperties,
                        new PasswordRecoveryCrypto(recoveryProperties)
                ),
                new PasswordRecoveryCrypto(recoveryProperties),
                portOneClient,
                new IdentityTokenCodec(identityProperties)
        );
    }

    @Test
    void easyAuthStartUsesOnlyServerConfiguredEasyChannel() {
        when(mapper.begin(
                any(),
                eq("customer01"),
                eq(""),
                eq("EASY_AUTH"),
                anyString(),
                eq(""),
                any(),
                anyInt(),
                anyInt()
        )).thenReturn(accepted());

        IdentityRecoveryStartResponseDTO response = service.start(
                new IdentityRecoveryStartRequestDTO(
                        "customer01",
                        PasswordRecoveryMethod.EASY_AUTH
                )
        );

        assertEquals("easy-channel", response.channelKey());
        assertTrue(response.testMode());
    }

    @Test
    void completedEasyAuthAllowsMissingDiAndCiForDatabaseFallback() {
        when(mapper.begin(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyInt(),
                anyInt()
        )).thenReturn(accepted());
        IdentityRecoveryStartResponseDTO started = service.start(
                new IdentityRecoveryStartRequestDTO(
                        "customer01",
                        PasswordRecoveryMethod.EASY_AUTH
                )
        );

        when(portOneClient.getVerification(
                started.identityVerificationId()
        )).thenReturn(new PortOneIdentityClient.PortOneVerification(
                "VERIFIED",
                started.identityVerificationId(),
                new PortOneIdentityClient.SelectedChannel(
                        "TEST",
                        "easy-channel"
                ),
                new PortOneIdentityClient.VerifiedCustomer(
                        "홍길동",
                        "2001-01-01",
                        "01012345678",
                        null,
                        null,
                        null
                ),
                OffsetDateTime.now().minusSeconds(5).toString(),
                "V2"
        ));
        when(mapper.completeIdentity(
                any(),
                eq("EASY_AUTH"),
                eq(started.identityVerificationId()),
                eq("TEST"),
                eq("홍길동"),
                any(),
                eq("01012345678"),
                eq(""),
                isNull(),
                any(),
                anyString(),
                any()
        )).thenReturn("OK");

        IssuedPasswordRecoverySession issued = service.complete(
                new IdentityRecoveryCompleteRequestDTO(
                        started.recoveryId(),
                        PasswordRecoveryMethod.EASY_AUTH,
                        started.identityVerificationId(),
                        started.stateToken()
                )
        );

        assertTrue(issued.response().authorized());
        assertEquals("EASY_AUTH", issued.response().method());
        verify(portOneClient).getVerification(
                started.identityVerificationId()
        );
    }

    private BeginPasswordRecoveryResultDTO accepted() {
        BeginPasswordRecoveryResultDTO result =
                new BeginPasswordRecoveryResultDTO();
        result.setResultCode("ACCEPTED");
        return result;
    }

    private PortOneIdentityProperties identityProperties() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        String encoded = Base64.getEncoder().encodeToString(key);
        return new PortOneIdentityProperties(
                true,
                "https://api.portone.io",
                "api-secret",
                "store-id",
                "",
                encoded,
                encoded,
                1,
                1,
                true,
                true
        );
    }
}
