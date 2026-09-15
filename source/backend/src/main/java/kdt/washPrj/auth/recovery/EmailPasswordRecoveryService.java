package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartResponseDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryVerifyRequestDTO;
import kdt.washPrj.auth.recovery.dto.PasswordRecoverySessionResponseDTO;
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
public class EmailPasswordRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(EmailPasswordRecoveryService.class);
    private static final String GENERIC_MESSAGE =
            "입력한 정보와 일치하는 계정이 있으면 인증 메일을 발송했습니다.";

    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryMapper mapper;
    private final PasswordRecoveryCrypto crypto;
    private final RecoveryEmailSender emailSender;
    private final Clock clock;
    private final Executor recoveryExecutor;

    @Autowired
    public EmailPasswordRecoveryService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryMapper mapper,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            @Qualifier("passwordRecoveryExecutor") Executor recoveryExecutor
    ) {
        this(
                properties,
                mapper,
                crypto,
                emailSender,
                Clock.systemUTC(),
                recoveryExecutor
        );
    }

    EmailPasswordRecoveryService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryMapper mapper,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender
    ) {
        this(
                properties,
                mapper,
                crypto,
                emailSender,
                Clock.systemUTC(),
                Runnable::run
        );
    }

    EmailPasswordRecoveryService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryMapper mapper,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            Clock clock,
            Executor recoveryExecutor
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.crypto = crypto;
        this.emailSender = emailSender;
        this.clock = clock;
        this.recoveryExecutor = recoveryExecutor;
    }

    public EmailRecoveryStartResponseDTO start(
            EmailRecoveryStartRequestDTO request
    ) {
        properties.requireEmailReady();
        if (request == null) {
            throw new IllegalArgumentException("가입 정보를 입력해주세요.");
        }

        UUID challengeId = UUID.randomUUID();
        String code = crypto.generateOtp();
        String codeHmac = crypto.hmacOtp(challengeId, code);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                clock.instant().plusSeconds(
                        properties.getChallengeTtlSeconds()
                ),
                ZoneOffset.UTC
        );

        String loginId = normalizeLoginId(request.loginId());
        BeginPasswordRecoveryResultDTO result = mapper.begin(
                challengeId,
                loginId,
                normalizeEmail(request.emailAddress()),
                PasswordRecoveryMethod.EMAIL_OTP.name(),
                crypto.hmacSubject(loginId),
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
                    () -> markDeliveryFailed(challengeId)
            );
        }

        return new EmailRecoveryStartResponseDTO(
                challengeId.toString(),
                properties.getResendSeconds(),
                GENERIC_MESSAGE
        );
    }

    public IssuedPasswordRecoverySession verify(
            EmailRecoveryVerifyRequestDTO request
    ) {
        properties.requireEmailReady();
        if (request == null) {
            throw invalidCode();
        }

        UUID challengeId = parseUuid(request.challengeId());
        String codeHmac = crypto.hmacOtp(challengeId, request.code());
        String rawResetToken = crypto.generateResetToken();
        String resetTokenHmac = crypto.hmacResetToken(rawResetToken);
        OffsetDateTime resetExpiresAt = OffsetDateTime.ofInstant(
                clock.instant().plusSeconds(
                        properties.getResetTtlSeconds()
                ),
                ZoneOffset.UTC
        );

        String result = mapper.verifyEmail(
                challengeId,
                codeHmac,
                resetTokenHmac,
                resetExpiresAt
        );
        if (!"OK".equals(result)) {
            throw invalidCode();
        }

        return new IssuedPasswordRecoverySession(
                rawResetToken,
                new PasswordRecoverySessionResponseDTO(
                        true,
                        resetExpiresAt,
                        PasswordRecoveryMethod.EMAIL_OTP.name()
                )
        );
    }

    private static String normalizeLoginId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("^[A-Za-z0-9]+$")
                || normalized.length() > 50) {
            throw new IllegalArgumentException("아이디 형식을 확인해주세요.");
        }
        return normalized;
    }

    private static String normalizeEmail(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()
                || normalized.length() > 100
                || !normalized.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
        )) {
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

    private static void requireAccepted(
            BeginPasswordRecoveryResultDTO result
    ) {
        if (result == null || !"ACCEPTED".equals(result.getResultCode())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "잠시 후 다시 시도해주세요."
            );
        }
    }

    private static ResponseStatusException invalidCode() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "인증번호가 유효하지 않거나 만료되었습니다."
        );
    }

    private void markDeliveryFailed(UUID challengeId) {
        markDelivery(challengeId, "FAILED");
    }

    private void submitEmail(
            UUID challengeId,
            String destination,
            String code
    ) {
        try {
            emailSender.sendVerificationCode(destination, code)
                    .whenCompleteAsync(
                            (ignored, error) -> {
                                if (error == null) {
                                    markDelivery(challengeId, "SENT");
                                } else {
                                    markDeliveryFailed(challengeId);
                                    logDeliveryFailure(challengeId);
                                }
                            },
                            recoveryExecutor
                    );
        } catch (RuntimeException exception) {
            // The OTP and address are never logged.
            markDeliveryFailed(challengeId);
            logDeliveryFailure(challengeId);
        }
    }

    private void scheduleDelivery(UUID challengeId, Runnable operation) {
        try {
            recoveryExecutor.execute(operation);
        } catch (RuntimeException exception) {
            markDeliveryFailed(challengeId);
            logDeliveryFailure(challengeId);
        }
    }

    private static void logDeliveryFailure(UUID challengeId) {
        log.warn(
                "Password recovery email delivery failed for challenge {}",
                challengeId
        );
    }

    private void markDelivery(UUID challengeId, String status) {
        try {
            mapper.markEmailDelivery(challengeId, status);
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not record password recovery email delivery state for challenge {}",
                    challengeId
            );
        }
    }
}
