package kdt.washPrj.auth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NaverOAuthException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public NaverOAuthException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
