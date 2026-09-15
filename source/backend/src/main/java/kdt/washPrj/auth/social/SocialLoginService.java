package kdt.washPrj.auth.social;

import kdt.washPrj.auth.AccountVO;
import kdt.washPrj.auth.AuthMapper;
import kdt.washPrj.auth.AuthService;
import kdt.washPrj.auth.dto.LoginResponseDTO;
import kdt.washPrj.auth.dto.SocialAuthorizationResponseDTO;
import kdt.washPrj.auth.dto.SocialCallbackResponseDTO;
import kdt.washPrj.auth.dto.SocialProfileDTO;
import kdt.washPrj.auth.dto.SocialSignupRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class SocialLoginService {

    private final Map<SocialProvider, SocialOAuthClient> clients;
    private final SocialLoginTokenCodec tokenCodec;
    private final AuthMapper authMapper;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SocialLoginService(
            List<SocialOAuthClient> clients,
            SocialLoginTokenCodec tokenCodec,
            AuthMapper authMapper,
            AuthService authService
    ) {
        this.clients = new EnumMap<>(SocialProvider.class);
        clients.forEach(client -> this.clients.put(client.provider(), client));
        this.tokenCodec = tokenCodec;
        this.authMapper = authMapper;
        this.authService = authService;
    }

    public SocialAuthorizationResponseDTO createAuthorization(String providerPath) {
        return createAuthorization(providerPath, false);
    }

    public SocialAuthorizationResponseDTO createAuthorization(
            String providerPath,
            boolean appClient
    ) {
        SocialProvider provider = SocialProvider.fromPath(providerPath);
        requireLoginProvider(provider);
        return createAuthorization(provider, appClient);
    }

    private SocialAuthorizationResponseDTO createAuthorization(
            SocialProvider provider,
            boolean appClient
    ) {
        SocialOAuthClient client = requireClient(provider);
        if (!client.isConfigured() || !tokenCodec.isConfigured()) {
            throw notConfigured(provider);
        }
        SocialLoginTokenCodec.IssuedState state = tokenCodec.issueState(provider);
        return new SocialAuthorizationResponseDTO(
                client.createAuthorizationUrl(state.token(), appClient),
                state.token(),
                state.expiresInSeconds()
        );
    }

    public boolean isConfigured(String providerPath) {
        SocialProvider provider = SocialProvider.fromPath(providerPath);
        SocialOAuthClient client = clients.get(provider);
        return client != null
                && client.isConfigured()
                && tokenCodec.isConfigured();
    }

    @Transactional
    public SocialCallbackResponseDTO handleCallback(
            String providerPath,
            String code,
            String state,
            String error,
            String errorDescription
    ) {
        return handleCallback(
                providerPath,
                code,
                state,
                error,
                errorDescription,
                false
        );
    }

    @Transactional
    public SocialCallbackResponseDTO handleCallback(
            String providerPath,
            String code,
            String state,
            String error,
            String errorDescription,
            boolean appClient
    ) {
        SocialProvider provider = SocialProvider.fromPath(providerPath);
        requireLoginProvider(provider);
        tokenCodec.validateState(provider, state);

        if (error != null && !error.isBlank()) {
            boolean canceled = "access_denied".equals(error);
            throw new SocialOAuthException(
                    provider.name() + (canceled ? "_LOGIN_CANCELED" : "_LOGIN_FAILED"),
                    canceled
                            ? provider.displayName() + " 로그인 동의가 취소되었습니다."
                            : provider.displayName() + " 로그인을 완료하지 못했습니다."
                            + safeDescription(errorDescription),
                    HttpStatus.BAD_REQUEST
            );
        }
        if (code == null || code.isBlank()) {
            throw new SocialOAuthException(
                    provider.name() + "_CODE_MISSING",
                    provider.displayName() + " 인증 코드가 없습니다.",
                    HttpStatus.BAD_REQUEST
            );
        }

        SocialProfileDTO profile = requireClient(provider)
                .exchangeCodeAndFetchProfile(code, appClient);
        AccountVO account = authMapper.resolveSocialLoginAccount(
                provider.name(),
                profile.providerUserId()
        );
        if (account != null) {
            return SocialCallbackResponseDTO.builder()
                    .status("LOGIN_SUCCESS")
                    .login(authService.createLoginResponse(account))
                    .build();
        }

        return SocialCallbackResponseDTO.builder()
                .status("REGISTRATION_REQUIRED")
                .registrationToken(tokenCodec.issueRegistration(provider, profile))
                .profile(profile)
                .build();
    }

    @Transactional
    public LoginResponseDTO completeSignup(
            String providerPath,
            SocialSignupRequestDTO request
    ) {
        SocialProvider provider = SocialProvider.fromPath(providerPath);
        requireLoginProvider(provider);
        SocialProfileDTO profile = tokenCodec.readRegistration(
                provider,
                request.getRegistrationToken()
        );

        try {
            authService.signupSocial(request, randomPassword());
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("이미 등록된")) {
                throw new SocialOAuthException(
                        "SOCIAL_DUPLICATE_ACCOUNT",
                        "같은 연락처 또는 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인해주세요.",
                        HttpStatus.CONFLICT
                );
            }
            throw exception;
        }

        String linkResult = authMapper.linkSocialAccount(
                provider.name(),
                profile.providerUserId(),
                request.getLoginId()
        );
        if (!"OK".equals(linkResult)) {
            throw new SocialOAuthException(
                    "SOCIAL_ACCOUNT_LINK_FAILED",
                    provider.displayName() + " 계정 연결에 실패했습니다.",
                    HttpStatus.CONFLICT
            );
        }

        AccountVO account = authMapper.resolveSocialLoginAccount(
                provider.name(),
                profile.providerUserId()
        );
        if (account == null) {
            throw new IllegalStateException("Linked social account was not found.");
        }
        return authService.createLoginResponse(account);
    }

    @Transactional
    public void unlink(String providerPath, String loginId) {
        SocialProvider provider = SocialProvider.fromPath(providerPath);
        if (authMapper.unlinkSocialAccount(loginId, provider.name()) == 0) {
            throw new SocialOAuthException(
                    "SOCIAL_ACCOUNT_NOT_LINKED",
                    "연결된 " + provider.displayName() + " 계정이 없습니다.",
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private SocialOAuthClient requireClient(SocialProvider provider) {
        SocialOAuthClient client = clients.get(provider);
        if (client == null) {
            throw SocialOAuthException.unsupportedProvider();
        }
        return client;
    }

    private void requireLoginProvider(SocialProvider provider) {
        if (provider == SocialProvider.LINE) {
            throw SocialOAuthException.unsupportedProvider();
        }
    }

    private SocialOAuthException notConfigured(SocialProvider provider) {
        return new SocialOAuthException(
                provider.name() + "_LOGIN_NOT_CONFIGURED",
                provider.displayName() + " 로그인 환경변수가 설정되지 않았습니다.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private String randomPassword() {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        return "SOCIAL_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String safeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        return " (" + description.replaceAll("[\\r\\n]", " ").trim() + ")";
    }
}
