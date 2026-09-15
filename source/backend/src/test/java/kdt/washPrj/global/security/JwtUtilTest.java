package kdt.washPrj.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-thirty-two-bytes-long";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
    }

    @Test
    void createTokenReturnsSignedJwt() {
        String token =
                jwtUtil.createToken("customer01", "ROLE_CUSTOMER", 0L);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void validateTokenAcceptsGeneratedTokenAndRejectsTampering() {
        String token =
                jwtUtil.createToken("customer01", "ROLE_CUSTOMER", 0L);

        assertTrue(jwtUtil.validateToken(token));
        assertFalse(jwtUtil.validateToken(token + "tampered"));
    }

    @Test
    void getUsernameReturnsTokenSubject() {
        String token =
                jwtUtil.createToken("customer01", "ROLE_CUSTOMER", 0L);

        assertEquals("customer01", jwtUtil.getUsername(token));
    }

    @Test
    void getRoleReturnsTokenRoleClaim() {
        String token =
                jwtUtil.createToken("owner01", "ROLE_OWNER", 3L);

        assertEquals("ROLE_OWNER", jwtUtil.getRole(token));
    }

    @Test
    void getCredentialVersionReturnsVersionClaim() {
        String token = jwtUtil.createToken(
                "customer01",
                "ROLE_CUSTOMER",
                4L
        );

        assertEquals(4L, jwtUtil.getCredentialVersion(token));
    }
}
