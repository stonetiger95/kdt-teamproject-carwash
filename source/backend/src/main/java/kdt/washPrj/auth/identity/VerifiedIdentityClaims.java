package kdt.washPrj.auth.identity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record VerifiedIdentityClaims(
        String identityVerificationId,
        String name,
        LocalDate birthDate,
        String phone,
        String gender,
        String diHmac,
        String stateBinding,
        String verificationMode,
        OffsetDateTime verifiedAt,
        OffsetDateTime proofExpiresAt
) {
}
