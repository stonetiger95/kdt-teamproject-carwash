package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.AccountIdEmailStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdRecoveryResponseDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartResponseDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryVerifyRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class EmailAccountIdRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(
            EmailAccountIdRecoveryService.class
    );
    private static final String GENERIC_MESSAGE =
            "입력한 이메일과 일치하는 계정이 있으면 인증 메일을 발송했습니다.";

    private final PasswordRecoveryProperties properties;
    private final AccountIdRecoveryMapper mapper;
    private final PasswordRecoveryCrypto crypto;
    private final RecoveryEmailSender emailSender;
    private final Executor recoveryExecutor;
    private final Clock clock;

    @Autowired
    public EmailAccountIdRecoveryService(
            PasswordRecoveryProperties properties,
            AccountIdRecoveryMapper mapper,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            @Qualifier("passwordRecoveryExecutor") Executor recoveryExecutor
    ) {
        this(
                properties,
                mapper,
                crypto,
                emailSender,
                recoveryExecutor,
                Clock.systemUTC()
        );
    }

    EmailAccountIdRecoveryService(
            PasswordRecoveryProperties properties,
            AccountIdRecoveryMapper mapper,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            Executor recoveryExecutor,
            Clock clock
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.crypto = crypto;
        this.emailSender = emailSender;
        this.recoveryExecutor = recoveryExecutor;
        this.clock = clock;
    }

    public EmailRecoveryStartResponseDTO start(
            AccountIdEmailStartRequestDTO request
    ) {
        properties.requireEmailReady();
        if (request == null) {
            throw new IllegalArgumentException("가입 이메일을 입력해주세요.");
        }

        String emailAddress = normalizeEmail(request.emailAddress());
        UUID challengeId = UUID.randomUUID();
        String code = crypto.generateOtp();
        String codeHmac = crypto.hmacAccountIdOtp(challengeId, code);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                clock.instant().plusSeconds(
                        properties.getChallengeTtlSeconds()
                ),
                ZoneOffset.UTC
        );

        BeginPasswordRecoveryResultDTO result = mapper.beginEmail(
                challengeId,
                emailAddress,
                crypto.hmacAccountIdSubject(emailAddress),
                codeHmac,
                expiresAt,
                properties.getResendSeconds(),
                properties.getMaxAttempts()
        );
        requireAccepted(result);

        String destination = result.getDestinationEmail();
        if (destination != null && !destination.isBlank()) {
            scheduleDelivery(
                    challengeId,
                    () -> submitEmail(challengeId, destination, code)
            );
        } else {
            scheduleDelivery(
                    challengeId,
                    () -> markDelivery(challengeId, "FAILED")
            );
        }

        return new EmailRecoveryStartResponseDTO(
                challengeId.toString(),
                properties.getResendSeconds(),
                GENERIC_MESSAGE
        );
    }

    public AccountIdRecoveryResponseDTO verify(
            EmailRecoveryVerifyRequestDTO request
    ) {
        properties.requireEmailReady();
        if (request == null) {
            throw invalidCode();
        }

        UUID challengeId = parseUuid(request.challengeId());
        AccountIdRecoveryResultDTO result = mapper.verifyEmail(
                challengeId,
                crypto.hmacAccountIdOtp(challengeId, request.code())
        );
        requireRecovered(result);

        return new AccountIdRecoveryResponseDTO(
                result.getLoginId(),
                PasswordRecoveryMethod.EMAIL_OTP.name(),
                false
        );
    }

    private void submitEmail(
            UUID challengeId,
            String destination,
            String code
    ) {
        try {
            emailSender.sendAccountIdVerificationCode(destination, code)
                    .whenCompleteAsync(
                            (ignored, error) -> {
                                if (error == null) {
                                    markDelivery(challengeId, "SENT");
                                } else {
                                    markDelivery(challengeId, "FAILED");
                                    logDeliveryFailure(challengeId);
                                }
                            },
                            recoveryExecutor
                    );
        } catch (RuntimeException exception) {
            markDelivery(challengeId, "FAILED");
            logDeliveryFailure(challengeId);
        }
    }

    private void scheduleDelivery(UUID challengeId, Runnable operation) {
        try {
            recoveryExecutor.execute(operation);
        } catch (RuntimeException exception) {
            markDelivery(challengeId, "FAILED");
            logDeliveryFailure(challengeId);
        }
    }

    private void markDelivery(UUID challengeId, String status) {
        try {
            mapper.markEmailDelivery(challengeId, status);
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not record account ID recovery delivery state for challenge {}",
                    challengeId
            );
        }
    }

    private static void logDeliveryFailure(UUID challengeId) {
        log.warn(
                "Account ID recovery email delivery failed for challenge {}",
                challengeId
        );
    }

    private static String normalizeEmail(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()
                || normalized.length() > 100
                || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식을 확인해주세요.");
        }
        return normalized;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidCode();
        }
    }

    private static void requireAccepted(BeginPasswordRecoveryResultDTO result) {
        if (result == null || !"ACCEPTED".equals(result.getResultCode())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "잠시 후 다시 시도해주세요."
            );
        }
    }

    private static void requireRecovered(AccountIdRecoveryResultDTO result) {
        if (result == null
                || !"OK".equals(result.getResultCode())
                || result.getLoginId() == null
                || !result.getLoginId().matches("^[A-Za-z0-9]{1,50}$")) {
            throw invalidCode();
        }
    }

    private static ResponseStatusException invalidCode() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "인증번호가 올바르지 않거나 만료되었습니다."
        );
    }
}
