package kdt.washPrj.auth.dto;

public record NaverAuthorizationResponseDTO(
        String authorizationUrl,
        long expiresInSeconds
) {
}
