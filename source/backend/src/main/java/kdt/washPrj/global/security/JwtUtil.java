package kdt.washPrj.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

    }

    /*
     * JWT 생성
     */
    public String createToken(
            String loginId,
            String role,
            long credentialVersion
    ) {

        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + Duration.ofHours(1).toMillis()
        );

        return Jwts.builder()
                .subject(loginId)
                .claim("role", role)
                .claim("credentialVersion", credentialVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();

    }

    /**
     * Claims 추출
     */
    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰 유효성 검사
     */
    public boolean validateToken(String token) {

        try {

            getClaims(token);

            return true;

        } catch (Exception e) {

            return false;

        }

    }

    /**
     * 로그인 아이디 추출
     */
    public String getUsername(String token) {

        return getClaims(token).getSubject();

    }

    /**
     * 권한 추출
     */
    public String getRole(String token) {

        return getClaims(token).get("role", String.class);

    }

    public Long getCredentialVersion(String token) {
        Object value = getClaims(token).get("credentialVersion");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

}
