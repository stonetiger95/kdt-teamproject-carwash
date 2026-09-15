package kdt.washPrj.auth.social;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Getter
@Component
public class SocialLoginProperties {

    private final String masterKey;
    private final long stateTtlSeconds;
    private final long registrationTtlSeconds;

    public SocialLoginProperties(
            @Value("${social-login.master-key:}") String masterKey,
            @Value("${social-login.state-ttl-seconds:300}") long stateTtlSeconds,
            @Value("${social-login.registration-ttl-seconds:600}") long registrationTtlSeconds
    ) {
        this.masterKey = masterKey == null ? "" : masterKey.trim();
        this.stateTtlSeconds = stateTtlSeconds;
        this.registrationTtlSeconds = registrationTtlSeconds;
    }

    public byte[] decodedMasterKey() {
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length < 32) {
                throw notConfigured();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw notConfigured();
        }
    }

    public void requireReady() {
        if (stateTtlSeconds < 60
                || stateTtlSeconds > 900
                || registrationTtlSeconds < 60
                || registrationTtlSeconds > 1800) {
            throw notConfigured();
        }
        decodedMasterKey();
    }

    public boolean isReady() {
        try {
            requireReady();
            return true;
        } catch (SocialOAuthException exception) {
            return false;
        }
    }

    private SocialOAuthException notConfigured() {
        return new SocialOAuthException(
                "SOCIAL_LOGIN_NOT_CONFIGURED",
                "소셜 로그인 암호화 환경변수가 설정되지 않았습니다.",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
