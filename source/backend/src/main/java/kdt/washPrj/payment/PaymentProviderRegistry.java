package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentProviderOptionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Component
public class PaymentProviderRegistry {

    public static final String PAYAPP = "PAYAPP";
    public static final String KAKAOPAY = "KAKAOPAY";
    public static final String TOSS_PAYMENTS = "TOSSPAYMENTS";

    private final KakaoPayClient kakaoPayClient;
    private final boolean kakaoPayEnabled;
    private final String kakaoPayCid;
    private final String kakaoPayCallbackSecret;
    private final String payAppUserid;
    private final String payAppLinkKey;
    private final String payAppLinkValue;
    private final TossPaymentsClient tossPaymentsClient;
    private final boolean tossPaymentsEnabled;
    private final String tossPaymentsClientKey;
    private final String tossPaymentsCallbackSecret;

    public PaymentProviderRegistry(
            KakaoPayClient kakaoPayClient,
            @Value("${kakaopay.enabled:false}") boolean kakaoPayEnabled,
            @Value("${kakaopay.cid:TC0ONETIME}") String kakaoPayCid,
            @Value("${kakaopay.callback-secret:}") String kakaoPayCallbackSecret,
            @Value("${payapp.userid:}") String payAppUserid,
            @Value("${payapp.link-key:}") String payAppLinkKey,
            @Value("${payapp.link-value:}") String payAppLinkValue,
            TossPaymentsClient tossPaymentsClient,
            @Value("${tosspayments.enabled:false}") boolean tossPaymentsEnabled,
            @Value("${tosspayments.client-key:}") String tossPaymentsClientKey,
            @Value("${tosspayments.callback-secret:}") String tossPaymentsCallbackSecret
    ) {
        this.kakaoPayClient = kakaoPayClient;
        this.kakaoPayEnabled = kakaoPayEnabled;
        this.kakaoPayCid = kakaoPayCid;
        this.kakaoPayCallbackSecret = kakaoPayCallbackSecret;
        this.payAppUserid = payAppUserid;
        this.payAppLinkKey = payAppLinkKey;
        this.payAppLinkValue = payAppLinkValue;
        this.tossPaymentsClient = tossPaymentsClient;
        this.tossPaymentsEnabled = tossPaymentsEnabled;
        this.tossPaymentsClientKey = tossPaymentsClientKey;
        this.tossPaymentsCallbackSecret = tossPaymentsCallbackSecret;
    }

    public List<PaymentProviderOptionDTO> getOptions() {
        return List.of(
                new PaymentProviderOptionDTO(PAYAPP, "일반결제", isPayAppAvailable()),
                new PaymentProviderOptionDTO(KAKAOPAY, "카카오페이", isKakaoPayAvailable()),
                new PaymentProviderOptionDTO(TOSS_PAYMENTS, "토스페이먼츠", isTossPaymentsAvailable())
        );
    }

    public String requireAvailable(String requestedProvider) {
        String provider = normalize(requestedProvider);
        if (provider == null) {
            provider = PAYAPP;
        }

        if (!isAvailable(provider)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    provider + " payment is not configured."
            );
        }
        return provider;
    }

    public boolean isAvailable(String provider) {
        return switch (normalize(provider)) {
            case PAYAPP -> isPayAppAvailable();
            case KAKAOPAY -> isKakaoPayAvailable();
            case TOSS_PAYMENTS -> isTossPaymentsAvailable();
            default -> false;
        };
    }

    public boolean isKakaoPayAvailable() {
        return kakaoPayEnabled
                && kakaoPayClient.hasCredentials()
                && kakaoPayCid != null
                && !kakaoPayCid.isBlank()
                && kakaoPayCallbackSecret != null
                && !kakaoPayCallbackSecret.isBlank();
    }

    public boolean isTossPaymentsAvailable() {
        return tossPaymentsEnabled
                && tossPaymentsClient.hasCredentials()
                && isNotBlank(tossPaymentsClientKey)
                && isNotBlank(tossPaymentsCallbackSecret);
    }

    private boolean isPayAppAvailable() {
        return isNotBlank(payAppUserid)
                && isNotBlank(payAppLinkKey)
                && isNotBlank(payAppLinkValue);
    }

    private String normalize(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
