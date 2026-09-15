package kdt.washPrj.inquiry;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

public class AuthRoleContext {

    private AuthRoleContext() {
    }

    public static String getLoginId() {
        Authentication authentication = getAuthentication();
        return authentication.getName();
    }

    public static String getInquiryRole() {
        Authentication authentication = getAuthentication();

        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(AuthRoleContext::toInquiryRole)
                .filter(role -> !role.isBlank())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private static Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return authentication;
    }

    private static String toInquiryRole(String role) {
        if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) {
            return "ADMIN";
        }

        if ("ROLE_OWNER".equals(role) || "OWNER".equals(role)) {
            return "OWNER";
        }

        if ("ROLE_CUSTOMER".equals(role) || "CUSTOMER".equals(role)) {
            return "CUSTOMER";
        }

        return "";
    }

}
