package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartResponseDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryVerifyRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

class EmailPasswordRecoveryServiceTest {

    private PasswordRecoveryMapper mapper;
    private RecoveryEmailSender emailSender;
    private EmailPasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties();
        mapper = mock(PasswordRecoveryMapper.class);
        emailSender = mock(RecoveryEmailSender.class);
        when(emailSender.sendVerificationCode(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        service = new EmailPasswordRecoveryService(
                properties,
                mapper,
                new PasswordRecoveryCrypto(properties),
                emailSender
        );
    }

    @Test
    void startSendsOnlyToCanonicalDatabaseDestinationAndMarksSent() {
        BeginPasswordRecoveryResultDTO result = accepted();
        result.setDestinationEmail("canonical@example.com");
        when(mapper.begin(
                any(),
                eq("customer01"),
                eq("customer@example.com"),
                eq("EMAIL_OTP"),
                anyString(),
                anyString(),
                any(),
                anyInt(),
                anyInt()
        )).thenReturn(result);

        EmailRecoveryStartResponseDTO response = service.start(
                new EmailRecoveryStartRequestDTO(
                        "customer01",
                        "Customer@Example.com"
                )
        );

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(
                eq("canonical@example.com"),
                code.capture()
        );
        assertTrue(code.getValue().matches("^[0-9]{6}$"));
        verify(mapper).markEmailDelivery(any(), eq("SENT"));
        assertEquals(60, response.resendAfterSeconds());
    }

    @Test
    void startKeepsMissingAccountResponseGenericAndMarksFailed() {
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

        EmailRecoveryStartResponseDTO response = service.start(
                new EmailRecoveryStartRequestDTO(
                        "missing",
                        "missing@example.com"
                )
        );

        verify(mapper).markEmailDelivery(any(), eq("FAILED"));
        assertTrue(response.message().contains("계정이 있으면"));
    }

    @Test
    void startDoesNotWaitForEmailDeliveryCompletion() {
        BeginPasswordRecoveryResultDTO result = accepted();
        result.setDestinationEmail("canonical@example.com");
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
        )).thenReturn(result);
        CompletableFuture<Void> delivery = new CompletableFuture<>();
        when(emailSender.sendVerificationCode(anyString(), anyString()))
                .thenReturn(delivery);

        EmailRecoveryStartResponseDTO response = service.start(
                new EmailRecoveryStartRequestDTO(
                        "customer01",
                        "customer@example.com"
                )
        );

        assertTrue(response.message().contains("계정이 있으면"));
        verify(mapper, never()).markEmailDelivery(any(), anyString());

        delivery.complete(null);

        verify(mapper).markEmailDelivery(any(), eq("SENT"));
    }

    @Test
    void verifiedOtpIssuesEmailResetSession() {
        when(mapper.verifyEmail(any(), anyString(), anyString(), any()))
                .thenReturn("OK");

        IssuedPasswordRecoverySession issued = service.verify(
                new EmailRecoveryVerifyRequestDTO(
                        java.util.UUID.randomUUID().toString(),
                        "123456"
                )
        );

        assertTrue(issued.response().authorized());
        assertEquals("EMAIL_OTP", issued.response().method());
    }

    private BeginPasswordRecoveryResultDTO accepted() {
        BeginPasswordRecoveryResultDTO result =
                new BeginPasswordRecoveryResultDTO();
        result.setResultCode("ACCEPTED");
        return result;
    }
}
