package kdt.washPrj.notification;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class LineWebhookSignatureVerifier {

    private final LineMessagingProperties properties;

    public LineWebhookSignatureVerifier(LineMessagingProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return hasText(properties.getChannelSecret());
    }

    public boolean verify(String rawBody, String suppliedSignature) {
        if (!isConfigured()
                || rawBody == null
                || !hasText(suppliedSignature)) {
            return false;
        }

        try {
            byte[] expected = hmac(rawBody);
            byte[] supplied = Base64.getDecoder().decode(suppliedSignature.trim());
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] hmac(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getChannelSecret().trim()
                            .getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
