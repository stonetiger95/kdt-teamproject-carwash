package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.AuthRegistrationResultDTO;
import kdt.washPrj.auth.dto.LoginRequestDTO;
import kdt.washPrj.auth.dto.LoginResponseDTO;
import kdt.washPrj.auth.dto.OwnerRegisterRequestDTO;
import kdt.washPrj.auth.dto.SignupRequestDTO;
import kdt.washPrj.auth.identity.PortOneIdentityService;
import kdt.washPrj.auth.identity.VerifiedIdentityClaims;
import kdt.washPrj.global.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthMapper authMapper;
    private JwtUtil jwtUtil;
    private PortOneIdentityService portOneIdentityService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authMapper = mock(AuthMapper.class);
        jwtUtil = mock(JwtUtil.class);
        portOneIdentityService = mock(PortOneIdentityService.class);
        authService = new AuthService(
                authMapper,
                jwtUtil,
                portOneIdentityService
        );
    }

    @Test
    void loginReturnsAccountAndGeneratedToken() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setLoginId("customer01");
        request.setPassword("password");

        AccountVO account = new AccountVO();
        account.setLoginId("customer01");
        account.setUserName("Customer");
        account.setRole("ROLE_CUSTOMER");
        account.setCustomerId(7);
        account.setCustomerStatus("ACTIVE");
        account.setCredentialVersion(0L);

        when(authMapper.login(request)).thenReturn(account);
        when(jwtUtil.createToken("customer01", "ROLE_CUSTOMER", 0L))
                .thenReturn("access-token");

        LoginResponseDTO response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("customer01", response.getLoginId());
        assertEquals("Customer", response.getUserName());
        assertEquals("ROLE_CUSTOMER", response.getRole());
        assertEquals(7, response.getCustomerId());
        assertEquals("ACTIVE", response.getCustomerStatus());
    }

    @Test
    void publicLoginRejectsAdministratorAccountBeforeTokenCreation() {
        LoginRequestDTO request = loginRequest("admin");
        AccountVO account = account("admin", "ROLE_ADMIN");

        when(authMapper.login(request)).thenReturn(account);

        LoginResponseDTO response = authService.login(request);

        assertNull(response);
        verify(jwtUtil, never()).createToken(
                "admin",
                "ROLE_ADMIN",
                0L
        );
    }

    @Test
    void adminLoginReturnsTokenForAdministratorAccount() {
        LoginRequestDTO request = loginRequest("admin");
        AccountVO account = account("admin", "ROLE_ADMIN");

        when(authMapper.login(request)).thenReturn(account);
        when(jwtUtil.createToken("admin", "ROLE_ADMIN", 0L))
                .thenReturn("admin-access-token");

        LoginResponseDTO response = authService.loginAdmin(request);

        assertEquals("admin-access-token", response.getAccessToken());
        assertEquals("ROLE_ADMIN", response.getRole());
    }

    @Test
    void adminLoginRejectsNonAdministratorAccountBeforeTokenCreation() {
        LoginRequestDTO request = loginRequest("customer01");
        AccountVO account = account("customer01", "ROLE_CUSTOMER");
        account.setCustomerStatus("ACTIVE");

        when(authMapper.login(request)).thenReturn(account);

        LoginResponseDTO response = authService.loginAdmin(request);

        assertNull(response);
        verify(jwtUtil, never()).createToken(
                "customer01",
                "ROLE_CUSTOMER",
                0L
        );
    }

    @Test
    void signupRejectsDuplicatePhone() {
        SignupRequestDTO request = signupRequest();
        request.setGender("F");
        VerifiedIdentityClaims identity = identityClaims("U");
        stubIdentityProof(request, identity);

        when(authMapper.registerIdentityVerifiedCustomerChecked(
                request,
                PortOneIdentityService.PROVIDER,
                identity
        ))
                .thenReturn(registrationResult("DUPLICATE_PHONE", null));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signup(request)
        );

        assertEquals(
                "이미 등록된 전화번호입니다.",
                exception.getMessage()
        );
        verify(authMapper).registerIdentityVerifiedCustomerChecked(
                request,
                PortOneIdentityService.PROVIDER,
                identity
        );
        verify(authMapper, never()).existsPhone("01012345678");
    }

    private LoginRequestDTO loginRequest(String loginId) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setLoginId(loginId);
        request.setPassword("password");
        return request;
    }

    private AccountVO account(String loginId, String role) {
        AccountVO account = new AccountVO();
        account.setLoginId(loginId);
        account.setUserName(loginId);
        account.setRole(role);
        account.setCredentialVersion(0L);
        return account;
    }

    @Test
    void signupRejectsDuplicateEmail() {
        SignupRequestDTO request = signupRequest();
        VerifiedIdentityClaims identity = identityClaims("M");
        stubIdentityProof(request, identity);

        when(authMapper.registerIdentityVerifiedCustomerChecked(
                request,
                PortOneIdentityService.PROVIDER,
                identity
        ))
                .thenReturn(registrationResult("DUPLICATE_EMAIL", null));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signup(request)
        );

        assertEquals(
                "이미 등록된 이메일입니다.",
                exception.getMessage()
        );
        verify(authMapper).registerIdentityVerifiedCustomerChecked(
                request,
                PortOneIdentityService.PROVIDER,
                identity
        );
        verify(authMapper, never()).existsEmail("customer@example.com");
    }

    @Test
    void signupNormalizesEmailAndCreatesIdentityVerifiedAccount() {
        SignupRequestDTO request = signupRequest();
        request.setGender(" f ");
        VerifiedIdentityClaims identity = identityClaims("U");
        stubIdentityProof(request, identity);
        when(authMapper.registerIdentityVerifiedCustomerChecked(
                request,
                PortOneIdentityService.PROVIDER,
                identity
        )).thenReturn(registrationResult("OK", 7L));

        assertDoesNotThrow(() -> authService.signup(request));

        assertEquals("customer@example.com", request.getEmailAddress());
        assertEquals("F", request.getGender());
        verify(authMapper).registerIdentityVerifiedCustomerChecked(
                request,
                PortOneIdentityService.PROVIDER,
                identity
        );
    }

    @Test
    void signupRequiresSelfReportedGenderRegardlessOfProviderResult() {
        SignupRequestDTO request = signupRequest();
        request.setGender(null);
        VerifiedIdentityClaims identity = identityClaims("M");
        stubIdentityProof(request, identity);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signup(request)
        );

        assertEquals("성별을 선택해주세요.", exception.getMessage());
        verify(authMapper, never())
                .registerIdentityVerifiedCustomerChecked(
                        request,
                        PortOneIdentityService.PROVIDER,
                        identity
                );
    }

    @Test
    void signupRejectsInvalidSelfReportedGenderRegardlessOfProviderResult() {
        SignupRequestDTO request = signupRequest();
        request.setGender("UNKNOWN");
        VerifiedIdentityClaims identity = identityClaims("F");
        stubIdentityProof(request, identity);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signup(request)
        );

        assertEquals("성별을 선택해주세요.", exception.getMessage());
        verify(authMapper, never())
                .registerIdentityVerifiedCustomerChecked(
                        request,
                        PortOneIdentityService.PROVIDER,
                        identity
                );
    }

    @Test
    void signupAcceptsEverySupportedSelfReportedGender() {
        for (String gender : new String[]{"M", "F", "N"}) {
            SignupRequestDTO request = signupRequest();
            request.setGender(gender);
            VerifiedIdentityClaims identity = identityClaims("U");
            stubIdentityProof(request, identity);
            when(authMapper.registerIdentityVerifiedCustomerChecked(
                    request,
                    PortOneIdentityService.PROVIDER,
                    identity
            )).thenReturn(registrationResult("OK", 7L));

            assertDoesNotThrow(() -> authService.signup(request));

            verify(authMapper)
                    .registerIdentityVerifiedCustomerChecked(
                            request,
                            PortOneIdentityService.PROVIDER,
                            identity
                    );
        }
    }

    @Test
    void signupKeepsProviderGenderSeparateFromCustomerInput() {
        String[][] cases = {
                {"M", "F"},
                {"F", "M"},
                {"N", "M"}
        };

        for (String[] genderCase : cases) {
            SignupRequestDTO request = signupRequest();
            request.setGender(genderCase[1]);
            VerifiedIdentityClaims identity =
                    identityClaims(genderCase[0]);
            stubIdentityProof(request, identity);
            when(authMapper.registerIdentityVerifiedCustomerChecked(
                    request,
                    PortOneIdentityService.PROVIDER,
                    identity
            )).thenReturn(registrationResult("OK", 7L));

            assertDoesNotThrow(() -> authService.signup(request));

            assertEquals(genderCase[0], identity.gender());
            assertEquals(genderCase[1], request.getGender());
            verify(authMapper)
                    .registerIdentityVerifiedCustomerChecked(
                            request,
                            PortOneIdentityService.PROVIDER,
                            identity
                    );
        }
    }

    @Test
    void loginIdAvailabilityChecksFormatAndDuplicate() {
        when(authMapper.existsLoginId("customer01")).thenReturn(0);

        assertTrue(authService.isLoginIdAvailable("customer01"));
        assertFalse(authService.isLoginIdAvailable("customer-01"));
        verify(authMapper).existsLoginId("customer01");
    }

    @Test
    void phoneAvailabilityNormalizesHyphens() {
        when(authMapper.existsPhone("01012345678")).thenReturn(0);

        assertTrue(authService.isPhoneAvailable("010-1234-5678"));
        assertFalse(authService.isPhoneAvailable("010-ABCD-5678"));
        verify(authMapper).existsPhone("01012345678");
    }

    @Test
    void emailAvailabilityNormalizesCaseAndWhitespace() {
        when(authMapper.existsEmail("customer@example.com")).thenReturn(0);

        assertTrue(authService.isEmailAvailable(" Customer@Example.com "));
        assertFalse(authService.isEmailAvailable("not-an-email"));
        verify(authMapper).existsEmail("customer@example.com");
    }

    @Test
    void registerOwnerNormalizesContactNumbersAndEmail() {
        OwnerRegisterRequestDTO request = ownerRequest();
        when(authMapper.registerOwnerChecked("admin01", request))
                .thenReturn(registrationResult("OK", 1L));

        assertDoesNotThrow(() ->
                authService.registerOwner("admin01", request)
        );

        assertEquals("123-45-67890", request.getBusinessNumber());
        assertEquals("01022223333", request.getPhone());
        assertEquals("owner@example.com", request.getEmailAddress());
        verify(authMapper, never()).existsBusinessNumber("1234567890");
        verify(authMapper).registerOwnerChecked("admin01", request);
    }

    @Test
    void registerOwnerRejectsDuplicateEmail() {
        OwnerRegisterRequestDTO request = ownerRequest();

        when(authMapper.registerOwnerChecked("admin01", request))
                .thenReturn(registrationResult("DUPLICATE_EMAIL", null));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerOwner("admin01", request)
        );

        assertEquals(
                "이미 등록된 이메일입니다.",
                exception.getMessage()
        );
        verify(authMapper).registerOwnerChecked("admin01", request);
        verify(authMapper, never()).existsEmail("owner@example.com");
    }

    private SignupRequestDTO signupRequest() {
        SignupRequestDTO request = new SignupRequestDTO();

        request.setLoginId("customer01");
        request.setPassword("password");
        request.setEmailAddress("Customer@Example.com");
        request.setGender("F");
        request.setIdentityVerificationId(
                "iv0123456789abcdef0123456789abcdef"
        );
        request.setIdentityProof("identity-proof");
        request.setIdentityStateToken("identity-state-token");

        return request;
    }

    private void stubIdentityProof(
            SignupRequestDTO request,
            VerifiedIdentityClaims identity
    ) {
        when(portOneIdentityService.readSignupProof(
                request.getIdentityVerificationId(),
                request.getIdentityProof(),
                request.getIdentityStateToken()
        )).thenReturn(identity);
    }

    private VerifiedIdentityClaims identityClaims(String gender) {
        return new VerifiedIdentityClaims(
                "iv0123456789abcdef0123456789abcdef",
                "Customer",
                LocalDate.of(1995, 1, 2),
                "01012345678",
                gender,
                "a".repeat(64),
                "b".repeat(64),
                "TEST",
                OffsetDateTime.of(
                        2026, 8, 6, 1, 0, 0, 0,
                        ZoneOffset.UTC
                ),
                OffsetDateTime.of(
                        2026, 8, 6, 1, 10, 0, 0,
                        ZoneOffset.UTC
                )
        );
    }

    private AuthRegistrationResultDTO registrationResult(
            String resultCode,
            Long entityId
    ) {
        AuthRegistrationResultDTO result =
                new AuthRegistrationResultDTO();
        result.setResultCode(resultCode);
        result.setEntityId(entityId);
        return result;
    }

    private OwnerRegisterRequestDTO ownerRequest() {
        OwnerRegisterRequestDTO request =
                new OwnerRegisterRequestDTO();

        request.setLoginId("owner01");
        request.setPassword("password");
        request.setOwnerName("Owner");
        request.setBusinessNumber("123-45-67890");
        request.setPhone("010-2222-3333");
        request.setEmailAddress(" OWNER@Example.com ");
        request.setBusinessAddress("Busan");

        return request;
    }
}
