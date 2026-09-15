package kdt.washPrj.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/social/naver")
@RequiredArgsConstructor
public class NaverAccountController {

    private final NaverLoginService naverLoginService;

    @DeleteMapping
    public ResponseEntity<Void> unlink(Authentication authentication) {
        naverLoginService.unlink(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
