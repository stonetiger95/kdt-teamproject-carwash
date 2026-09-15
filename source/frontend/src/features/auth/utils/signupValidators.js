const LOGIN_ID_PATTERN = /^[A-Za-z0-9]{1,50}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const GENDER_PATTERN = /^[MFN]$/
const KOREAN_MOBILE_PATTERN = /^01[016789]\d{7,8}$/
const IDENTITY_VERIFICATION_ID_PATTERN = /^[A-Za-z0-9]{16,40}$/

export function sanitizeLoginId(value) {
    return String(value ?? "").replace(/[^A-Za-z0-9]/g, "")
}

export function isValidLoginId(value) {
    return LOGIN_ID_PATTERN.test(String(value ?? ""))
}

export function isValidPassword(value) {
    const password = String(value ?? "")

    return password.length >= 4
        && password.length <= 72
        && password.trim().length > 0
}

export function isValidEmailAddress(value) {
    const emailAddress = String(value ?? "")

    return emailAddress.length > 0
        && emailAddress.length <= 100
        && EMAIL_PATTERN.test(emailAddress)
}

export function isValidGender(value) {
    return GENDER_PATTERN.test(String(value ?? ""))
}

export function isValidKoreanMobileNumber(value) {
    return KOREAN_MOBILE_PATTERN.test(
        String(value ?? "").replace(/\D/g, "")
    )
}

export function splitKoreanMobileNumber(value) {
    const digits = String(value ?? "").replace(/\D/g, "")

    if (!isValidKoreanMobileNumber(digits)) {
        return ["", "", ""]
    }

    return [
        digits.slice(0, 3),
        digits.slice(3, -4),
        digits.slice(-4)
    ]
}

export function isValidIdentityVerificationId(value) {
    return IDENTITY_VERIFICATION_ID_PATTERN.test(
        String(value ?? "")
    )
}
