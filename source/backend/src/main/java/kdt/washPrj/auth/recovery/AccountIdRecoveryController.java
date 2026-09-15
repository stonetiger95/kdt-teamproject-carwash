package kdt.washPrj.auth.recovery;

import jakarta.validation.Valid;
import kdt.washPrj.auth.recovery.dto.AccountIdEmailStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdIdentityCompleteRequestDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdIdentityStartRequestDTO;
import kdt.washPrj.auth.recovery.dto.AccountIdRecoveryResponseDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryStartResponseDTO;
import kdt.washPrj.auth.recovery.dto.EmailRecoveryVerifyRequestDTO;
import kdt.washPrj.auth.recovery.dto.IdentityRecoveryStartResponseDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/account-id-recovery")
public class AccountIdRecoveryController {

    private final PortOneAccountIdRecoveryService identityService;
    private final EmailAccountIdRecoveryService emailService;

    public AccountIdRecoveryController(
            PortOneAccountIdRecoveryService identityService,
            EmailAccountIdRecoveryService emailService
    ) {
        this.identityService = identityService;
        this.emailService = emailService;
    }

    @PostMapping("/identity/start")
    public ResponseEntity<IdentityRecoveryStartResponseDTO> startIdentity(
            @Valid @RequestBody AccountIdIdentityStartRequestDTO request
    ) {
        return noStore(identityService.start(request));
    }

    @PostMapping("/identity/complete")
    public ResponseEntity<AccountIdRecoveryResponseDTO> completeIdentity(
            @Valid @RequestBody AccountIdIdentityCompleteRequestDTO request
    ) {
        return noStore(identityService.complete(request));
    }

    @PostMapping("/email/start")
    public ResponseEntity<EmailRecoveryStartResponseDTO> startEmail(
            @Valid @RequestBody AccountIdEmailStartRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(emailService.start(request));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<AccountIdRecoveryResponseDTO> verifyEmail(
            @Valid @RequestBody EmailRecoveryVerifyRequestDTO request
    ) {
        return noStore(emailService.verify(request));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
