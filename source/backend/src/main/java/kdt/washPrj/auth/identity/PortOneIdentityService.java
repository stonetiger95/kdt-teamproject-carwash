package kdt.washPrj.auth.identity;

import kdt.washPrj.auth.dto.IdentityVerificationCompleteRequestDTO;
import kdt.washPrj.auth.dto.IdentityVerificationCompleteResponseDTO;
import kdt.washPrj.auth.dto.IdentityVerificationStartResponseDTO;
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
import java.util.Locale;
import java.util.UUID;

@Service
public class PortOneIdentityService {

    public static final String PROVIDER = "PORTONE_V2";

    private final PortOneIdentityProperties properties;
    private final PortOneIdentityClient portOneIdentityClient;
    private final IdentityTokenCodec tokenCodec;
    private final Clock clock;

    @Autowired
    public PortOneIdentityService(
            PortOneIdentityProperties properties,
            PortOneIdentityClient portOneIdentityClient,
            IdentityTokenCodec tokenCodec
    ) {
        this(
                properties,
                portOneIdentityClient,
                tokenCodec,
                Clock.systemUTC()
        );
    }

    PortOneIdentityService(
            PortOneIdentityProperties properties,
            PortOneIdentityClient portOneIdentityClient,
            IdentityTokenCodec tokenCodec,
            Clock clock
    ) {
        this.properties = properties;
        this.portOneIdentityClient = portOneIdentityClient;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
    }

    public IdentityVerificationStartResponseDTO start() {
        properties.requireReady();

        String identityVerificationId = "iv"
                + UUID.randomUUID().toString().replace("-", "");
        IdentityTokenCodec.IssuedToken issuedState =
                tokenCodec.issueState(identityVerificationId);

        return new IdentityVerificationStartResponseDTO(
                identityVerificationId,
                issuedState.token(),
                properties.getStoreId(),
                properties.getChannelKey(),
                properties.isTestMode(),
                issuedState.expiresAt()
        );
    }

    public IdentityVerificationCompleteResponseDTO complete(
            IdentityVerificationCompleteRequestDTO request
    ) {
        properties.requireReady();
        if (request == null) {
            throw invalidVerification();
        }

        String identityVerificationId =
                request.getIdentityVerificationId();
        tokenCodec.validateState(
                identityVerificationId,
                request.getStateToken()
        );

        PortOneIdentityClient.PortOneVerification verification =
                portOneIdentityClient.getVerification(
                        identityVerificationId
                );
        validateEnvelope(identityVerificationId, verification);

        PortOneIdentityClient.VerifiedCustomer customer =
                verification.verifiedCustomer();
        if (customer == null) {
            throw invalidVerification();
        }

        String name = normalizeName(customer.name());
        LocalDate birthDate = parseBirthDate(customer.birthDate());
        String phone = normalizePhone(customer.phoneNumber());
        String gender = normalizeGender(customer.gender());
        String diHmac = resolveDiHmac(
                verification,
                customer,
                birthDate,
                phone
        );
        OffsetDateTime verifiedAt = parseVerifiedAt(
                verification.verifiedAt()
        );
        OffsetDateTime proofExpiresAt = OffsetDateTime.ofInstant(
                clock.instant().plusSeconds(
                        properties.getProofTtlSeconds()
                ),
                ZoneOffset.UTC
        );

        VerifiedIdentityClaims claims = new VerifiedIdentityClaims(
                identityVerificationId,
                name,
                birthDate,
                phone,
                gender,
                diHmac,
                tokenCodec.bindStateToken(request.getStateToken()),
                verification.channel().type(),
                verifiedAt,
                proofExpiresAt
        );
        String identityProof = tokenCodec.issueProof(claims);

        return new IdentityVerificationCompleteResponseDTO(
                identityVerificationId,
                identityProof,
                name,
                phone,
                "U".equals(gender) ? null : gender,
                "U".equals(gender),
                properties.isTestMode(),
                proofExpiresAt
        );
    }

    public VerifiedIdentityClaims readSignupProof(
            String identityVerificationId,
            String identityProof,
            String stateToken
    ) {
        properties.requireReady();
        return tokenCodec.readProof(
                identityVerificationId,
                identityProof,
                stateToken
        );
    }

    private void validateEnvelope(
            String expectedId,
            PortOneIdentityClient.PortOneVerification verification
    ) {
        if (!"VERIFIED".equals(verification.status())
                || !expectedId.equals(verification.id())
                || !"V2".equals(verification.version())
                || verification.channel() == null
                || !sameValue(
                properties.getChannelKey(),
                verification.channel().key()
        )) {
            throw invalidVerification();
        }

        String channelType = verification.channel().type();
        String expectedChannelType = properties.isTestMode()
                ? "TEST"
                : "LIVE";
        if (!expectedChannelType.equals(channelType)
                || (properties.isRequireLive()
                && !"LIVE".equals(channelType))) {
            throw invalidVerification();
        }
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 50) {
            throw invalidVerification();
        }
        return normalized;
    }

    private LocalDate parseBirthDate(String value) {
        try {
            LocalDate birthDate = LocalDate.parse(value);
            if (!birthDate.isBefore(LocalDate.now(clock))) {
                throw invalidVerification();
            }
            return birthDate;
        } catch (DateTimeException | NullPointerException exception) {
            throw invalidVerification();
        }
    }

    private String normalizePhone(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("[^0-9]", "");
        if (!normalized.matches("^01[016789][0-9]{7,8}$")) {
            throw invalidVerification();
        }
        return normalized;
    }

    private String normalizeGender(String value) {
        if (value == null || value.isBlank()) {
            // PASS does not always return gender. U is an encrypted-proof
            // sentinel and is never persisted to public.customer.
            return "U";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "MALE" -> "M";
            case "FEMALE" -> "F";
            case "OTHER" -> "N";
            default -> throw invalidVerification();
        };
    }

    private String resolveDiHmac(
            PortOneIdentityClient.PortOneVerification verification,
            PortOneIdentityClient.VerifiedCustomer customer,
            LocalDate birthDate,
            String phone
    ) {
        if (customer.di() != null && !customer.di().isBlank()) {
            return tokenCodec.hmacDi(customer.di());
        }

        if (!properties.isTestMode()
                || verification.channel() == null
                || !"TEST".equals(verification.channel().type())) {
            return tokenCodec.hmacDi(customer.di());
        }

        String testIdentity = customer.ci() != null
                && !customer.ci().isBlank()
                ? "TEST_CI:" + customer.ci()
                : "TEST_CUSTOMER:" + birthDate + ":" + phone;
        return tokenCodec.hmacDi(testIdentity);
    }

    private OffsetDateTime parseVerifiedAt(String value) {
        try {
            Instant instant = Instant.from(
                    DateTimeFormatter.ISO_DATE_TIME.parse(value)
            );
            if (instant.isAfter(clock.instant().plusSeconds(60))) {
                throw invalidVerification();
            }
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeException | NullPointerException exception) {
            throw invalidVerification();
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

    private static ResponseStatusException invalidVerification() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "완료된 본인인증 결과를 확인할 수 없습니다."
        );
    }
}
