package kdt.washPrj.auth.dto;

import java.time.OffsetDateTime;

public record IdentityVerificationCompleteResponseDTO(
        String identityVerificationId,
        String identityProof,
        String name,
        String phoneNumber,
        String verifiedGender,
        boolean genderSelectionRequired,
        boolean testMode,
        OffsetDateTime expiresAt
) {
}
