export const PASSWORD_RECOVERY_METHOD = Object.freeze({
    PHONE_IDENTITY: "PHONE_IDENTITY",
    EASY_AUTH: "EASY_AUTH",
    EMAIL_OTP: "EMAIL_OTP"
})

export const IDENTITY_PASSWORD_RECOVERY_METHODS = Object.freeze([
    PASSWORD_RECOVERY_METHOD.PHONE_IDENTITY,
    PASSWORD_RECOVERY_METHOD.EASY_AUTH
])

export function isIdentityPasswordRecoveryMethod(value) {
    return IDENTITY_PASSWORD_RECOVERY_METHODS.includes(value)
}

export function isValidPasswordRecoveryPassword(value) {
    const password = String(value ?? "")
    const byteLength = new TextEncoder().encode(password).length

    return password.length >= 8
        && password.length <= 72
        && byteLength <= 72
        && password.trim().length > 0
}
