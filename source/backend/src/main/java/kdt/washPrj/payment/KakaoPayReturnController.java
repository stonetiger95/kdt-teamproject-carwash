package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentProviderContextDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/public/payments/kakaopay")
public class KakaoPayReturnController {

    private final KakaoPayService kakaoPayService;
    private final String frontendBaseUrl;
    private final String flutterWebBaseUrl;
    private final String mobileAppBaseUrl;

    public KakaoPayReturnController(
            KakaoPayService kakaoPayService,
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.flutter-web-base-url:${app.frontend-base-url}}") String flutterWebBaseUrl,
            @Value("${app.mobile-app-base-url:straffic://app}") String mobileAppBaseUrl
    ) {
        this.kakaoPayService = kakaoPayService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.flutterWebBaseUrl = flutterWebBaseUrl;
        this.mobileAppBaseUrl = mobileAppBaseUrl;
    }

    @GetMapping("/approve")
    public ResponseEntity<Void> approve(
            @RequestParam Long paymentId,
            @RequestParam String state,
            @RequestParam(name = "pg_token") String pgToken,
            @RequestParam(required = false) String returnClient
    ) {
        try {
            PaymentProviderContextDTO context = kakaoPayService.approve(
                    paymentId,
                    state,
                    pgToken
            );
            return redirect(successLocation(context, returnClient));
        } catch (RuntimeException exception) {
            return redirect(failureLocation(paymentId, "KAKAOPAY_APPROVAL_FAILED", returnClient));
        }
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> cancel(
            @RequestParam Long paymentId,
            @RequestParam String state,
            @RequestParam(required = false) String returnClient
    ) {
        try {
            PaymentProviderContextDTO context = kakaoPayService.cancelReadyPayment(
                    paymentId,
                    state
            );
            return redirect(resultLocation(context, "canceled", returnClient));
        } catch (RuntimeException exception) {
            return redirect(failureLocation(paymentId, "KAKAOPAY_CANCEL_FAILED", returnClient));
        }
    }

    @GetMapping("/fail")
    public ResponseEntity<Void> fail(
            @RequestParam Long paymentId,
            @RequestParam String state,
            @RequestParam(required = false) String returnClient
    ) {
        try {
            PaymentProviderContextDTO context = kakaoPayService.failReadyPayment(
                    paymentId,
                    state
            );
            return redirect(resultLocation(context, "failed", returnClient));
        } catch (RuntimeException exception) {
            return redirect(failureLocation(paymentId, "KAKAOPAY_FAILURE_CALLBACK_ERROR", returnClient));
        }
    }

    private URI successLocation(PaymentProviderContextDTO context, String returnClient) {
        if (context.getCustomerId() != null) {
            if (isFlutterClient(returnClient)) {
                return flutterLocation(context.getPaymentHistoryId(), "success", null, returnClient);
            }
            return UriComponentsBuilder
                    .fromUriString(frontendBaseUrl)
                    .path("/customer/payments/success")
                    .queryParam("paymentId", context.getPaymentHistoryId())
                    .build()
                    .encode()
                    .toUri();
        }
        return nonMemberLocation(context, "success");
    }

    private URI resultLocation(
            PaymentProviderContextDTO context,
            String result,
            String returnClient
    ) {
        if (context.getCustomerId() != null) {
            if (isFlutterClient(returnClient)) {
                return flutterLocation(context.getPaymentHistoryId(), result, null, returnClient);
            }
            return UriComponentsBuilder
                    .fromUriString(frontendBaseUrl)
                    .path("/customer/payments/fail")
                    .queryParam("paymentId", context.getPaymentHistoryId())
                    .queryParam("result", result)
                    .build()
                    .encode()
                    .toUri();
        }
        return nonMemberLocation(context, result);
    }

    private URI nonMemberLocation(
            PaymentProviderContextDTO context,
            String result
    ) {
        return UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/non-member-payments/{washHistoryId}")
                .queryParam("paymentId", context.getPaymentHistoryId())
                .queryParam("result", result)
                .buildAndExpand(context.getWashHistoryId())
                .encode()
                .toUri();
    }

    private URI failureLocation(Long paymentId, String code, String returnClient) {
        try {
            PaymentProviderContextDTO context = kakaoPayService
                    .requireContext(paymentId);
            if (context.getCustomerId() == null
                    && context.getWashHistoryId() != null) {
                return nonMemberLocation(context, "failed");
            }
        } catch (RuntimeException ignored) {
            // Fall back to the member failure page when context lookup fails.
        }
        if (isFlutterClient(returnClient)) {
            return flutterLocation(paymentId, "failed", code, returnClient);
        }
        return UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/customer/payments/fail")
                .queryParam("paymentId", paymentId)
                .queryParam("code", code)
                .build()
                .encode()
                .toUri();
    }

    private URI flutterLocation(
            Long paymentId,
            String result,
            String code,
            String returnClient
    ) {
        return UriComponentsBuilder
                .fromUriString(clientBaseUrl(returnClient))
                .path("/payment-result")
                .queryParam("paymentId", paymentId)
                .queryParam("result", result)
                .queryParamIfPresent("code", java.util.Optional.ofNullable(code))
                .build()
                .encode()
                .toUri();
    }

    private boolean isFlutterClient(String returnClient) {
        return "FLUTTER_WEB".equalsIgnoreCase(returnClient)
                || "MOBILE".equalsIgnoreCase(returnClient);
    }

    private String clientBaseUrl(String returnClient) {
        return "MOBILE".equalsIgnoreCase(returnClient)
                ? mobileAppBaseUrl
                : flutterWebBaseUrl;
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(location)
                .build();
    }
}
