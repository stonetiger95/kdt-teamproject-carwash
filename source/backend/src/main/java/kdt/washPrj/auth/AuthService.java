package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.AuthRegistrationResultDTO;
import kdt.washPrj.auth.dto.LoginRequestDTO;
import kdt.washPrj.auth.dto.LoginResponseDTO;
import kdt.washPrj.auth.dto.SocialSignupRequestDTO;
import kdt.washPrj.auth.dto.OwnerRegisterRequestDTO;
import kdt.washPrj.auth.dto.SignupRequestDTO;
import kdt.washPrj.auth.identity.PortOneIdentityService;
import kdt.washPrj.auth.identity.VerifiedIdentityClaims;
import kdt.washPrj.global.security.JwtUtil;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AuthMapper authMapper;
    private final JwtUtil jwtUtil;
    private final PortOneIdentityService portOneIdentityService;

    public LoginResponseDTO login(LoginRequestDTO request) {

        AccountVO account = authMapper.login(request);

        if (account != null && ROLE_ADMIN.equals(account.getRole())) {
            return null;
        }

        return createLoginResponse(account);
    }

    public LoginResponseDTO loginAdmin(LoginRequestDTO request) {

        AccountVO account = authMapper.login(request);

        if (account == null || !ROLE_ADMIN.equals(account.getRole())) {
            return null;
        }

        return createLoginResponse(account);
    }

    public LoginResponseDTO createLoginResponse(AccountVO account) {

        if (account == null) {
            return null;
        }

        String status = account.getCustomerStatus();

        if ("ROLE_CUSTOMER".equals(account.getRole())) {

            if ("WITHDRAWN".equals(status)) {
                throw new AccountWithdrawnException();
            }

            // ACTIVE와 SUSPENDED는 로그인 허용
            if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
                return null;
            }
        }

        Long loginVersion = authMapper.rotateAccountLoginVersion(
                account.getLoginId(),
                account.getRole()
        );
        if (loginVersion == null) {
            throw new IllegalStateException(
                    "로그인 세션을 생성하지 못했습니다."
            );
        }

        String token = jwtUtil.createToken(
                account.getLoginId(),
                account.getRole(),
                loginVersion
        );

        return LoginResponseDTO.builder()
                .accessToken(token)
                .loginId(account.getLoginId())
                .userName(account.getUserName())
                .role(account.getRole())
                .customerId(account.getCustomerId())
                .ownerId(account.getOwnerId())
                .customerStatus(status)
                .build();
    }

    @Transactional
    public void signupSocial(
            SocialSignupRequestDTO request,
            String generatedPassword
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "회원가입 정보를 입력해주세요."
            );
        }

        if (request.getLoginId() == null
                || !request.getLoginId().matches("^[A-Za-z0-9]+$")) {
            throw new IllegalArgumentException(
                    "아이디는 영문과 숫자만 사용할 수 있습니다."
            );
        }

        if (generatedPassword == null || generatedPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "소셜 계정 비밀번호를 생성하지 못했습니다."
            );
        }

        String normalizedPhone = normalizeDigits(
                request.getPhone(),
                "휴대전화번호는 숫자만 사용할 수 있습니다."
        );
        String normalizedEmail = normalizeEmail(
                request.getEmailAddress()
        );

        request.setPhone(normalizedPhone);
        request.setEmailAddress(normalizedEmail);

        requireCustomerRegistration(
                authMapper.registerSocialCustomerChecked(
                        request,
                        generatedPassword
                )
        );
    }

    public LoginResponseDTO signup(SignupRequestDTO request) {

        validateBasicSignupRequest(request);

        String normalizedEmail = normalizeEmail(
                request.getEmailAddress()
        );

        request.setEmailAddress(normalizedEmail);

        VerifiedIdentityClaims identity =
                portOneIdentityService.readSignupProof(
                        request.getIdentityVerificationId(),
                        request.getIdentityProof(),
                        request.getIdentityStateToken()
                );

        String selfReportedGender = normalizeSelfReportedGender(
                request.getGender()
        );
        if (selfReportedGender == null) {
            throw new IllegalArgumentException(
                    "성별을 선택해주세요."
            );
        }
        request.setGender(selfReportedGender);

        Long customerId = requireCustomerRegistration(
                authMapper.registerIdentityVerifiedCustomerChecked(
                        request,
                        PortOneIdentityService.PROVIDER,
                        identity
                )
        );

        String role = "ROLE_CUSTOMER";
        String status = "ACTIVE";
        String token = jwtUtil.createToken(
                request.getLoginId(),
                role,
                0L
        );

        return LoginResponseDTO.builder()
                .accessToken(token)
                .loginId(request.getLoginId())
                .userName(identity.name())
                .role(role)
                .customerId(customerId.intValue())
                .ownerId(null)
                .customerStatus(status)
                .build();
    }

    private void validateBasicSignupRequest(SignupRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "회원가입 정보를 입력해주세요."
            );
        }

        if (request.getLoginId() == null
                || !request.getLoginId()
                .matches("^[A-Za-z0-9]+$")) {

            throw new IllegalArgumentException(
                    "아이디는 영문과 숫자만 사용할 수 있습니다."
            );
        }

        if (request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new IllegalArgumentException(
                    "비밀번호를 입력해주세요."
            );
        }
    }

    public boolean isLoginIdAvailable(String loginId) {

        if (loginId == null
                || !loginId.matches("^[A-Za-z0-9]+$")) {
            return false;
        }

        return authMapper.existsLoginId(loginId) == 0;
    }

    public boolean isPhoneAvailable(String phone) {
        try {
            String normalizedPhone = normalizeDigits(
                    phone,
                    "전화번호는 숫자만 사용할 수 있습니다."
            );
            return authMapper.existsPhone(normalizedPhone) == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isEmailAvailable(String emailAddress) {
        try {
            String normalizedEmail = normalizeEmail(emailAddress);
            return authMapper.existsEmail(normalizedEmail) == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional
    public void registerOwner(
            String adminLoginId,
            OwnerRegisterRequestDTO request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "업주 등록 정보를 입력해주세요."
            );
        }

        if (request.getLoginId() == null
                || !request.getLoginId()
                .matches("^[A-Za-z0-9]+$")) {

            throw new IllegalArgumentException(
                    "아이디는 영문과 숫자만 사용할 수 있습니다."
            );
        }


        if (request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new IllegalArgumentException(
                    "비밀번호를 입력해주세요."
            );
        }


        if (request.getOwnerName() == null
                || request.getOwnerName().isBlank()) {

            throw new IllegalArgumentException(
                    "업주명을 입력해주세요."
            );
        }


        if (request.getBusinessAddress() == null
                || request.getBusinessAddress().isBlank()) {

            throw new IllegalArgumentException(
                    "사업장 주소를 입력해주세요."
            );
        }


        String normalizedBusinessNumber = normalizeBusinessNumber(
                request.getBusinessNumber()
        );
        String normalizedPhone = normalizeDigits(
                request.getPhone(),
                "전화번호는 숫자만 사용할 수 있습니다."
        );
        String normalizedEmail = normalizeEmail(
                request.getEmailAddress()
        );

        request.setBusinessNumber(normalizedBusinessNumber);
        request.setPhone(normalizedPhone);
        request.setEmailAddress(normalizedEmail);


        // 1. owner INSERT
        // owner_id 자동 생성
        Long ownerId = requireOwnerRegistration(
                authMapper.registerOwnerChecked(adminLoginId, request)
        );


        // 2. 생성된 owner_id로 account INSERT
        request.setOwnerId(ownerId.intValue());
    }

    private Long requireCustomerRegistration(
            AuthRegistrationResultDTO result
    ) {
        String resultCode = requireRegistrationResultCode(result);

        return switch (resultCode) {
            case "OK" -> requireRegistrationId(result, "Customer");
            case "DUPLICATE_LOGIN" -> throw new IllegalArgumentException(
                    "이미 사용 중인 아이디입니다."
            );
            case "DUPLICATE_PHONE" -> throw new IllegalArgumentException(
                    "이미 등록된 전화번호입니다."
            );
            case "DUPLICATE_EMAIL" -> throw new IllegalArgumentException(
                    "이미 등록된 이메일입니다."
            );
            case "IDENTITY_ALREADY_REGISTERED" -> throw new IllegalStateException(
                    "이미 가입에 사용된 본인인증 정보입니다."
            );
            case "IDENTITY_VERIFICATION_ALREADY_USED" -> throw new IllegalStateException(
                    "이미 사용된 본인인증 결과입니다."
            );
            case "IDENTITY_VERIFICATION_EXPIRED" -> throw new IllegalArgumentException(
                    "본인인증 결과가 만료되었습니다. 다시 인증해주세요."
            );
            case "IDENTITY_VERIFICATION_INVALID" -> throw new IllegalArgumentException(
                    "본인인증 결과가 유효하지 않습니다."
            );
            case "INVALID_REQUEST" -> throw new IllegalArgumentException(
                    "회원가입 정보를 확인해주세요."
            );
            case "DUPLICATE_CONFLICT" -> throw new IllegalArgumentException(
                    "Customer account information is already registered."
            );
            default -> throw new IllegalStateException(
                    "Unexpected customer registration result: " + resultCode
            );
        };
    }

    private Long requireOwnerRegistration(
            AuthRegistrationResultDTO result
    ) {
        String resultCode = requireRegistrationResultCode(result);

        return switch (resultCode) {
            case "OK" -> requireRegistrationId(result, "Owner");
            case "FORBIDDEN" -> throw new SecurityException(
                    "Administrator authority is required."
            );
            case "DUPLICATE_LOGIN" -> throw new IllegalArgumentException(
                    "이미 사용 중인 아이디입니다."
            );
            case "DUPLICATE_BUSINESS" -> throw new IllegalArgumentException(
                    "이미 등록된 사업자번호입니다."
            );
            case "DUPLICATE_PHONE" -> throw new IllegalArgumentException(
                    "이미 등록된 전화번호입니다."
            );
            case "DUPLICATE_EMAIL" -> throw new IllegalArgumentException(
                    "이미 등록된 이메일입니다."
            );
            case "DUPLICATE_CONFLICT" -> throw new IllegalArgumentException(
                    "Owner account information is already registered."
            );
            default -> throw new IllegalStateException(
                    "Unexpected owner registration result: " + resultCode
            );
        };
    }

    private String requireRegistrationResultCode(
            AuthRegistrationResultDTO result
    ) {
        if (result == null || result.getResultCode() == null) {
            throw new IllegalStateException(
                    "Registration result was not returned."
            );
        }
        return result.getResultCode();
    }

    private Long requireRegistrationId(
            AuthRegistrationResultDTO result,
            String accountType
    ) {
        if (result.getEntityId() == null) {
            throw new IllegalStateException(
                    accountType + " account was not created."
            );
        }
        return result.getEntityId();
    }

    private String normalizeDigits(
            String value,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        String trimmed = value.trim();

        if (!trimmed.matches("^[0-9-]+$")) {
            throw new IllegalArgumentException(errorMessage);
        }

        String normalized = trimmed.replace("-", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return normalized;
    }

    private String normalizeBusinessNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "사업자번호를 입력해주세요."
            );
        }

        String trimmed = value.trim();

        if (!trimmed.matches(
                "^(?:[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5})$"
        )) {
            throw new IllegalArgumentException(
                    "사업자번호 형식을 확인해주세요."
            );
        }

        String digits = trimmed.replace("-", "");

        return digits.substring(0, 3)
                + "-"
                + digits.substring(3, 5)
                + "-"
                + digits.substring(5);
    }

    private String normalizeEmail(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "이메일을 입력해주세요."
            );
        }

        String normalized = emailAddress
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "이메일 형식을 확인해주세요."
            );
        }

        return normalized;
    }

    private String normalizeSelfReportedGender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^(M|F|N)$")) {
            throw new IllegalArgumentException(
                    "성별을 선택해주세요."
            );
        }
        return normalized;
    }
}
