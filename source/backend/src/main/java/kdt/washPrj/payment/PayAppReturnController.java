package kdt.washPrj.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/public/payments/payapp/return")
public class PayAppReturnController {

    private final String frontendBaseUrl;
    private final String flutterWebBaseUrl;
    private final String mobileAppBaseUrl;

    public PayAppReturnController(
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.flutter-web-base-url:${app.frontend-base-url}}") String flutterWebBaseUrl,
            @Value("${app.mobile-app-base-url:straffic://app}") String mobileAppBaseUrl
    ) {
        this.frontendBaseUrl = frontendBaseUrl;
        this.flutterWebBaseUrl = flutterWebBaseUrl;
        this.mobileAppBaseUrl = mobileAppBaseUrl;
    }

    @RequestMapping(
            value = "/customer",
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    public ResponseEntity<Void> returnCustomerPayment(
            @RequestParam Long paymentId,
            @RequestParam(required = false) String returnClient
    ) {
        if (isFlutterClient(returnClient)) {
            URI location = UriComponentsBuilder
                    .fromUriString(clientBaseUrl(returnClient))
                    .path("/payment-result")
                    .queryParam("paymentId", paymentId)
                    .queryParam("result", "success")
                    .build()
                    .encode()
                    .toUri();
            return redirect(location);
        }
        URI location = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/customer/payments/success")
                .queryParam("paymentId", paymentId)
                .build()
                .encode()
                .toUri();

        return redirect(location);
    }

    @RequestMapping(
            value = "/non-member",
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    public ResponseEntity<Void> returnNonMemberPayment(
            @RequestParam Long washHistoryId,
            @RequestParam(required = false) Long paymentId
    ) {
        UriComponentsBuilder locationBuilder = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/non-member-payments/{washHistoryId}")
                .queryParamIfPresent(
                        "paymentId",
                        java.util.Optional.ofNullable(paymentId)
                );

        URI location = locationBuilder
                .buildAndExpand(washHistoryId)
                .encode()
                .toUri();

        return redirect(location);
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
