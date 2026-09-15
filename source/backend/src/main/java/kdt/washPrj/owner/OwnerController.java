package kdt.washPrj.owner;

import jakarta.validation.Valid;
import kdt.washPrj.owner.dto.ChangePasswordRequestDTO;
import kdt.washPrj.owner.dto.OwnerMyPageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;


    @GetMapping("/mypage")
    public OwnerMyPageDTO getMyPage(
            Authentication authentication
    ) {

        String loginId =
                authentication.getName();


        return ownerService.getMyPage(
                loginId
        );
    }


    @PutMapping("/mypage")
    public OwnerMyPageDTO updateMyPage(
            Authentication authentication,
            @Valid @RequestBody OwnerMyPageDTO request
    ) {

        String loginId =
                authentication.getName();


        return ownerService.updateMyPage(
                loginId,
                request
        );
    }

    @PutMapping("/password")
    public ResponseEntity<String> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequestDTO request
    ) {
        String loginId = authentication.getName();
        ownerService.changePassword(loginId, request);
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
    }
}
