package kdt.washPrj.customer;

import kdt.washPrj.auth.recovery.PasswordRecoveryCrypto;
import kdt.washPrj.auth.recovery.PasswordRecoveryProperties;
import kdt.washPrj.auth.recovery.RecoveryEmailSender;
import kdt.washPrj.customer.dto.BeginCustomerEmailChangeResultDTO;
import kdt.washPrj.customer.dto.CompleteCustomerEmailChangeResultDTO;
import kdt.washPrj.customer.dto.CustomerEmailChangeStartRequestDTO;
import kdt.washPrj.customer.dto.CustomerEmailChangeStartResponseDTO;
import kdt.washPrj.customer.dto.CustomerEmailChangeVerifyRequestDTO;
import kdt.washPrj.customer.dto.CustomerMyPageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerEmailChangeServiceTest {

    private CustomerEmailChangeMapper mapper;
    private RecoveryEmailSender emailSender;
    private CustomerEmailChangeService service;

    @BeforeEach
    void setUp() {
        PasswordRecoveryProperties properties = properties();
        mapper = mock(CustomerEmailChangeMapper.class);
        emailSender = mock(RecoveryEmailSender.class);
        when(emailSender.sendEmailChangeVerificationCode(
                anyString(),
                anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));
        service = new CustomerEmailChangeService(
                properties,
                new PasswordRecoveryCrypto(properties),
                emailSender,
                mapper
        );
    }

    @Test
    void startSendsOtpToDatabaseApprovedDestination() {
        BeginCustomerEmailChangeResultDTO result = beginResult(
                "ACCEPTED",
                "new@example.com"
        );
        when(mapper.begin(
                any(),
                eq("customer01"),
                eq("new@example.com"),
                anyString(),
                any(),
                anyInt(),
                anyInt()
        )).thenReturn(result);

        CustomerEmailChangeStartResponseDTO response = service.start(
                "customer01",
                new CustomerEmailChangeStartRequestDTO("New@Example.com")
        );

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendEmailChangeVerificationCode(
                eq("new@example.com"),
                code.capture()
        );
        assertTrue(code.getValue().matches("^[0-9]{6}$"));
        verify(mapper).markDelivery(any(UUID.class), eq("SENT"));
        assertFalse(response.alreadySynchronized());
    }

    @Test
    void startSkipsMailWhenAddressesAlreadyMatch() {
        when(mapper.begin(
                any(), anyString(), anyString(), anyString(), any(),
                anyInt(), anyInt()
        )).thenReturn(beginResult("UNCHANGED", null));

        CustomerEmailChangeStartResponseDTO response = service.start(
                "customer01",
                new CustomerEmailChangeStartRequestDTO("same@example.com")
        );

        assertTrue(response.alreadySynchronized());
        verify(emailSender, never()).sendEmailChangeVerificationCode(
                anyString(),
                anyString()
        );
    }

    @Test
    void verifyReturnsAtomicallyUpdatedProfile() {
        CompleteCustomerEmailChangeResultDTO result =
                new CompleteCustomerEmailChangeResultDTO();
        result.setResultCode("OK");
        result.setLoginId("customer01");
        result.setEmailAddress("new@example.com");
        when(mapper.complete(any(), eq("customer01"), anyString()))
                .thenReturn(result);

        CustomerMyPageDTO updated = service.verify(
                "customer01",
                new CustomerEmailChangeVerifyRequestDTO(
                        UUID.randomUUID().toString(),
                        "123456"
                )
        );

        assertEquals("new@example.com", updated.getEmailAddress());
    }

    @Test
    void verifyRejectsInvalidOtp() {
        CompleteCustomerEmailChangeResultDTO result =
                new CompleteCustomerEmailChangeResultDTO();
        result.setResultCode("INVALID_CODE");
        when(mapper.complete(any(), anyString(), anyString()))
                .thenReturn(result);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.verify(
                        "customer01",
                        new CustomerEmailChangeVerifyRequestDTO(
                                UUID.randomUUID().toString(),
                                "123456"
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static BeginCustomerEmailChangeResultDTO beginResult(
            String code,
            String destination
    ) {
        BeginCustomerEmailChangeResultDTO result =
                new BeginCustomerEmailChangeResultDTO();
        result.setResultCode(code);
        result.setDestinationEmail(destination);
        return result;
    }

    private static PasswordRecoveryProperties properties() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        return new PasswordRecoveryProperties(
                true,
                Base64.getEncoder().encodeToString(key),
                300,
                60,
                5,
                600,
                true,
                24,
                "straffic_password_recovery",
                false,
                "Strict",
                "phone-channel",
                true,
                "easy-channel",
                true,
                true,
                "smtp.gmail.com",
                587,
                "sender@example.com",
                "app-password",
                "sender@example.com"
        );
    }
}
