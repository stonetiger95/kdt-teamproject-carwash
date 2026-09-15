package kdt.washPrj.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kdt.washPrj.global.error.ApiErrorResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final CurrentUserMapper currentUserMapper;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public JwtFilter(
            JwtUtil jwtUtil,
            CurrentUserMapper currentUserMapper,
            ApiErrorResponseWriter apiErrorResponseWriter) {
        this.jwtUtil = jwtUtil;
        this.currentUserMapper = currentUserMapper;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        String path =
                request.getServletPath();

        return path.startsWith("/api/auth/")
                || path.equals("/api/line/webhook")
                || (path.startsWith("/api/public/")
                && !path.startsWith("/api/public/chatbot/"))
                || path.equals("/api/payments/payapp/feedback")
                || path.equals("/api/prepaid/billing-cards/payapp/callback")
                || path.startsWith("/api/plate-detections")
                || path.startsWith("/api/gates/")
                || path.equals("/api/wash-cycles/detect")
                || path.startsWith("/api/admin/test-console/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Skip CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // No JWT -> continue without authentication
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            String loginId = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);
            Long credentialVersion = jwtUtil.getCredentialVersion(token);
            if (credentialVersion == null
                    || currentUserMapper.isCurrentCredential(
                    loginId,
                    role,
                    credentialVersion
            ) == 0) {
                SecurityContextHolder.clearContext();
                apiErrorResponseWriter.write(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "SESSION_REPLACED",
                        "다른 기기에서 로그인했거나 계정 정보가 변경되어 로그아웃되었습니다."
                );
                return;
            }
            //상태가 정상이면 인증객체 생성
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority(role));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            loginId,
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        } catch (Exception e) {

            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
