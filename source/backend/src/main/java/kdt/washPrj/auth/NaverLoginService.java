package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.LoginResponseDTO;
import kdt.washPrj.auth.dto.NaverAuthorizationResponseDTO;
import kdt.washPrj.auth.dto.NaverCallbackResponseDTO;
import kdt.washPrj.auth.dto.NaverProfileDTO;
import kdt.washPrj.auth.dto.NaverSignupRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class NaverLoginService {

    private static final String PROVIDER = "NAVER";

    private final NaverOAuthClient naverOAuthClient;
    private final NaverLoginSessionStore sessionStore;
    private final AuthMapper authMapper;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    public NaverLoginService(
            NaverOAuthClient naverOAuthClient,
            NaverLoginSessionStore sessionStore,
            AuthMapper authMapper,
            AuthService authService
    ) {
        this.naverOAuthClient = naverOAuthClient;
        this.sessionStore = sessionStore;
        this.authMapper = authMapper;
        this.authService = authService;
    }

    public NaverAuthorizationResponseDTO createAuthorization() {
        return createAuthorization(false);
    }

    public NaverAuthorizationResponseDTO createAuthorization(boolean appClient) {
        if (!naverOAuthClient.isConfigured()) {
            throw new NaverOAuthException(
                    "NAVER_LOGIN_NOT_CONFIGURED",
                    "네이버 로그인 환경변수가 설정되지 않았습니다.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        String state = sessionStore.issueState();
        return new NaverAuthorizationResponseDTO(
                naverOAuthClient.createAuthorizationUrl(state, appClient),
                sessionStore.getStateTtlSeconds()
        );
    }

    @Transactional
    public NaverCallbackResponseDTO handleCallback(
            String code,
            String state,
            String error,
            String errorDescription
    ) {
        sessionStore.consumeState(state);

        if (error != null && !error.isBlank()) {
            String message = "access_denied".equals(error)
                    ? "네이버 로그인 동의가 취소되었습니다."
                    : "네이버 로그인을 완료하지 못했습니다."
                            + safeDescription(errorDescription);
            throw new NaverOAuthException(
                    "access_denied".equals(error) ? "NAVER_LOGIN_CANCELED" : "NAVER_LOGIN_FAILED",
                    message,
                    HttpStatus.BAD_REQUEST
            );
        }

        if (code == null || code.isBlank()) {
            throw new NaverOAuthException(
                    "NAVER_CODE_MISSING",
                    "네이버 인증 코드가 없습니다.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String accessToken = naverOAuthClient.exchangeAccessToken(code, state);
        NaverProfileDTO profile = naverOAuthClient.fetchProfile(accessToken);
        AccountVO account = authMapper.resolveSocialLoginAccount(
                PROVIDER,
                profile.providerUserId()
        );

        if (account != null) {
            return NaverCallbackResponseDTO.builder()
                    .status("LOGIN_SUCCESS")
                    .login(authService.createLoginResponse(account))
                    .build();
        }

        return NaverCallbackResponseDTO.builder()
                .status("REGISTRATION_REQUIRED")
                .registrationToken(sessionStore.issueRegistration(profile))
                .profile(profile)
                .build();
    }

    @Transactional
    public LoginResponseDTO completeSignup(NaverSignupRequestDTO request) {
        NaverProfileDTO profile = sessionStore.consumeRegistration(
                request.getRegistrationToken()
        );

        try {
            authService.signupSocial(request, randomPassword());
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("이미 등록된")) {
                throw new NaverOAuthException(
                        "NAVER_DUPLICATE_ACCOUNT",
                        "같은 연락처 또는 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인해주세요.",
                        HttpStatus.CONFLICT
                );
            }
            throw exception;
        }

        String linkResult = authMapper.linkSocialAccount(
                PROVIDER,
                profile.providerUserId(),
                request.getLoginId()
        );
        if (!"OK".equals(linkResult)) {
            throw new NaverOAuthException(
                    "NAVER_ACCOUNT_LINK_FAILED",
                    "네이버 계정 연결에 실패했습니다.",
                    HttpStatus.CONFLICT
            );
        }

        AccountVO account = authMapper.resolveSocialLoginAccount(
                PROVIDER,
                profile.providerUserId()
        );
        if (account == null) {
            throw new IllegalStateException("Linked Naver account was not found.");
        }
        return authService.createLoginResponse(account);
    }

    @Transactional
    public void unlink(String loginId) {
        if (authMapper.unlinkSocialAccount(loginId, PROVIDER) == 0) {
            throw new NaverOAuthException(
                    "NAVER_ACCOUNT_NOT_LINKED",
                    "연결된 네이버 계정이 없습니다.",
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private String randomPassword() {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        return "SOCIAL_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String safeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        return " (" + description.replaceAll("[\\r\\n]", " ").trim() + ")";
    }
}
