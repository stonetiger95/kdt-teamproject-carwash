package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.identity.IdentityTokenCodec;
import kdt.washPrj.auth.identity.PortOneIdentityClient;
import kdt.washPrj.auth.identity.PortOneIdentityProperties;
import kdt.washPrj.auth.recovery.dto.AccountIdIdentityCompleteRequestDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdIdentityStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdRecoveryResponseDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryStartResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PortOneAccountIdRecoveryService {

    private final PasswordRecoveryProperties recoveryProperties;
    private final PortOneIdentityProperties identityProperties;
    private final AccountIdRecoveryMapper mapper;
    private final AccountIdRecoveryStateCodec stateCodec;
    private final PasswordRecoveryCrypto crypto;
    private final PortOneIdentityClient portOneClient;
    private final IdentityTokenCodec identityTokenCodec;
    private final Clock clock;

    @Autowired
    public PortOneAccountIdRecoveryService(
            PasswordRecoveryProperties recoveryProperties,
            PortOneIdentityProperties identityProperties,
            AccountIdRecoveryMapper mapper,
            AccountIdRecoveryStateCodec stateCodec,
            PasswordRecoveryCrypto crypto,
            PortOneIdentityClient portOneClient,
            IdentityTokenCodec identityTokenCodec
    ) {
        this(
                recoveryProperties,
                identityProperties,
                mapper,
                stateCodec,
                crypto,
                portOneClient,
                identityTokenCodec,
                Clock.systemUTC()
        );
    }

    PortOneAccountIdRecoveryService(
            PasswordRecoveryProperties recoveryProperties,
            PortOneIdentityProperties identityProperties,
            AccountIdRecoveryMapper mapper,
            AccountIdRecoveryStateCodec stateCodec,
            PasswordRecoveryCrypto crypto,
            PortOneIdentityClient portOneClient,
            IdentityTokenCodec identityTokenCodec,
            Clock clock
    ) {
        this.recoveryProperties = recoveryProperties;
        this.identityProperties = identityProperties;
        this.mapper = mapper;
        this.stateCodec = stateCodec;
        this.crypto = crypto;
        this.portOneClient = portOneClient;
        this.identityTokenCodec = identityTokenCodec;
        this.clock = clock;
    }

    public IdentityRecoveryStartResponseDTO start(
            AccountIdIdentityStartRequestDTO request
    ) {
        PasswordRecoveryMethod method = requireIdentityMethod(
                request == null ? null : request.method()
        );
        PasswordRecoveryProperties.ChannelConfiguration channel =
                recoveryProperties.requireIdentityChannel(method);
        identityProperties.requireProviderReady();

        UUID recoveryId = UUID.randomUUID();
        String identityVerificationId = "iv"
                + UUID.randomUUID().toString().replace("-", "");
        AccountIdRecoveryStateCodec.IssuedState state = stateCodec.issue(
                recoveryId,
                method,
                identityVerificationId
        );

        BeginPasswordRecoveryResultDTO result = mapper.beginIdentity(
                recoveryId,
                method.name(),
                crypto.hmacAccountIdSubject(identityVerificationId),
                state.expiresAt(),
                recoveryProperties.getResendSeconds(),
                recoveryProperties.getMaxAttempts()
        );
        requireAccepted(result);

        return new IdentityRecoveryStartResponseDTO(
                recoveryId.toString(),
                identityVerificationId,
                state.token(),
                identityProperties.getStoreId(),
                channel.channelKey(),
                channel.testMode(),
                state.expiresAt()
        );
    }

    public AccountIdRecoveryResponseDTO complete(
            AccountIdIdentityCompleteRequestDTO request
    ) {
        if (request == null) {
            throw invalidIdentityResult();
        }

        UUID recoveryId = parseUuid(request.recoveryId());
        PasswordRecoveryMethod method = requireIdentityMethod(
                request.method()
        );
        PasswordRecoveryProperties.ChannelConfiguration channel =
                recoveryProperties.requireIdentityChannel(method);
        identityProperties.requireProviderReady();

        stateCodec.validate(
                recoveryId,
                method,
                request.identityVerificationId(),
                request.stateToken()
        );

        PortOneIdentityClient.PortOneVerification verification =
                portOneClient.getVerification(
                        request.identityVerificationId()
                );
        validateEnvelope(
                request.identityVerificationId(),
                channel,
                verification
        );

        PortOneIdentityClient.VerifiedCustomer customer =
                verification.verifiedCustomer();
        if (customer == null) {
            throw invalidIdentityResult();
        }

        String name = normalizeName(customer.name());
        LocalDate birthDate = parseBirthDate(customer.birthDate());
        String phone = normalizePhone(customer.phoneNumber());
        String diHmac = customer.di() == null || customer.di().isBlank()
                ? ""
                : identityTokenCodec.hmacDi(customer.di());
        String ciHmac = identityTokenCodec.hmacCi(customer.ci());
        OffsetDateTime verifiedAt = parseVerifiedAt(
                verification.verifiedAt()
        );

        AccountIdRecoveryResultDTO result = mapper.completeIdentity(
                recoveryId,
                method.name(),
                request.identityVerificationId(),
                channel.testMode() ? "TEST" : "LIVE",
                name,
                birthDate,
                phone,
                diHmac,
                ciHmac,
                verifiedAt
        );
        requireRecovered(result);

        return new AccountIdRecoveryResponseDTO(
                result.getLoginId(),
                method.name(),
                channel.testMode()
        );
    }

    private void validateEnvelope(
            String expectedId,
            PasswordRecoveryProperties.ChannelConfiguration channel,
            PortOneIdentityClient.PortOneVerification verification
    ) {
        if (verification == null
                || !"VERIFIED".equals(verification.status())
                || !expectedId.equals(verification.id())
                || !"V2".equals(verification.version())
                || verification.channel() == null
                || !sameValue(
                channel.channelKey(),
                verification.channel().key()
        )) {
            throw invalidIdentityResult();
        }

        String expectedMode = channel.testMode() ? "TEST" : "LIVE";
        if (!expectedMode.equals(verification.channel().type())) {
            throw invalidIdentityResult();
        }
    }

    private static PasswordRecoveryMethod requireIdentityMethod(
            PasswordRecoveryMethod method
    ) {
        if (method == null || !method.isIdentityMethod()) {
            throw new IllegalArgumentException("본인인증 방법을 확인해주세요.");
        }
        return method;
    }

    private static String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 50) {
            throw invalidIdentityResult();
        }
        return normalized;
    }

    private LocalDate parseBirthDate(String value) {
        try {
            LocalDate birthDate = LocalDate.parse(value);
            if (!birthDate.isBefore(LocalDate.now(clock))) {
                throw invalidIdentityResult();
            }
            return birthDate;
        } catch (DateTimeException | NullPointerException exception) {
            throw invalidIdentityResult();
        }
    }

    private static String normalizePhone(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("[^0-9]", "");
        if (!normalized.matches("^01[016789][0-9]{7,8}$")) {
            throw invalidIdentityResult();
        }
        return normalized;
    }

    private OffsetDateTime parseVerifiedAt(String value) {
        try {
            Instant instant = Instant.from(
                    DateTimeFormatter.ISO_DATE_TIME.parse(value)
            );
            Instant now = clock.instant();
            if (instant.isAfter(now.plusSeconds(60))
                    || instant.isBefore(now.minusSeconds(
                    recoveryProperties.getChallengeTtlSeconds() + 60L
            ))) {
                throw invalidIdentityResult();
            }
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeException | NullPointerException exception) {
            throw invalidIdentityResult();
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "아이디 찾기 요청번호 형식을 확인해주세요."
            );
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
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "본인인증 결과와 일치하는 계정을 확인할 수 없습니다."
            );
        }
    }

    private static boolean sameValue(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ResponseStatusException invalidIdentityResult() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "완료된 본인인증 결과를 확인할 수 없습니다."
        );
    }
}
