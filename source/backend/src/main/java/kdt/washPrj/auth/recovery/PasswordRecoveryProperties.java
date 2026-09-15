package kdt.washPrj.auth.recovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

@Component
public class PasswordRecoveryProperties {

    private final boolean enabled;
    private final String masterKey;
    private final int challengeTtlSeconds;
    private final int resendSeconds;
    private final int maxAttempts;
    private final int resetTtlSeconds;
    private final boolean allowTestIdentity;
    private final int cleanupRetentionHours;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String phoneChannelKey;
    private final boolean phoneTestMode;
    private final String easyAuthChannelKey;
    private final boolean easyAuthTestMode;
    private final boolean emailEnabled;
    private final String emailSmtpHost;
    private final int emailSmtpPort;
    private final String emailUsername;
    private final String emailPassword;
    private final String emailFromAddress;

    public PasswordRecoveryProperties(
            @Value("${password-recovery.enabled:false}") boolean enabled,
            @Value("${password-recovery.master-key:}") String masterKey,
            @Value("${password-recovery.challenge-ttl-seconds:300}") int challengeTtlSeconds,
            @Value("${password-recovery.resend-seconds:60}") int resendSeconds,
            @Value("${password-recovery.max-attempts:5}") int maxAttempts,
            @Value("${password-recovery.reset-ttl-seconds:600}") int resetTtlSeconds,
            @Value("${password-recovery.allow-test-identity:false}") boolean allowTestIdentity,
            @Value("${password-recovery.cleanup-retention-hours:24}") int cleanupRetentionHours,
            @Value("${password-recovery.cookie.name:straffic_password_recovery}") String cookieName,
            @Value("${password-recovery.cookie.secure:false}") boolean cookieSecure,
            @Value("${password-recovery.cookie.same-site:Strict}") String cookieSameSite,
            @Value("${password-recovery.portone.phone-channel-key:}") String phoneChannelKey,
            @Value("${password-recovery.portone.phone-test-mode:true}") boolean phoneTestMode,
            @Value("${password-recovery.portone.easy-auth-channel-key:}") String easyAuthChannelKey,
            @Value("${password-recovery.portone.easy-auth-test-mode:true}") boolean easyAuthTestMode,
            @Value("${password-recovery.email.enabled:false}") boolean emailEnabled,
            @Value("${spring.mail.host:}") String emailSmtpHost,
            @Value("${spring.mail.port:0}") int emailSmtpPort,
            @Value("${spring.mail.username:}") String emailUsername,
            @Value("${spring.mail.password:}") String emailPassword,
            @Value("${password-recovery.email.from-address:}") String emailFromAddress
    ) {
        this.enabled = enabled;
        this.masterKey = trim(masterKey);
        this.challengeTtlSeconds = challengeTtlSeconds;
        this.resendSeconds = resendSeconds;
        this.maxAttempts = maxAttempts;
        this.resetTtlSeconds = resetTtlSeconds;
        this.allowTestIdentity = allowTestIdentity;
        this.cleanupRetentionHours = cleanupRetentionHours;
        this.cookieName = trim(cookieName);
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = trim(cookieSameSite);
        this.phoneChannelKey = trim(phoneChannelKey);
        this.phoneTestMode = phoneTestMode;
        this.easyAuthChannelKey = trim(easyAuthChannelKey);
        this.easyAuthTestMode = easyAuthTestMode;
        this.emailEnabled = emailEnabled;
        this.emailSmtpHost = trim(emailSmtpHost);
        this.emailSmtpPort = emailSmtpPort;
        this.emailUsername = trim(emailUsername);
        this.emailPassword = trim(emailPassword);
        this.emailFromAddress = trim(emailFromAddress);
    }

    public void requireReady() {
        if (!enabled) {
            throw unavailable("비밀번호 복구 기능이 비활성화되어 있습니다.");
        }
        if (cookieName.isBlank()
                || challengeTtlSeconds < 60
                || challengeTtlSeconds > 900
                || resendSeconds < 30
                || resendSeconds > challengeTtlSeconds
                || maxAttempts < 1
                || maxAttempts > 10
                || resetTtlSeconds < 60
                || resetTtlSeconds > 1200
                || cleanupRetentionHours < 1
                || cleanupRetentionHours > 720
                || canonicalCookieSameSite() == null
                || ("None".equals(canonicalCookieSameSite())
                && !cookieSecure)) {
            throw unavailable("비밀번호 복구 설정이 올바르지 않습니다.");
        }
        decodedMasterKey();
    }

    public void requireEmailReady() {
        requireReady();
        if (!emailEnabled
                || emailSmtpHost.isBlank()
                || emailSmtpPort < 1
                || emailSmtpPort > 65_535
                || emailUsername.isBlank()
                || emailPassword.isBlank()
                || emailFromAddress.isBlank()) {
            throw unavailable("이메일 인증 서비스가 설정되지 않았습니다.");
        }
    }

    public ChannelConfiguration requireIdentityChannel(
            PasswordRecoveryMethod method
    ) {
        requireReady();
        if (method == null || !method.isIdentityMethod()) {
            throw new IllegalArgumentException("본인인증 방법을 확인해주세요.");
        }

        String channelKey = method == PasswordRecoveryMethod.PHONE_IDENTITY
                ? phoneChannelKey
                : easyAuthChannelKey;
        boolean testMode = method == PasswordRecoveryMethod.PHONE_IDENTITY
                ? phoneTestMode
                : easyAuthTestMode;

        if (channelKey.isBlank()) {
            throw unavailable("선택한 본인인증 채널이 설정되지 않았습니다.");
        }
        if (testMode && !allowTestIdentity) {
            throw unavailable("테스트 본인인증은 비밀번호 복구에 사용할 수 없습니다.");
        }
        return new ChannelConfiguration(channelKey, testMode);
    }

    public byte[] decodedMasterKey() {
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("invalid key length");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw unavailable(
                    "PASSWORD_RECOVERY_MASTER_KEY must be a base64-encoded 32-byte key."
            );
        }
    }

    public int getChallengeTtlSeconds() {
        return challengeTtlSeconds;
    }

    public int getResendSeconds() {
        return resendSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getResetTtlSeconds() {
        return resetTtlSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCleanupRetentionHours() {
        return cleanupRetentionHours;
    }

    public String getCookieName() {
        return cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public String getCookieSameSite() {
        String canonical = canonicalCookieSameSite();
        if (canonical == null) {
            throw unavailable(
                    "PASSWORD_RECOVERY_COOKIE_SAME_SITE must be Strict, Lax, or None."
            );
        }
        if ("None".equals(canonical) && !cookieSecure) {
            throw unavailable(
                    "PASSWORD_RECOVERY_COOKIE_SAME_SITE=None requires PASSWORD_RECOVERY_COOKIE_SECURE=true."
            );
        }
        return canonical;
    }

    public String getEmailFromAddress() {
        return emailFromAddress;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String canonicalCookieSameSite() {
        if ("strict".equalsIgnoreCase(cookieSameSite)) {
            return "Strict";
        }
        if ("lax".equalsIgnoreCase(cookieSameSite)) {
            return "Lax";
        }
        if ("none".equalsIgnoreCase(cookieSameSite)) {
            return "None";
        }
        return null;
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                message
        );
    }

    public record ChannelConfiguration(
            String channelKey,
            boolean testMode
    ) {
    }
}
