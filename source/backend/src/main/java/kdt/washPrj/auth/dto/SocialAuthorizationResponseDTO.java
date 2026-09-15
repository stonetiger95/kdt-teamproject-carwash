package kdt.washPrj.auth.dto;

public record SocialAuthorizationResponseDTO(
        String authorizationUrl,
        String state,
        long expiresInSeconds
) {
}
