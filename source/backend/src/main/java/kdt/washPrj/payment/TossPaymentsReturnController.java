package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentProviderContextDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/public/payments/tosspayments")
public class TossPaymentsReturnController {

    private final TossPaymentsService tossPaymentsService;
    private final String frontendBaseUrl;
    private final String flutterWebBaseUrl;
    private final String mobileAppBaseUrl;

    public TossPaymentsReturnController(
            TossPaymentsService tossPaymentsService,
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.flutter-web-base-url:${app.frontend-base-url}}") String flutterWebBaseUrl,
            @Value("${app.mobile-app-base-url:straffic://app}") String mobileAppBaseUrl
    ) {
        this.tossPaymentsService = tossPaymentsService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.flutterWebBaseUrl = flutterWebBaseUrl;
        this.mobileAppBaseUrl = mobileAppBaseUrl;
    }

    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> checkout(
            @RequestParam String clientKey,
            @RequestParam String orderId,
            @RequestParam String orderName,
            @RequestParam Integer amount,
            @RequestParam String successUrl,
            @RequestParam String failUrl
    ) {
        if (clientKey.isBlank()
                || orderId.isBlank()
                || orderName.isBlank()
                || amount == null
                || amount <= 0
                || successUrl.isBlank()
                || failUrl.isBlank()) {
            return ResponseEntity.badRequest().body("Invalid Toss Payments checkout request.");
        }

        return ResponseEntity.ok()
                .contentType(new MediaType(
                        "text",
                        "html",
                        StandardCharsets.UTF_8
                ))
                .body(checkoutPage(new TossCheckoutInfo(
                        clientKey,
                        orderId,
                        orderName,
                        amount,
                        successUrl,
                        failUrl
                )));
    }

    @GetMapping("/success")
    public ResponseEntity<Void> success(
            @RequestParam Long paymentId,
            @RequestParam String state,
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Integer amount,
            @RequestParam(required = false) String returnClient
    ) {
        try {
            PaymentProviderContextDTO context = tossPaymentsService.confirm(
                    paymentId,
                    state,
                    paymentKey,
                    orderId,
                    amount
            );
            return redirect(successLocation(context, returnClient));
        } catch (RuntimeException exception) {
            return redirect(failureLocation(paymentId, "TOSS_CONFIRM_FAILED", returnClient));
        }
    }

    @GetMapping("/fail")
    public ResponseEntity<Void> fail(
            @RequestParam Long paymentId,
            @RequestParam String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String returnClient
    ) {
        try {
            PaymentProviderContextDTO context = tossPaymentsService.fail(
                    paymentId,
                    state,
                    code,
                    message
            );
            return redirect(resultLocation(context, code, returnClient));
        } catch (RuntimeException exception) {
            return redirect(failureLocation(paymentId, "TOSS_FAILURE_CALLBACK_ERROR", returnClient));
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
            String code,
            String returnClient
    ) {
        if (context.getCustomerId() != null) {
            if (isFlutterClient(returnClient)) {
                return flutterLocation(context.getPaymentHistoryId(), "failed", code, returnClient);
            }
            return UriComponentsBuilder
                    .fromUriString(frontendBaseUrl)
                    .path("/customer/payments/fail")
                    .queryParam("paymentId", context.getPaymentHistoryId())
                    .queryParam("code", code)
                    .build()
                    .encode()
                    .toUri();
        }
        return nonMemberLocation(context, "failed");
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
            PaymentProviderContextDTO context = tossPaymentsService
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

    private String checkoutPage(TossCheckoutInfo paymentInfo) {
        String clientKey = base64(paymentInfo.clientKey());
        String orderId = base64(paymentInfo.orderId());
        String orderName = base64(paymentInfo.orderName());
        String successUrl = base64(paymentInfo.successUrl());
        String failUrl = base64(paymentInfo.failUrl());
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>토스페이먼츠 결제</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; font-family: Arial, sans-serif; background: #f5f7fa; color: #17202e; }
                    main { min-height: 100vh; display: grid; place-content: center; padding: 24px; text-align: center; }
                    section { width: min(100%%, 420px); padding: 28px 22px; background: white; border: 1px solid #e5e9f0; border-radius: 8px; }
                    h1 { margin: 0 0 10px; font-size: 22px; }
                    p { margin: 0 0 20px; color: #667085; line-height: 1.5; }
                    button { width: 100%%; min-height: 48px; border: 0; border-radius: 6px; background: #3182f6; color: white; font-size: 16px; font-weight: 700; }
                    button:disabled { opacity: .55; }
                    #error { margin-top: 14px; color: #d92d20; }
                  </style>
                  <script src="https://js.tosspayments.com/v2/standard"></script>
                </head>
                <body>
                  <main>
                    <section>
                      <h1>토스페이먼츠 결제</h1>
                      <p>결제창이 자동으로 열리지 않으면 아래 버튼을 눌러주세요.</p>
                      <button id="start" type="button">결제창 열기</button>
                      <div id="error" role="alert"></div>
                    </section>
                  </main>
                  <script>
                    const decode = (value) => new TextDecoder().decode(
                      Uint8Array.from(atob(value), (char) => char.charCodeAt(0))
                    );
                    const info = {
                      clientKey: decode('%s'),
                      orderId: decode('%s'),
                      orderName: decode('%s'),
                      amount: %d,
                      successUrl: decode('%s'),
                      failUrl: decode('%s')
                    };
                    let opening = false;

                    async function openPayment() {
                      if (opening) return;
                      opening = true;
                      const button = document.getElementById('start');
                      const error = document.getElementById('error');
                      button.disabled = true;
                      error.textContent = '';

                      try {
                        const tossPayments = TossPayments(info.clientKey);
                        const payment = tossPayments.payment({
                          customerKey: TossPayments.ANONYMOUS
                        });
                        await payment.requestPayment({
                          method: 'CARD',
                          amount: { currency: 'KRW', value: info.amount },
                          orderId: info.orderId,
                          orderName: info.orderName,
                          successUrl: info.successUrl,
                          failUrl: info.failUrl
                        });
                      } catch (cause) {
                        opening = false;
                        button.disabled = false;
                        error.textContent = cause?.message || '결제창을 열지 못했습니다.';
                      }
                    }

                    document.getElementById('start').addEventListener('click', openPayment);
                    window.addEventListener('load', () => setTimeout(openPayment, 150));
                  </script>
                </body>
                </html>
                """.formatted(
                clientKey,
                orderId,
                orderName,
                paymentInfo.amount(),
                successUrl,
                failUrl
        );
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private record TossCheckoutInfo(
            String clientKey,
            String orderId,
            String orderName,
            Integer amount,
            String successUrl,
            String failUrl
    ) {
    }
}
