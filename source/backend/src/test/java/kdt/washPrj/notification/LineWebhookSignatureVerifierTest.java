package kdt.washPrj.notification;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineWebhookSignatureVerifierTest {

    @Test
    void acceptsOnlySignatureMadeWithConfiguredChannelSecret() throws Exception {
        LineMessagingProperties properties = new LineMessagingProperties();
        properties.setChannelSecret("test-channel-secret");
        LineWebhookSignatureVerifier verifier = new LineWebhookSignatureVerifier(properties);
        String body = "{\"events\":[]}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                properties.getChannelSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(verifier.verify(body, signature));
        assertFalse(verifier.verify(body + " ", signature));
        assertFalse(verifier.verify(body, "invalid"));
    }
}
