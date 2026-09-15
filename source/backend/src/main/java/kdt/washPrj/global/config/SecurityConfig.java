package kdt.washPrj.global.config;

import jakarta.servlet.DispatcherType;
import kdt.washPrj.global.error.ApiErrorResponseWriter;
import kdt.washPrj.global.security.CurrentUserMapper;
import kdt.washPrj.global.security.JwtFilter;
import kdt.washPrj.global.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CurrentUserMapper currentUserMapper;
    private final JwtUtil jwtUtil;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.flutter-web-base-url:${app.frontend-base-url:http://localhost:5173}}")
    private String flutterWebBaseUrl;

    @Value("${app.backend-base-url:http://localhost}")
    private String backendBaseUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // JWT 인증을 사용하므로 CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // CORS 활성화
                .cors(Customizer.withDefaults())

                // 세션을 사용하지 않음
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 인증 실패 시 401 반환
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(
                                        (request, response, exception) ->
                                                apiErrorResponseWriter.write(
                                                        response,
                                                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                                                        "UNAUTHORIZED",
                                                        "인증이 필요합니다."
                                                )
                                )
                                .accessDeniedHandler(
                                        (request, response, exception) ->
                                                apiErrorResponseWriter.write(
                                                        response,
                                                        org.springframework.http.HttpStatus.FORBIDDEN,
                                                        "FORBIDDEN",
                                                        "접근 권한이 없습니다."
                                                )
                                )
                )

                .authorizeHttpRequests(auth -> auth

                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        // Spring error/async dispatch requests
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR,
                                DispatcherType.ASYNC
                        )
                        .permitAll()

                        // 비회원 공개 API
                        .requestMatchers("/api/public/**")
                        .permitAll()

                        // 인증 공개 API
                        .requestMatchers("/api/auth/**")
                        .permitAll()

                        // LINE Messaging API webhook (request signature is verified by the controller)
                        .requestMatchers(HttpMethod.POST, "/api/line/webhook")
                        .permitAll()

                        // Liveness is public; detailed operational metrics require an administrator JWT.
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()

                        .requestMatchers("/actuator/**")
                        .hasRole("ADMIN")

                        // PayApp 결제 결과 feedback
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payments/payapp/feedback"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payments/payapp/feedback"
                        )
                        .permitAll()

                        // PayApp 빌링카드 callback
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/prepaid/billing-cards/payapp/callback"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/prepaid/billing-cards/payapp/callback"
                        )
                        .permitAll()

                        // OCR 및 Gate 공개 API
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/plate-detections/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/gates/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/wash-cycles/**"
                        )
                        .permitAll()

                        // 시연용 Postpage 테스트 콘솔 API
                        .requestMatchers("/api/admin/test-console/**")
                        .permitAll()

                        // 고객 API
                        .requestMatchers("/api/customer/**")
                        .hasRole("CUSTOMER")

                        .requestMatchers("/api/prepaid/**")
                        .hasRole("CUSTOMER")

                        // 사업자 API
                        .requestMatchers("/api/owner/**")
                        .hasRole("OWNER")

                        // 관리자 API
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // 나머지 요청은 인증 필요
                        .anyRequest()
                        .authenticated()
                )

                // JWT 필터
                .addFilterBefore(
                        new JwtFilter(
                                jwtUtil,
                                currentUserMapper,
                                apiErrorResponseWriter
                        ),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        /*
         * 일반 프론트엔드 API용 CORS
         */
        CorsConfiguration defaultConfiguration =
                new CorsConfiguration();

        defaultConfiguration.setAllowedOrigins(List.of(
                frontendBaseUrl,
                "http://localhost:5173",
                flutterWebBaseUrl,
                "http://127.0.0.1:5173",
                backendBaseUrl
        ));

        defaultConfiguration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        defaultConfiguration.setAllowedHeaders(List.of("*"));
        defaultConfiguration.setAllowCredentials(true);

        /*
         * PayApp 결제 완료 복귀 전용 CORS
         *
         * PayApp가 다음 Origin으로 POST 요청을 보낸다.
         * Origin: https://www.payapp.kr
         *
         * 이 API는 인증정보를 사용하지 않고
         * 프론트 화면으로 303 이동만 처리한다.
         */
        CorsConfiguration payAppReturnConfiguration =
                new CorsConfiguration();

        /*
         * 결제창의 브라우저/웹뷰에 따라 Origin이 www.payapp.kr,
         * payapp.kr 또는 null로 전달될 수 있다. 이 설정은 결제 상태를
         * 변경하지 않고 고정된 프론트 주소로 303 이동만 하는 return
         * 경로에만 연결되므로 모든 Origin의 화면 복귀를 허용한다.
         */
        payAppReturnConfiguration.setAllowedOrigins(List.of("*"));

        payAppReturnConfiguration.setAllowedMethods(List.of(
                "GET",
                "POST"
        ));

        payAppReturnConfiguration.setAllowedHeaders(List.of("*"));
        payAppReturnConfiguration.setAllowCredentials(false);

        /*
         * 행정안전부 도로명주소 팝업 결과 복귀 전용 CORS
         *
         * 주소를 선택하면 business.juso.go.kr 페이지가 브라우저에서
         * 백엔드 callback URL로 POST한다.
         */
        CorsConfiguration jusoCallbackConfiguration =
                new CorsConfiguration();

        jusoCallbackConfiguration.setAllowedOriginPatterns(List.of(
                "https://*.juso.go.kr",
                "null"
        ));

        jusoCallbackConfiguration.setAllowedMethods(List.of(
                "POST"
        ));

        jusoCallbackConfiguration.setAllowedHeaders(List.of("*"));
        jusoCallbackConfiguration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // PayApp 복귀 API에는 전용 CORS 적용
        source.registerCorsConfiguration(
                "/api/public/payments/payapp/return/**",
                payAppReturnConfiguration
        );

        // 비회원 예약 결제도 PayApp 결제창에서 브라우저 POST로 복귀한다.
        source.registerCorsConfiguration(
                "/api/public/non-member-reservations/payments/payapp/return",
                payAppReturnConfiguration
        );

        // 공식 Juso 하위 도메인과 sandboxed popup의 null Origin만 허용
        source.registerCorsConfiguration(
                "/api/public/address/juso/callback",
                jusoCallbackConfiguration
        );

        // 나머지 API에는 일반 CORS 적용
        source.registerCorsConfiguration(
                "/**",
                defaultConfiguration
        );

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
