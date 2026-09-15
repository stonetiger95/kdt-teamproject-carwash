package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.LoginResponseDTO;
import kdt.washPrj.auth.dto.NaverCallbackResponseDTO;
import kdt.washPrj.auth.dto.NaverProfileDTO;
import kdt.washPrj.auth.dto.NaverSignupRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NaverLoginServiceTest {

    private NaverOAuthClient oauthClient;
    private NaverLoginSessionStore sessionStore;
    private AuthMapper authMapper;
    private AuthService authService;
    private NaverLoginService service;

    @BeforeEach
    void setUp() {
        oauthClient = mock(NaverOAuthClient.class);
        sessionStore = new NaverLoginSessionStore(300, 600);
        authMapper = mock(AuthMapper.class);
        authService = mock(AuthService.class);
        service = new NaverLoginService(oauthClient, sessionStore, authMapper, authService);
    }

    @Test
    void authorizationIsRejectedBeforeIssuingStateWhenNotConfigured() {
        when(oauthClient.isConfigured()).thenReturn(false);

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                service::createAuthorization
        );

        assertEquals("NAVER_LOGIN_NOT_CONFIGURED", exception.getCode());
        verify(oauthClient, never()).createAuthorizationUrl(
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void existingLinkedCustomerLogsInWithoutSignup() {
        String state = sessionStore.issueState();
        NaverProfileDTO profile = profile();
        AccountVO account = account();
        LoginResponseDTO login = loginResponse();
        when(oauthClient.exchangeAccessToken("code", state)).thenReturn("access-token");
        when(oauthClient.fetchProfile("access-token")).thenReturn(profile);
        when(authMapper.resolveSocialLoginAccount("NAVER", "naver-user-1"))
                .thenReturn(account);
        when(authService.createLoginResponse(account)).thenReturn(login);

        NaverCallbackResponseDTO result = service.handleCallback(
                "code",
                state,
                null,
                null
        );

        assertEquals("LOGIN_SUCCESS", result.getStatus());
        assertEquals(login, result.getLogin());
        assertNull(result.getRegistrationToken());
        verify(authService, never()).signup(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void firstSocialLoginCreatesShortLivedRegistrationSession() {
        String state = sessionStore.issueState();
        NaverProfileDTO profile = profile();
        when(oauthClient.exchangeAccessToken("code", state)).thenReturn("access-token");
        when(oauthClient.fetchProfile("access-token")).thenReturn(profile);
        when(authMapper.resolveSocialLoginAccount("NAVER", "naver-user-1"))
                .thenReturn(null);

        NaverCallbackResponseDTO result = service.handleCallback(
                "code",
                state,
                null,
                null
        );

        assertEquals("REGISTRATION_REQUIRED", result.getStatus());
        assertEquals(profile, result.getProfile());
        assertTrue(result.getRegistrationToken() != null
                && !result.getRegistrationToken().isBlank());
    }

    @Test
    void deniedConsentStopsBeforeTokenExchange() {
        String state = sessionStore.issueState();

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                () -> service.handleCallback(
                        null,
                        state,
                        "access_denied",
                        "user canceled"
                )
        );

        assertEquals("NAVER_LOGIN_CANCELED", exception.getCode());
        verify(oauthClient, never()).exchangeAccessToken(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void completeSignupCreatesCustomerLinksIdentityAndReturnsLogin() {
        String registrationToken = sessionStore.issueRegistration(profile());
        NaverSignupRequestDTO request = signupRequest(registrationToken);
        AccountVO linkedAccount = account();
        LoginResponseDTO login = loginResponse();
        when(authMapper.linkSocialAccount("NAVER", "naver-user-1", "naver01"))
                .thenReturn("OK");
        when(authMapper.resolveSocialLoginAccount("NAVER", "naver-user-1"))
                .thenReturn(linkedAccount);
        when(authService.createLoginResponse(linkedAccount)).thenReturn(login);

        LoginResponseDTO result = service.completeSignup(request);

        assertEquals(login, result);
        ArgumentCaptor<NaverSignupRequestDTO> signupCaptor = ArgumentCaptor.forClass(
                NaverSignupRequestDTO.class
        );
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).signupSocial(signupCaptor.capture(), passwordCaptor.capture());
        NaverSignupRequestDTO signup = signupCaptor.getValue();
        assertEquals("naver01", signup.getLoginId());
        assertEquals(request.getUserName(), signup.getUserName());
        assertTrue(passwordCaptor.getValue().startsWith("SOCIAL_"));
        verify(authMapper).linkSocialAccount("NAVER", "naver-user-1", "naver01");
    }

    @Test
    void duplicateContactIsReturnedAsSocialAccountConflict() {
        String registrationToken = sessionStore.issueRegistration(profile());
        NaverSignupRequestDTO request = signupRequest(registrationToken);
        org.mockito.Mockito.doThrow(
                new IllegalArgumentException("이미 등록된 이메일입니다.")
        ).when(authService).signupSocial(
                org.mockito.ArgumentMatchers.any(NaverSignupRequestDTO.class),
                org.mockito.ArgumentMatchers.anyString()
        );

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                () -> service.completeSignup(request)
        );

        assertEquals("NAVER_DUPLICATE_ACCOUNT", exception.getCode());
        verify(authMapper, never()).linkSocialAccount(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void unlinkRejectsAccountWithoutNaverLink() {
        when(authMapper.unlinkSocialAccount("customer01", "NAVER")).thenReturn(0);

        NaverOAuthException exception = assertThrows(
                NaverOAuthException.class,
                () -> service.unlink("customer01")
        );

        assertEquals("NAVER_ACCOUNT_NOT_LINKED", exception.getCode());
    }

    private NaverProfileDTO profile() {
        return new NaverProfileDTO(
                "naver-user-1",
                "김민준",
                "minjun@example.com",
                "01012345678",
                "M",
                LocalDate.of(1995, 3, 12)
        );
    }

    private NaverSignupRequestDTO signupRequest(String registrationToken) {
        NaverSignupRequestDTO request = new NaverSignupRequestDTO();
        request.setRegistrationToken(registrationToken);
        request.setLoginId("naver01");
        request.setUserName("김민준");
        request.setBirthDate(LocalDate.of(1995, 3, 12));
        request.setPhone("01012345678");
        request.setGender("M");
        request.setEmailAddress("minjun@example.com");
        request.setTermsAccepted(true);
        return request;
    }

    private AccountVO account() {
        AccountVO account = new AccountVO();
        account.setLoginId("naver01");
        account.setUserName("김민준");
        account.setRole("ROLE_CUSTOMER");
        account.setCustomerId(7);
        account.setCustomerStatus("ACTIVE");
        return account;
    }

    private LoginResponseDTO loginResponse() {
        return LoginResponseDTO.builder()
                .accessToken("jwt-token")
                .loginId("naver01")
                .userName("김민준")
                .role("ROLE_CUSTOMER")
                .customerId(7)
                .customerStatus("ACTIVE")
                .build();
    }
}
