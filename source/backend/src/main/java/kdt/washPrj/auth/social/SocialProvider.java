package kdt.washPrj.auth.social;

import java.util.Locale;

public enum SocialProvider {
    GOOGLE("구글"),
    KAKAO("카카오"),
    LINE("LINE");

    private final String displayName;

    SocialProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static SocialProvider fromPath(String value) {
        if (value == null) {
            throw SocialOAuthException.unsupportedProvider();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw SocialOAuthException.unsupportedProvider();
        }
    }
}
