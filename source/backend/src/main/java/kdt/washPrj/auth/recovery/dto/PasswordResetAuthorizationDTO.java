package kdt.washPrj.auth.recovery.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PasswordResetAuthorizationDTO {
    private boolean authorized;
    private OffsetDateTime expiresAt;
    private String method;
}
