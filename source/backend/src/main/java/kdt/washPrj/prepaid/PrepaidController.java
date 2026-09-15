package kdt.washPrj.prepaid;

import jakarta.validation.Valid;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.prepaid.dto.BillingCardDTO;
import kdt.washPrj.prepaid.dto.BillingCardPayAppReadyDTO;
import kdt.washPrj.prepaid.dto.BillingCardPayAppRequestDTO;
import kdt.washPrj.prepaid.dto.BillingCardRegisterRequestDTO;
import kdt.washPrj.prepaid.dto.BillingCardTestRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargeRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargeResponseDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.prepaid.dto.PrepaidWalletDTO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/prepaid")
public class PrepaidController {

    private final PrepaidService prepaidService;

    public PrepaidController(PrepaidService prepaidService) {
        this.prepaidService = prepaidService;
    }

    @GetMapping("/wallet")
    public PrepaidWalletDTO getWallet() {
        return prepaidService.getWallet(getLoginId(), getRole());
    }

    @GetMapping("/billing-cards")
    public List<BillingCardDTO> getBillingCards() {
        return prepaidService.getBillingCards(getLoginId(), getRole());
    }

    @PostMapping("/billing-cards")
    public BillingCardDTO registerBillingCard(
            @Valid @RequestBody BillingCardRegisterRequestDTO requestDTO
    ) {
        return prepaidService.registerBillingCard(requestDTO, getLoginId(), getRole());
    }

    @PostMapping("/billing-cards/test")
    public BillingCardDTO registerTestBillingCard(
            @Valid @RequestBody(required = false) BillingCardTestRequestDTO requestDTO
    ) {
        return prepaidService.registerTestBillingCard(
                requestDTO,
                getLoginId(),
                getRole()
        );
    }

    @PostMapping("/billing-cards/payapp/request")
    public BillingCardPayAppReadyDTO createPayAppBillingAuthRequest(
            @Valid @RequestBody(required = false) BillingCardPayAppRequestDTO requestDTO
    ) {
        return prepaidService.createPayAppBillingAuthRequest(
                requestDTO,
                getLoginId(),
                getRole()
        );
    }

    @RequestMapping(
            value = "/billing-cards/payapp/callback",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE
    )
    public String completePayAppBillingAuth(
            @RequestParam Map<String, String> params
    ) {
        return prepaidService.completePayAppBillingAuth(params);
    }

    @DeleteMapping("/billing-cards/{payAppCardId}")
    public void deleteBillingCard(@PathVariable Long payAppCardId) {
        prepaidService.deleteBillingCard(payAppCardId, getLoginId(), getRole());
    }

    @PostMapping("/charges")
    public PrepaidChargeResponseDTO charge(@Valid @RequestBody PrepaidChargeRequestDTO requestDTO) {
        return prepaidService.charge(requestDTO, getLoginId(), getRole());
    }

    @PostMapping("/charges/{paymentHistoryId}/abandon")
    public PaymentResponseDTO abandonCharge(
            @PathVariable Long paymentHistoryId
    ) {
        return prepaidService.abandonCharge(
                paymentHistoryId,
                getLoginId(),
                getRole()
        );
    }

    @PostMapping("/uses")
    public PrepaidUseResponseDTO use(@Valid @RequestBody PrepaidUseRequestDTO requestDTO) {
        return prepaidService.use(requestDTO, getLoginId(), getRole());
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

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return authentication;
    }

}
