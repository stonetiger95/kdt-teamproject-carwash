package kdt.washPrj.customer;

import jakarta.validation.Valid;
import kdt.washPrj.customer.dto.ChangePasswordRequestDTO;
import kdt.washPrj.customer.dto.CustomerEmailChangeStartRequestDTO;
import kdt.washPrj.customer.dto.CustomerEmailChangeStartResponseDTO;
import kdt.washPrj.customer.dto.CustomerEmailChangeVerifyRequestDTO;
import kdt.washPrj.customer.dto.CustomerMyPageDTO;
import kdt.washPrj.customer.dto.WithdrawRequestDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerEmailChangeService customerEmailChangeService;

    @GetMapping("/mypage")
    public CustomerMyPageDTO getMyPage(
            Authentication authentication
    ) {

        String loginId = authentication.getName();

        return customerService.getMyPage(loginId);
    }

    @PutMapping("/mypage")
    public CustomerMyPageDTO updateMyPage(
            Authentication authentication,
            @Valid @RequestBody CustomerMyPageDTO request
    ) {

        String loginId =
                authentication.getName();

        return customerService.updateMyPage(
                loginId,
                request
        );
    }

    @PostMapping("/mypage/email-change/start")
    public ResponseEntity<CustomerEmailChangeStartResponseDTO>
    startEmailChange(
            Authentication authentication,
            @Valid @RequestBody CustomerEmailChangeStartRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(customerEmailChangeService.start(
                        authentication.getName(),
                        request
                ));
    }

    @PostMapping("/mypage/email-change/verify")
    public ResponseEntity<CustomerMyPageDTO> verifyEmailChange(
            Authentication authentication,
            @Valid @RequestBody CustomerEmailChangeVerifyRequestDTO request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(customerEmailChangeService.verify(
                        authentication.getName(),
                        request
                ));
    }

    @PutMapping("/password")
    public ResponseEntity<String> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequestDTO request
    ) {
        String loginId = authentication.getName();
        customerService.changePassword(loginId, request);
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
    }
    @PatchMapping("/withdraw")
    public ResponseEntity<Void> withdraw(
            Authentication authentication,
            @Valid @RequestBody WithdrawRequestDTO request
    ){
        String loginId = authentication.getName();

        customerService.withdraw(
                loginId,
                request);
        return ResponseEntity.noContent().build();
    }
}
