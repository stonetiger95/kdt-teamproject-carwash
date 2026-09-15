package kdt.washPrj.auth.social;

import kdt.washPrj.auth.dto.SocialProfileDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public class KakaoOAuthClient extends AbstractSocialOAuthClient {

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String appRedirectUri;
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String userInfoUrl;

    public KakaoOAuthClient(
            JsonMapper jsonMapper,
            @Value("${kakao.login.enabled:false}") boolean enabled,
            @Value("${kakao.login.client-id:}") String clientId,
            @Value("${kakao.login.client-secret:}") String clientSecret,
            @Value("${kakao.login.redirect-uri:http://localhost:5173/auth/kakao/callback}") String redirectUri,
            @Value("${kakao.login.app-redirect-uri:}") String appRedirectUri,
            @Value("${kakao.login.authorization-url:https://kauth.kakao.com/oauth/authorize}") String authorizationUrl,
            @Value("${kakao.login.token-url:https://kauth.kakao.com/oauth/token}") String tokenUrl,
            @Value("${kakao.login.user-info-url:https://kapi.kakao.com/v2/user/me}") String userInfoUrl
    ) {
        super(jsonMapper);
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.appRedirectUri = appRedirectUri;
        this.authorizationUrl = authorizationUrl;
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public boolean isConfigured() {
        return enabled
                && hasText(clientId)
                && hasText(clientSecret)
                && hasText(redirectUri);
    }

    @Override
    public String createAuthorizationUrl(String state) {
        return createAuthorizationUrl(state, false);
    }

    @Override
    public String createAuthorizationUrl(String state, boolean appClient) {
        requireConfigured();
        String callbackUri = redirectUri(appClient);
        return authorizationUrl
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callbackUri)
                + "&state=" + encode(state);
    }

    @Override
    public SocialProfileDTO exchangeCodeAndFetchProfile(String code) {
        return exchangeCodeAndFetchProfile(code, false);
    }

    @Override
    public SocialProfileDTO exchangeCodeAndFetchProfile(String code, boolean appClient) {
        requireConfigured();
        String callbackUri = redirectUri(appClient);
        String form = "grant_type=authorization_code"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(callbackUri)
                + "&code=" + encode(code);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        Map<?, ?> tokenBody = sendJson(tokenRequest, "token");
        String accessToken = text(tokenBody.get("access_token"));
        if (accessToken == null) {
            throw providerFailure("인증 토큰을 발급받지 못했습니다.");
        }

        HttpRequest profileRequest = HttpRequest.newBuilder()
                .uri(URI.create(userInfoUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        Map<?, ?> profile = sendJson(profileRequest, "userinfo");
        String providerUserId = text(profile.get("id"));
        if (providerUserId == null) {
            throw providerFailure("회원 식별값을 확인하지 못했습니다.");
        }

        Map<?, ?> account = map(profile.get("kakao_account"));
        Map<?, ?> accountProfile = account == null
                ? null
                : map(account.get("profile"));
        String email = account != null
                && booleanValue(account.get("is_email_valid"))
                && booleanValue(account.get("is_email_verified"))
                ? normalizeEmail(text(account.get("email")))
                : null;

        return new SocialProfileDTO(
                providerUserId,
                accountProfile == null ? null : text(accountProfile.get("nickname")),
                email,
                account == null ? null : normalizePhone(text(account.get("phone_number"))),
                account == null ? "N" : normalizeGender(text(account.get("gender"))),
                account == null ? null : parseBirthDate(
                        text(account.get("birthyear")),
                        text(account.get("birthday"))
                )
        );
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private String normalizeGender(String value) {
        if ("male".equalsIgnoreCase(value)) {
            return "M";
        }
        if ("female".equalsIgnoreCase(value)) {
            return "F";
        }
        return "N";
    }

    private LocalDate parseBirthDate(String birthYear, String birthday) {
        if (birthYear == null || birthday == null || !birthday.matches("^[0-9]{4}$")) {
            return null;
        }
        try {
            return LocalDate.parse(
                    birthYear + "-" + birthday.substring(0, 2)
                            + "-" + birthday.substring(2)
            );
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String redirectUri(boolean appClient) {
        if (!appClient) {
            return redirectUri;
        }
        if (!hasText(appRedirectUri)) {
            throw providerFailure("앱 웹 로그인 콜백 주소가 설정되지 않았습니다.");
        }
        return appRedirectUri;
    }
}
