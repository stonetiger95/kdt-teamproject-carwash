package kdt.washPrj.auth.recovery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class PasswordRecoveryCrypto {

    private static final String OTP_DOMAIN =
            "straffic:password-recovery:otp:v1";
    private static final String PROFILE_EMAIL_OTP_DOMAIN =
            "straffic:profile-email-change:otp:v1";
    private static final String RESET_TOKEN_DOMAIN =
            "straffic:password-recovery:reset-token:v1";
    private static final String STATE_DOMAIN =
            "straffic:password-recovery:state:v1";
    private static final String SUBJECT_DOMAIN =
            "straffic:password-recovery:subject:v1";
    private static final String ACCOUNT_ID_OTP_DOMAIN =
            "straffic:account-id-recovery:otp:v1";
    private static final String ACCOUNT_ID_STATE_DOMAIN =
            "straffic:account-id-recovery:state:v1";
    private static final String ACCOUNT_ID_SUBJECT_DOMAIN =
            "straffic:account-id-recovery:subject:v1";

    private final PasswordRecoveryProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public PasswordRecoveryCrypto(PasswordRecoveryProperties properties) {
        this(properties, new SecureRandom());
    }

    PasswordRecoveryCrypto(
            PasswordRecoveryProperties properties,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public String generateOtp() {
        properties.requireReady();
        return String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );
    }

    public String generateResetToken() {
        properties.requireReady();
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token);
    }

    public String hmacOtp(UUID challengeId, String code) {
        if (challengeId == null
                || code == null
                || !code.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("인증번호 형식을 확인해주세요.");
        }
        return hmacHex(
                OTP_DOMAIN,
                challengeId + ":" + code
        );
    }

    public String hmacProfileEmailOtp(UUID challengeId, String code) {
        if (challengeId == null
                || code == null
                || !code.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("인증번호 형식을 확인해주세요.");
        }
        return hmacHex(
                PROFILE_EMAIL_OTP_DOMAIN,
                challengeId + ":" + code
        );
    }

    public String hmacResetToken(String token) {
        if (token == null
                || token.isBlank()
                || token.length() > 256) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 인증이 유효하지 않습니다."
            );
        }
        return hmacHex(RESET_TOKEN_DOMAIN, token);
    }

    public String hmacSubject(String normalizedLoginId) {
        if (normalizedLoginId == null
                || !normalizedLoginId.matches("^[A-Za-z0-9]+$")
                || normalizedLoginId.length() > 50) {
            throw new IllegalArgumentException("아이디 형식을 확인해주세요.");
        }
        return hmacHex(SUBJECT_DOMAIN, normalizedLoginId);
    }

    public String hmacAccountIdOtp(UUID challengeId, String code) {
        if (challengeId == null
                || code == null
                || !code.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("인증번호 형식을 확인해주세요.");
        }
        return hmacHex(
                ACCOUNT_ID_OTP_DOMAIN,
                challengeId + ":" + code
        );
    }

    public String hmacAccountIdSubject(String normalizedSubject) {
        if (normalizedSubject == null
                || normalizedSubject.isBlank()
                || normalizedSubject.length() > 200) {
            throw new IllegalArgumentException("아이디 찾기 요청 형식을 확인해주세요.");
        }
        return hmacHex(ACCOUNT_ID_SUBJECT_DOMAIN, normalizedSubject);
    }

    byte[] signState(byte[] payload) {
        properties.requireReady();
        return hmac(derivedKey(STATE_DOMAIN), payload);
    }

    byte[] signAccountIdState(byte[] payload) {
        properties.requireReady();
        return hmac(derivedKey(ACCOUNT_ID_STATE_DOMAIN), payload);
    }

    private String hmacHex(String domain, String value) {
        properties.requireReady();
        return HexFormat.of().formatHex(hmac(
                derivedKey(domain),
                value.getBytes(StandardCharsets.UTF_8)
        ));
    }

    private byte[] derivedKey(String domain) {
        return hmac(
                properties.decodedMasterKey(),
                domain.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "비밀번호 복구 암호화 기능을 사용할 수 없습니다."
            );
        }
    }
}
