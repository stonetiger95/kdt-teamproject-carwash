package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.NaverProfileDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

@Component
public class NaverOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(NaverOAuthClient.class);

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String appRedirectUri;
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String profileUrl;

    public NaverOAuthClient(
            JsonMapper jsonMapper,
            @Value("${naver.login.enabled:false}") boolean enabled,
            @Value("${naver.login.client-id:}") String clientId,
            @Value("${naver.login.client-secret:}") String clientSecret,
            @Value("${naver.login.redirect-uri:http://localhost:5173/auth/naver/callback}") String redirectUri,
            @Value("${naver.login.app-redirect-uri:}") String appRedirectUri,
            @Value("${naver.login.authorization-url:https://nid.naver.com/oauth2.0/authorize}") String authorizationUrl,
            @Value("${naver.login.token-url:https://nid.naver.com/oauth2.0/token}") String tokenUrl,
            @Value("${naver.login.profile-url:https://openapi.naver.com/v1/nid/me}") String profileUrl
    ) {
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.appRedirectUri = appRedirectUri;
        this.authorizationUrl = authorizationUrl;
        this.tokenUrl = tokenUrl;
        this.profileUrl = profileUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public boolean isConfigured() {
        return enabled
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }

    public String createAuthorizationUrl(String state) {
        return createAuthorizationUrl(state, false);
    }

    public String createAuthorizationUrl(String state, boolean appClient) {
        requireConfigured();
        String callbackUri = redirectUri(appClient);
        return authorizationUrl
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callbackUri)
                + "&state=" + encode(state);
    }

    public String exchangeAccessToken(String code, String state) {
        requireConfigured();
        String form = "grant_type=authorization_code"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&state=" + encode(state);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        Map<?, ?> body = sendJson(request, "token");
        String accessToken = text(body.get("access_token"));
        if (accessToken == null) {
            log.warn("Naver token response did not contain an access token. error={}", body.get("error"));
            throw providerFailure("네이버 인증 토큰을 발급받지 못했습니다.");
        }
        return accessToken;
    }

    public NaverProfileDTO fetchProfile(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(profileUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        Map<?, ?> body = sendJson(request, "profile");
        if (!"00".equals(text(body.get("resultcode")))) {
            throw providerFailure("네이버 회원 정보를 확인하지 못했습니다.");
        }

        Object response = body.get("response");
        if (!(response instanceof Map<?, ?> profile)) {
            throw providerFailure("네이버 회원 정보 응답 형식이 올바르지 않습니다.");
        }

        String providerUserId = text(profile.get("id"));
        if (providerUserId == null) {
            throw providerFailure("네이버 회원 식별값을 확인하지 못했습니다.");
        }

        String name = firstText(profile, "name", "nickname");
        String email = normalizeEmail(text(profile.get("email")));
        String phone = normalizePhone(text(profile.get("mobile")));
        String gender = normalizeGender(text(profile.get("gender")));
        LocalDate birthDate = parseBirthDate(
                text(profile.get("birthyear")),
                text(profile.get("birthday"))
        );

        return new NaverProfileDTO(
                providerUserId,
                name,
                email,
                phone,
                gender,
                birthDate
        );
    }

    private Map<?, ?> sendJson(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Naver OAuth {} request failed with HTTP {}.", operation, response.statusCode());
                throw providerFailure("네이버 인증 서버가 요청을 처리하지 못했습니다.");
            }
            return jsonMapper.readValue(response.body(), Map.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerFailure("네이버 인증 요청이 중단되었습니다.");
        } catch (NaverOAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Naver OAuth {} request failed: {}", operation, exception.getMessage());
            throw providerFailure("네이버 인증 서버에 연결하지 못했습니다.");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new NaverOAuthException(
                    "NAVER_LOGIN_NOT_CONFIGURED",
                    "네이버 로그인 환경변수가 설정되지 않았습니다.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private NaverOAuthException providerFailure(String message) {
        return new NaverOAuthException(
                "NAVER_PROVIDER_ERROR",
                message,
                HttpStatus.BAD_GATEWAY
        );
    }

    private String firstText(Map<?, ?> profile, String... keys) {
        for (String key : keys) {
            String value = text(profile.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    private String normalizeGender(String value) {
        if ("M".equalsIgnoreCase(value)) {
            return "M";
        }
        if ("F".equalsIgnoreCase(value)) {
            return "F";
        }
        return "N";
    }

    private LocalDate parseBirthDate(String birthYear, String birthday) {
        if (birthYear == null || birthday == null) {
            return null;
        }
        try {
            return LocalDate.parse(birthYear + "-" + birthday);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String redirectUri(boolean appClient) {
        if (!appClient) {
            return redirectUri;
        }
        if (appRedirectUri == null || appRedirectUri.isBlank()) {
            throw new NaverOAuthException(
                    "NAVER_APP_LOGIN_NOT_CONFIGURED",
                    "네이버 앱 웹 로그인 콜백 주소가 설정되지 않았습니다.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return appRedirectUri;
    }
}
