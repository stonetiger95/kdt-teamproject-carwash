package kdt.washPrj.payment;

import jakarta.validation.Valid;
import kdt.washPrj.payment.dto.PaymentReconciliationReviewDTO;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentReconciliationController {

    private final PaymentService paymentService;

    public AdminPaymentReconciliationController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{paymentId}/reconciliation-review")
    public PaymentResponseDTO reviewPayAppReconciliation(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentReconciliationReviewDTO request
    ) {
        Authentication authentication = getAuthentication();
        String role = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");

        return paymentService.reviewPayAppReconciliation(
                paymentId,
                request,
                authentication.getName(),
                role
        );
    }

    private Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return authentication;
    }
}
