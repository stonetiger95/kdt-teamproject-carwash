package kdt.washPrj.global.security;

import jakarta.servlet.FilterChain;
import kdt.washPrj.global.error.ApiErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtFilterTest {

    private JwtUtil jwtUtil;
    private CurrentUserMapper currentUserMapper;
    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        currentUserMapper = mock(CurrentUserMapper.class);
        filter = new JwtFilter(
                jwtUtil,
                currentUserMapper,
                mock(ApiErrorResponseWriter.class)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilterPublicPathsOnly() {
        MockHttpServletRequest publicRequest =
                new MockHttpServletRequest();
        publicRequest.setServletPath("/api/auth/login");
        MockHttpServletRequest securedRequest =
                new MockHttpServletRequest();
        securedRequest.setServletPath("/api/customer/my-page");
        MockHttpServletRequest washCycleRequest =
                new MockHttpServletRequest();
        washCycleRequest.setServletPath("/api/wash-cycles/detect");

        assertTrue(filter.shouldNotFilter(publicRequest));
        assertTrue(filter.shouldNotFilter(washCycleRequest));
        assertFalse(filter.shouldNotFilter(securedRequest));
    }

    @Test
    void doFilterInternalAuthenticatesValidActiveCustomer()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/customer/my-page");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.getUsername("valid-token"))
                .thenReturn("customer01");
        when(jwtUtil.getRole("valid-token"))
                .thenReturn("ROLE_CUSTOMER");
        when(jwtUtil.getCredentialVersion("valid-token"))
                .thenReturn(0L);
        when(currentUserMapper.isCurrentCredential(
                "customer01",
                "ROLE_CUSTOMER",
                0L
        )).thenReturn(1);

        filter.doFilterInternal(request, response, chain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertEquals("customer01", authentication.getName());
        assertEquals(
                "ROLE_CUSTOMER",
                authentication.getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );
        verify(chain).doFilter(request, response);
    }
}
