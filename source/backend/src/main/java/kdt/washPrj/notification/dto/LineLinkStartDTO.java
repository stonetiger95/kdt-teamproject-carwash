package kdt.washPrj.notification.dto;

public record LineLinkStartDTO(
        String command,
        String officialAccountUrl,
        long expiresInSeconds
) {
}
