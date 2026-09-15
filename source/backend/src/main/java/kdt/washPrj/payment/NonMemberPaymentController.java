package kdt.washPrj.payment;

import jakarta.validation.Valid;
import kdt.washPrj.nonMember.NonMemberAccessService;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import kdt.washPrj.payment.dto.PaymentWaitingResponseDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/payments")
public class NonMemberPaymentController {

    private final PaymentService paymentService;
    private final NonMemberAccessService nonMemberAccessService;

    public NonMemberPaymentController(
            PaymentService paymentService,
            NonMemberAccessService nonMemberAccessService
    ) {
        this.paymentService = paymentService;
        this.nonMemberAccessService = nonMemberAccessService;
    }

    @GetMapping(
            "/checkout-default/{washHistoryId}"
    )
    public PaymentWaitingDTO
    getCheckoutDefault(
            @PathVariable Long washHistoryId,
            @RequestHeader(
                    value = NonMemberAccessService.ACCESS_HEADER,
                    required = false
            ) String accessToken
    ) {
        nonMemberAccessService.requireWashHistoryAccess(
                accessToken,
                washHistoryId
        );

        return paymentService
                .getNonMemberCheckoutDefault(
                        washHistoryId
                );
    }

    @PostMapping("/waiting")
    public PaymentWaitingResponseDTO
    waitingPayment(
            @Valid @RequestBody
            PaymentWaitingDTO requestDTO,
            @RequestHeader(
                    value = NonMemberAccessService.ACCESS_HEADER,
                    required = false
            ) String accessToken
    ) {
        nonMemberAccessService.requireWashHistoryAccess(
                accessToken,
                requestDTO == null
                        ? null
                        : requestDTO.getWashHistoryId()
        );

        return paymentService
                .waitingNonMemberPayment(
                        requestDTO
                );
    }

    @PostMapping("/{paymentId}/abandon")
    public PaymentResponseDTO abandonPayment(
            @PathVariable Long paymentId,
            @RequestParam Long washHistoryId,
            @RequestHeader(
                    value = NonMemberAccessService.ACCESS_HEADER,
                    required = false
            ) String accessToken
    ) {
        nonMemberAccessService.requireWashHistoryAccess(
                accessToken,
                washHistoryId
        );

        return paymentService.abandonNonMemberPayment(
                paymentId,
                washHistoryId
        );
    }

    @GetMapping(
            "/wash-history/{washHistoryId}"
    )
    public List<PaymentResponseDTO>
    getPaymentList(
            @PathVariable Long washHistoryId,
            @RequestHeader(
                    value = NonMemberAccessService.ACCESS_HEADER,
                    required = false
            ) String accessToken
    ) {
        nonMemberAccessService.requireWashHistoryAccess(
                accessToken,
                washHistoryId
        );

        return paymentService
                .getNonMemberPaymentList(
                        washHistoryId
                );
    }
}
