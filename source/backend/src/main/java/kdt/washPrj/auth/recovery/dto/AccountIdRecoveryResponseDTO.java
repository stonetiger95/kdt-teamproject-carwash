package kdt.washPrj.auth.recovery.dto;

public record AccountIdRecoveryResponseDTO(
        String loginId,
        String method,
        boolean testMode
) {
}
