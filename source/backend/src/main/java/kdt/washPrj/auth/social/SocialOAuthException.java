package kdt.washPrj.auth.social;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SocialOAuthException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public SocialOAuthException(
            String code,
            String message,
            HttpStatus status
    ) {
        super(message);
        this.code = code;
        this.status = status;
    }

    static SocialOAuthException unsupportedProvider() {
        return new SocialOAuthException(
                "SOCIAL_PROVIDER_UNSUPPORTED",
                "지원하지 않는 소셜 로그인 제공자입니다.",
                HttpStatus.BAD_REQUEST
        );
    }
}
