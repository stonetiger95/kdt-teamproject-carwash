package kdt.washPrj.chatbot;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record ChatbotUserContext(
        String loginId,
        String role,
        boolean authenticated
) {

    public static ChatbotUserContext anonymous() {
        return new ChatbotUserContext(null, null, false);
    }

    public static ChatbotUserContext from(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return anonymous();
        }

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
        return new ChatbotUserContext(authentication.getName(), role, true);
    }

    public boolean isCustomer() {
        return authenticated && "ROLE_CUSTOMER".equals(role);
    }
}
