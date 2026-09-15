package kdt.washPrj.notification;

import kdt.washPrj.auth.AuthMapper;
import kdt.washPrj.notification.dto.LineLinkStartDTO;
import kdt.washPrj.notification.dto.LineLinkStatusDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications/line")
public class LineAccountController {

    private final BusinessNotificationMapper notificationMapper;
    private final LineNotificationClient lineClient;
    private final LineWebhookSignatureVerifier signatureVerifier;
    private final LineLinkCodeStore linkCodeStore;
    private final LineMessagingProperties properties;
    private final AuthMapper authMapper;

    public LineAccountController(
            BusinessNotificationMapper notificationMapper,
            LineNotificationClient lineClient,
            LineWebhookSignatureVerifier signatureVerifier,
            LineLinkCodeStore linkCodeStore,
            LineMessagingProperties properties,
            AuthMapper authMapper
    ) {
        this.notificationMapper = notificationMapper;
        this.lineClient = lineClient;
        this.signatureVerifier = signatureVerifier;
        this.linkCodeStore = linkCodeStore;
        this.properties = properties;
        this.authMapper = authMapper;
    }

    @GetMapping("/status")
    public LineLinkStatusDTO status(Authentication authentication) {
        requireCustomerOrOwner(authentication);
        boolean available = isAvailable();
        return new LineLinkStatusDTO(
                available,
                available && notificationMapper.isLineLinked(authentication.getName())
        );
    }

    @GetMapping("/authorize")
    public LineLinkStartDTO authorize(Authentication authentication) {
        requireCustomerOrOwner(authentication);
        requireAvailable();
        LineLinkCodeStore.IssuedCode issued = linkCodeStore.issue(authentication.getName());
        return new LineLinkStartDTO(
                "연결 " + issued.code(),
                properties.getOfficialAccountUrl().trim(),
                issued.expiresInSeconds()
        );
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink(Authentication authentication) {
        requireCustomerOrOwner(authentication);
        if (authMapper.unlinkSocialAccount(authentication.getName(), "LINE") == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "연결된 LINE 계정이 없습니다."
            );
        }
        return ResponseEntity.noContent().build();
    }

    private void requireCustomerOrOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_CUSTOMER".equals(role) || "ROLE_OWNER".equals(role));
        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "고객 또는 업주만 LINE 알림을 연결할 수 있습니다."
            );
        }
    }

    private boolean isAvailable() {
        return lineClient.isConfigured()
                && signatureVerifier.isConfigured()
                && properties.getOfficialAccountUrl() != null
                && !properties.getOfficialAccountUrl().isBlank();
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LINE Messaging API 환경설정이 완료되지 않았습니다."
            );
        }
    }
}
