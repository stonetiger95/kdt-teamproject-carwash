package kdt.washPrj.auth.recovery;

public enum PasswordRecoveryMethod {
    EMAIL_OTP,
    PHONE_IDENTITY,
    EASY_AUTH;

    public boolean isIdentityMethod() {
        return this == PHONE_IDENTITY || this == EASY_AUTH;
    }
}
