package kdt.washPrj.auth.recovery;

import java.util.Base64;

final class PasswordRecoveryTestFixtures {

    private PasswordRecoveryTestFixtures() {
    }

    static PasswordRecoveryProperties properties() {
        return properties("Strict", false);
    }

    static PasswordRecoveryProperties properties(
            String cookieSameSite,
            boolean cookieSecure
    ) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        return new PasswordRecoveryProperties(
                true,
                Base64.getEncoder().encodeToString(key),
                300,
                60,
                5,
                600,
                true,
                24,
                "straffic_password_recovery",
                cookieSecure,
                cookieSameSite,
                "phone-channel",
                true,
                "easy-channel",
                true,
                true,
                "smtp.gmail.com",
                587,
                "sender@example.com",
                "app-password",
                "sender@example.com"
        );
    }
}
