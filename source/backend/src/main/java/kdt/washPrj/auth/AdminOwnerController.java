package kdt.washPrj.auth;

import jakarta.validation.Valid;
import kdt.washPrj.auth.dto.OwnerRegisterRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/owners")
@RequiredArgsConstructor
public class AdminOwnerController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<String> registerOwner(
            Authentication authentication,
            @Valid @RequestBody OwnerRegisterRequestDTO request
    ) {
        authService.registerOwner(authentication.getName(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("업주 등록이 완료되었습니다.");
    }
}
