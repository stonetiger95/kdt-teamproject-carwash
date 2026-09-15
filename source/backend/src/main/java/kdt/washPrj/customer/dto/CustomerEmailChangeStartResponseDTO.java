package kdt.washPrj.customer.dto;

public record CustomerEmailChangeStartResponseDTO(
        String challengeId,
        int resendAfterSeconds,
        boolean alreadySynchronized,
        String message
) {
}
