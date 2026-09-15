package kdt.washPrj.nonMember;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kdt.washPrj.nonMember.dto.NonMemberAccessResponseDTO;
import kdt.washPrj.global.vehicle.PlateNumberNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

@Service
public class NonMemberAccessService {

    public static final String ACCESS_HEADER = "X-Non-Member-Access-Token";

    private static final String TOKEN_TYPE = "NON_MEMBER_ACCESS";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final NonMemberAccessMapper nonMemberAccessMapper;
    private final SecretKey secretKey;
    private final long expirationSeconds;

    public NonMemberAccessService(
            NonMemberAccessMapper nonMemberAccessMapper,
            @Value("${jwt.secret}") String secret,
            @Value("${app.non-member-access.expiration-seconds:900}") long expirationSeconds
    ) {
        this.nonMemberAccessMapper = nonMemberAccessMapper;
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationSeconds = Math.max(60, expirationSeconds);
    }

    public NonMemberAccessResponseDTO issueAccessToken(String plateNumber) {
        String normalizedPlateNumber = normalizePlateNumber(plateNumber);

        if (!nonMemberAccessMapper.existsNonMemberPlateNumber(normalizedPlateNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Non-member wash history was not found."
            );
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

        String accessToken = Jwts.builder()
                .subject(normalizedPlateNumber)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new NonMemberAccessResponseDTO(accessToken, expiresAt);
    }

    public void requirePlateAccess(String accessToken, String plateNumber) {
        String tokenPlateNumber = parsePlateNumber(accessToken);
        String requestedPlateNumber = normalizePlateNumber(plateNumber);

        if (!secureEquals(tokenPlateNumber, requestedPlateNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The access token does not match the plate number."
            );
        }
    }

    public void requireWashHistoryAccess(
            String accessToken,
            Long washHistoryId
    ) {
        if (washHistoryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "washHistoryId is required."
            );
        }

        String savedPlateNumber = nonMemberAccessMapper
                .selectNonMemberPlateNumber(washHistoryId);

        if (savedPlateNumber == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Non-member wash history was not found."
            );
        }

        requirePlateAccess(accessToken, savedPlateNumber);
    }

    private String parsePlateNumber(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw unauthorized();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            if (!TOKEN_TYPE.equals(
                    claims.get(TOKEN_TYPE_CLAIM, String.class)
            )) {
                throw unauthorized();
            }

            return normalizePlateNumber(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private String normalizePlateNumber(String plateNumber) {
        String normalizedPlateNumber = PlateNumberNormalizer.normalize(plateNumber);
        if (normalizedPlateNumber == null) {
            normalizedPlateNumber = "";
        }

        if (!normalizedPlateNumber.matches("^[0-9가-힣]{3,8}\\d{4}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid full plate number is required."
            );
        }

        return normalizedPlateNumber;
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "A valid non-member access token is required."
        );
    }
}
