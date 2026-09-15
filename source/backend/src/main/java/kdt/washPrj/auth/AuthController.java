package kdt.washPrj.auth;

import jakarta.validation.Valid;
import kdt.washPrj.auth.dto.*;
import kdt.washPrj.auth.identity.PortOneIdentityService;
import kdt.washPrj.auth.social.SocialLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SERVER_INSTANCE_ID = UUID.randomUUID().toString();

    private final AuthService authService;
    private final PortOneIdentityService portOneIdentityService;
    private final NaverLoginService naverLoginService;
    private final NaverOAuthClient naverOAuthClient;
    private final SocialLoginService socialLoginService;

    @GetMapping("/server-instance")

    public Map<String, String> getServerInstance() {

        return Map.of(
                "instanceId",
                SERVER_INSTANCE_ID
        );
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {

        return requireSuccessfulLogin(authService.login(request));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<LoginResponseDTO> adminLogin(
            @Valid @RequestBody LoginRequestDTO request
    ) {

        return requireSuccessfulLogin(authService.loginAdmin(request));
    }

    private ResponseEntity<LoginResponseDTO> requireSuccessfulLogin(
            LoginResponseDTO response
    ) {

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "아이디 또는 비밀번호를 확인해주세요."
            );
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-login-id")
    public ResponseEntity<Boolean> checkLoginId(
            @RequestParam String loginId
    ) {
        return ResponseEntity.ok(
                authService.isLoginIdAvailable(loginId)
        );
    }

    @PostMapping("/check-phone")
    public ResponseEntity<Boolean> checkPhone(
            @Valid @RequestBody AvailabilityCheckRequestDTO request
    ) {
        return ResponseEntity.ok(
                authService.isPhoneAvailable(
                        request == null ? null : request.getValue()
                )
        );
    }

    @PostMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(
            @Valid @RequestBody AvailabilityCheckRequestDTO request
    ) {
        return ResponseEntity.ok(
                authService.isEmailAvailable(
                        request == null ? null : request.getValue()
                )
        );
    }

    @PostMapping("/signup")
    public ResponseEntity<LoginResponseDTO> signup(
            @Valid @RequestBody SignupRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(authService.signup(request));
    }

    @PostMapping("/identity-verifications/start")
    public ResponseEntity<IdentityVerificationStartResponseDTO>
    startIdentityVerification() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portOneIdentityService.start());
    }

    @PostMapping("/identity-verifications/complete")
    public ResponseEntity<IdentityVerificationCompleteResponseDTO>
    completeIdentityVerification(
            @Valid @RequestBody
            IdentityVerificationCompleteRequestDTO request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portOneIdentityService.complete(request));
    }

    @GetMapping("/naver/authorize")
    public NaverAuthorizationResponseDTO authorizeNaver(
            @RequestParam(required = false) String client
    ) {
        return naverLoginService.createAuthorization(isAppClient(client));
    }

    @GetMapping("/naver/callback")
    public NaverCallbackResponseDTO callbackNaver(
            @RequestParam(required = false) String code,
            @RequestParam String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        return naverLoginService.handleCallback(
                code,
                state,
                error,
                errorDescription
        );
    }

    @PostMapping("/naver/signup")
    public ResponseEntity<LoginResponseDTO> signupNaver(
            @Valid @RequestBody NaverSignupRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(naverLoginService.completeSignup(request));
    }

    @GetMapping("/social/providers")
    public Map<String, Boolean> socialProviders() {
        return Map.of(
                "naver", naverOAuthClient.isConfigured(),
                "google", socialLoginService.isConfigured("google"),
                "kakao", socialLoginService.isConfigured("kakao")
        );
    }

    @GetMapping("/social/{provider}/authorize")
    public SocialAuthorizationResponseDTO authorizeSocial(
            @PathVariable String provider,
            @RequestParam(required = false) String client
    ) {
        return socialLoginService.createAuthorization(
                provider,
                isAppClient(client)
        );
    }

    @GetMapping("/social/{provider}/callback")
    public SocialCallbackResponseDTO callbackSocial(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false)
            String errorDescription,
            @RequestParam(required = false) String client
    ) {
        return socialLoginService.handleCallback(
                provider,
                code,
                state,
                error,
                errorDescription,
                isAppClient(client)
        );
    }

    @PostMapping("/social/{provider}/signup")
    public ResponseEntity<LoginResponseDTO> signupSocial(
            @PathVariable String provider,
            @Valid @RequestBody SocialSignupRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(socialLoginService.completeSignup(provider, request));
    }

    private boolean isAppClient(String client) {
        return "app".equalsIgnoreCase(client);
    }
}
