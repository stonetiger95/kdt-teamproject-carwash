package kdt.washPrj.auth.social;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/social")
@RequiredArgsConstructor
public class SocialAccountController {

    private final SocialLoginService socialLoginService;

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(
            @PathVariable String provider,
            Authentication authentication
    ) {
        socialLoginService.unlink(provider, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
