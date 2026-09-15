package kdt.washPrj.auth.social;

import kdt.washPrj.auth.dto.SocialProfileDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

@Component
public class GoogleOAuthClient extends AbstractSocialOAuthClient {

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String appRedirectUri;
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String userInfoUrl;

    public GoogleOAuthClient(
            JsonMapper jsonMapper,
            @Value("${google.login.enabled:false}") boolean enabled,
            @Value("${google.login.client-id:}") String clientId,
            @Value("${google.login.client-secret:}") String clientSecret,
            @Value("${google.login.redirect-uri:http://localhost:5173/auth/google/callback}") String redirectUri,
            @Value("${google.login.app-redirect-uri:}") String appRedirectUri,
            @Value("${google.login.authorization-url:https://accounts.google.com/o/oauth2/v2/auth}") String authorizationUrl,
            @Value("${google.login.token-url:https://oauth2.googleapis.com/token}") String tokenUrl,
            @Value("${google.login.user-info-url:https://openidconnect.googleapis.com/v1/userinfo}") String userInfoUrl
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
        return SocialProvider.GOOGLE;
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
                + "&scope=" + encode("openid email profile")
                + "&state=" + encode(state)
                + "&prompt=select_account";
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
                .header("Content-Type", "application/x-www-form-urlencoded")
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
        String providerUserId = text(profile.get("sub"));
        if (providerUserId == null) {
            throw providerFailure("회원 식별값을 확인하지 못했습니다.");
        }

        String email = booleanValue(profile.get("email_verified"))
                ? normalizeEmail(text(profile.get("email")))
                : null;
        return new SocialProfileDTO(
                providerUserId,
                text(profile.get("name")),
                email,
                null,
                "N",
                null
        );
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
