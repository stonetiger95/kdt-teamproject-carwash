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
public class CustomerEmailChangeService {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerEmailChangeService.class);

    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryCrypto crypto;
    private final RecoveryEmailSender emailSender;
    private final CustomerEmailChangeMapper mapper;
    private final Clock clock;
    private final Executor executor;

    @Autowired
    public CustomerEmailChangeService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            CustomerEmailChangeMapper mapper,
            @Qualifier("passwordRecoveryExecutor") Executor executor
    ) {
        this(
                properties,
                crypto,
                emailSender,
                mapper,
                Clock.systemUTC(),
                executor
        );
    }

    CustomerEmailChangeService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            CustomerEmailChangeMapper mapper
    ) {
        this(
                properties,
                crypto,
                emailSender,
                mapper,
                Clock.systemUTC(),
                Runnable::run
        );
    }

    CustomerEmailChangeService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryCrypto crypto,
            RecoveryEmailSender emailSender,
            CustomerEmailChangeMapper mapper,
            Clock clock,
            Executor executor
    ) {
        this.properties = properties;
        this.crypto = crypto;
        this.emailSender = emailSender;
        this.mapper = mapper;
        this.clock = clock;
        this.executor = executor;
    }

    public CustomerEmailChangeStartResponseDTO start(
            String loginId,
            CustomerEmailChangeStartRequestDTO request
    ) {
        properties.requireEmailReady();
        if (request == null) {
            throw badRequest("새 이메일을 입력해주세요.");
        }

        UUID challengeId = UUID.randomUUID();
        String code = crypto.generateOtp();
        String otpHmac = crypto.hmacProfileEmailOtp(challengeId, code);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                clock.instant().plusSeconds(
                        properties.getChallengeTtlSeconds()
                ),
                ZoneOffset.UTC
        );

        BeginCustomerEmailChangeResultDTO result = mapper.begin(
                challengeId,
                normalizeLoginId(loginId),
                normalizeEmail(request.emailAddress()),
                otpHmac,
                expiresAt,
                properties.getResendSeconds(),
                properties.getMaxAttempts()
        );

        String resultCode = result == null ? null : result.getResultCode();
        if ("UNCHANGED".equals(resultCode)) {
            return new CustomerEmailChangeStartResponseDTO(
                    null,
                    0,
                    true,
                    "현재 이메일과 비밀번호 복구 이메일이 이미 일치합니다."
            );
        }
        if ("RATE_LIMITED".equals(resultCode)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호 재발송 가능 시간까지 기다려주세요."
            );
        }
        if ("EMAIL_IN_USE".equals(resultCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 다른 계정에서 사용 중인 이메일입니다."
            );
        }
        if ("ACCOUNT_NOT_FOUND".equals(resultCode)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "고객 계정을 찾을 수 없습니다."
            );
        }
        if (!"ACCEPTED".equals(resultCode)
                || result.getDestinationEmail() == null
                || result.getDestinationEmail().isBlank()) {
            throw badRequest("이메일 변경 요청을 처리할 수 없습니다.");
        }

        scheduleDelivery(
                challengeId,
                result.getDestinationEmail(),
                code
        );

        return new CustomerEmailChangeStartResponseDTO(
                challengeId.toString(),
                properties.getResendSeconds(),
                false,
                "새 이메일로 인증번호를 발송했습니다."
        );
    }

    public CustomerMyPageDTO verify(
            String loginId,
            CustomerEmailChangeVerifyRequestDTO request
    ) {
        properties.requireEmailReady();
        if (request == null) {
            throw invalidCode();
        }

        UUID challengeId = parseUuid(request.challengeId());
        CompleteCustomerEmailChangeResultDTO result = mapper.complete(
                challengeId,
                normalizeLoginId(loginId),
                crypto.hmacProfileEmailOtp(challengeId, request.code())
        );

        String resultCode = result == null ? null : result.getResultCode();
        if ("OK".equals(resultCode)) {
            return result.toCustomerMyPage();
        }
        if ("EMAIL_IN_USE".equals(resultCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 다른 계정에서 사용 중인 이메일입니다."
            );
        }
        if ("LOCKED".equals(resultCode)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호 입력 횟수를 초과했습니다. 새 인증번호를 요청해주세요."
            );
        }
        if ("EXPIRED".equals(resultCode)) {
            throw badRequest("인증번호가 만료되었습니다. 새 인증번호를 요청해주세요.");
        }
        throw invalidCode();
    }

    private void scheduleDelivery(
            UUID challengeId,
            String destination,
            String code
    ) {
        try {
            executor.execute(() -> submitEmail(
                    challengeId,
                    destination,
                    code
            ));
        } catch (RuntimeException exception) {
            markDelivery(challengeId, "FAILED");
            log.warn(
                    "Customer email-change delivery could not be scheduled: challengeId={}",
                    challengeId
            );
        }
    }

    private void submitEmail(
            UUID challengeId,
            String destination,
            String code
    ) {
        try {
            emailSender.sendEmailChangeVerificationCode(destination, code)
                    .whenCompleteAsync(
                            (ignored, error) -> {
                                if (error == null) {
                                    markDelivery(challengeId, "SENT");
                                } else {
                                    markDelivery(challengeId, "FAILED");
                                    logDeliveryFailure(challengeId);
                                }
                            },
                            executor
                    );
        } catch (RuntimeException exception) {
            markDelivery(challengeId, "FAILED");
            logDeliveryFailure(challengeId);
        }
    }

    private void markDelivery(UUID challengeId, String status) {
        try {
            mapper.markDelivery(challengeId, status);
        } catch (RuntimeException exception) {
            log.warn(
                    "Customer email-change delivery status could not be stored: challengeId={}",
                    challengeId
            );
        }
    }

    private void logDeliveryFailure(UUID challengeId) {
        log.warn(
                "Customer email-change delivery failed: challengeId={}",
                challengeId
        );
    }

    private static String normalizeLoginId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("^[A-Za-z0-9]+$")
                || normalized.length() > 50) {
            throw badRequest("아이디 형식을 확인해주세요.");
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
            throw badRequest("이메일 형식을 확인해주세요.");
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

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException invalidCode() {
        return badRequest("인증번호가 유효하지 않습니다.");
    }
}
