package kdt.washPrj.auth.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

@Component
public class PortOneIdentityProperties {

    private final boolean enabled;
    private final String apiUrl;
    private final String apiSecret;
    private final String storeId;
    private final String channelKey;
    private final String masterKey;
    private final String diHmacKey;
    private final int stateTtlSeconds;
    private final int proofTtlSeconds;
    private final boolean requireLive;
    private final boolean testMode;

    public PortOneIdentityProperties(
            @Value("${portone.identity.enabled:false}") boolean enabled,
            @Value("${portone.identity.api-url:https://api.portone.io}") String apiUrl,
            @Value("${portone.identity.api-secret:}") String apiSecret,
            @Value("${portone.identity.store-id:}") String storeId,
            @Value("${portone.identity.channel-key:}") String channelKey,
            @Value("${portone.identity.master-key:}") String masterKey,
            @Value("${portone.identity.di-hmac-key:}") String diHmacKey,
            @Value("${portone.identity.state-ttl-seconds:300}") int stateTtlSeconds,
            @Value("${portone.identity.proof-ttl-seconds:600}") int proofTtlSeconds,
            @Value("${portone.identity.require-live:false}") boolean requireLive,
            @Value("${portone.identity.test-mode:true}") boolean testMode
    ) {
        this.enabled = enabled;
        this.apiUrl = trim(apiUrl);
        this.apiSecret = trim(apiSecret);
        this.storeId = trim(storeId);
        this.channelKey = trim(channelKey);
        this.masterKey = trim(masterKey);
        this.diHmacKey = trim(diHmacKey);
        this.stateTtlSeconds = stateTtlSeconds;
        this.proofTtlSeconds = proofTtlSeconds;
        this.requireLive = requireLive;
        this.testMode = testMode;
    }

    public void requireReady() {
        requireProviderReady();
        if (channelKey.isBlank()) {
            throw unavailable(
                    "PortOne identity verification is not fully configured."
            );
        }
        if (stateTtlSeconds < 60 || stateTtlSeconds > 600
                || proofTtlSeconds < 60 || proofTtlSeconds > 1200) {
            throw unavailable(
                    "PortOne identity verification TTL configuration is invalid."
            );
        }
        if (requireLive && testMode) {
            throw unavailable(
                    "PortOne identity verification mode configuration is inconsistent."
            );
        }
    }

    public void requireProviderReady() {
        if (!enabled) {
            throw unavailable("PortOne identity verification is disabled.");
        }
        if (apiUrl.isBlank()
                || apiSecret.isBlank()
                || storeId.isBlank()
                || masterKey.isBlank()
                || diHmacKey.isBlank()) {
            throw unavailable(
                    "PortOne identity verification is not fully configured."
            );
        }
        decodedMasterKey();
        decodedDiHmacKey();
    }

    public byte[] decodedMasterKey() {
        return decodeKey(
                masterKey,
                "PORTONE_IDENTITY_MASTER_KEY"
        );
    }

    public byte[] decodedDiHmacKey() {
        return decodeKey(
                diHmacKey,
                "PORTONE_IDENTITY_DI_HMAC_KEY"
        );
    }

    private static byte[] decodeKey(String value, String settingName) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("invalid key length");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw unavailable(
                    settingName
                            + " must be a base64-encoded 32-byte key."
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public int getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public int getProofTtlSeconds() {
        return proofTtlSeconds;
    }

    public boolean isRequireLive() {
        return requireLive;
    }

    public boolean isTestMode() {
        return testMode;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                message
        );
    }
}
