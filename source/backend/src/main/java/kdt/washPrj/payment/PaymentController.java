package kdt.washPrj.payment;

import jakarta.validation.Valid;
import kdt.washPrj.payment.dto.PaymentCancelDTO;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import kdt.washPrj.payment.dto.PaymentWaitingResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/checkout-default")
    public PaymentWaitingDTO getCheckoutDefault() {

        return paymentService.getCheckoutDefault(getLoginId(), getRole());
    }

    @PostMapping("/waiting")
    public PaymentWaitingResponseDTO waitingPayment(@Valid @RequestBody PaymentWaitingDTO requestDTO) {
        return paymentService.waitingPayment(requestDTO, getLoginId(), getRole());
    }

    @PostMapping(
            value = "/payapp/feedback",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String payAppFeedback(@RequestParam Map<String, String> params) {
        return paymentService.handlePayAppFeedback(params);
    }

    @GetMapping
    public List<PaymentResponseDTO> getPaymentList(
            @Valid @ModelAttribute PaymentSearchCondition condition
    ) {
        return paymentService.getPaymentList(
                getLoginId(),
                getRole(),
                condition
        );
    }

    @GetMapping("/{paymentId}")
    public PaymentResponseDTO getPayment(@PathVariable Long paymentId) {
        return paymentService.getPayment(paymentId, getLoginId(), getRole());
    }

    @PostMapping("/{paymentId}/cancel")
    public int cancelPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentCancelDTO requestDTO
    ) {
        return paymentService.cancelPayment(paymentId, requestDTO, getLoginId(), getRole());
    }

    private String getLoginId() {
        return getAuthentication().getName();
    }

    private String getRole() {
        return getAuthentication()
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
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
