package kdt.washPrj.auth.recovery;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartResponseDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryVerifyRequestDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryCompleteRequestDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryStartResponseDTO;
import kdt.washPrj.auth.recovery.dto.PasswordRecoverySessionResponseDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetRequestDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetResponseDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-recovery")
public class PasswordRecoveryController {

    private final PortOnePasswordRecoveryService identityService;
    private final EmailPasswordRecoveryService emailService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final PasswordRecoveryCookieFactory cookieFactory;
    private final PasswordRecoveryProperties properties;

    public PasswordRecoveryController(
            PortOnePasswordRecoveryService identityService,
            EmailPasswordRecoveryService emailService,
            PasswordRecoveryService passwordRecoveryService,
            PasswordRecoveryCookieFactory cookieFactory,
            PasswordRecoveryProperties properties
    ) {
        this.identityService = identityService;
        this.emailService = emailService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.cookieFactory = cookieFactory;
        this.properties = properties;
    }

    @PostMapping("/identity/start")
    public ResponseEntity<IdentityRecoveryStartResponseDTO> startIdentity(
            @Valid @RequestBody IdentityRecoveryStartRequestDTO request
    ) {
        return noStore(identityService.start(request));
    }

    @PostMapping("/identity/complete")
    public ResponseEntity<PasswordRecoverySessionResponseDTO>
    completeIdentity(
            @Valid @RequestBody IdentityRecoveryCompleteRequestDTO request
    ) {
        return authorized(identityService.complete(request));
    }

    @PostMapping("/email/start")
    public ResponseEntity<EmailRecoveryStartResponseDTO> startEmail(
            @Valid @RequestBody EmailRecoveryStartRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(emailService.start(request));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<PasswordRecoverySessionResponseDTO> verifyEmail(
            @Valid @RequestBody EmailRecoveryVerifyRequestDTO request
    ) {
        return authorized(emailService.verify(request));
    }

    @GetMapping("/session")
    public ResponseEntity<PasswordRecoverySessionResponseDTO> session(
            HttpServletRequest request
    ) {
        return noStore(passwordRecoveryService.getSession(
                recoveryCookie(request)
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<PasswordResetResponseDTO> reset(
            HttpServletRequest servletRequest,
            @Valid @RequestBody PasswordResetRequestDTO request
    ) {
        PasswordResetResponseDTO response = passwordRecoveryService.reset(
                recoveryCookie(servletRequest),
                request
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.clear().toString()
                )
                .body(response);
    }

    private ResponseEntity<PasswordRecoverySessionResponseDTO> authorized(
            IssuedPasswordRecoverySession issuedSession
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.create(
                                issuedSession.rawToken()
                        ).toString()
                )
                .body(issuedSession.response());
    }

    private String recoveryCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (properties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
