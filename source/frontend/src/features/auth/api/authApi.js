import axiosClient from "@/shared/api/axiosClient"

export function getServerInstance() {

    return axiosClient.get(
        "/api/auth/server-instance"
    )
}

export function login(loginInfo) {
    return axiosClient.post("/api/auth/login", loginInfo)
}

export function adminLogin(loginInfo) {
    return axiosClient.post("/api/auth/admin/login", loginInfo)
}

export function logout() {
    return axiosClient.post("/api/auth/logout")
}

export function getSessionStatus() {
    return axiosClient.get("/api/session/status")
}

const PASSWORD_RECOVERY_REQUEST_CONFIG = {
    withCredentials: true
}

export function startPasswordRecoveryIdentity(recoveryInfo) {
    return axiosClient.post(
        "/api/auth/password-recovery/identity/start",
        recoveryInfo,
        PASSWORD_RECOVERY_REQUEST_CONFIG
    )
}

export function completePasswordRecoveryIdentity(verificationInfo) {
    return axiosClient.post(
        "/api/auth/password-recovery/identity/complete",
        verificationInfo,
        {
            ...PASSWORD_RECOVERY_REQUEST_CONFIG,
            // PortOne recommends at least a 60-second server read timeout.
            timeout: 70000
        }
    )
}

export function startPasswordRecoveryEmail(recoveryInfo) {
    return axiosClient.post(
        "/api/auth/password-recovery/email/start",
        recoveryInfo,
        PASSWORD_RECOVERY_REQUEST_CONFIG
    )
}

export function verifyPasswordRecoveryEmail(verificationInfo) {
    return axiosClient.post(
        "/api/auth/password-recovery/email/verify",
        verificationInfo,
        PASSWORD_RECOVERY_REQUEST_CONFIG
    )
}

export function getPasswordRecoverySession() {
    return axiosClient.get(
        "/api/auth/password-recovery/session",
        PASSWORD_RECOVERY_REQUEST_CONFIG
    )
}

export function resetForgottenPassword(passwordInfo) {
    return axiosClient.post(
        "/api/auth/password-recovery/reset",
        passwordInfo,
        PASSWORD_RECOVERY_REQUEST_CONFIG
    )
}

export function startAccountIdRecoveryIdentity(recoveryInfo) {
    return axiosClient.post(
        "/api/auth/account-id-recovery/identity/start",
        recoveryInfo
    )
}

export function completeAccountIdRecoveryIdentity(verificationInfo) {
    return axiosClient.post(
        "/api/auth/account-id-recovery/identity/complete",
        verificationInfo,
        { timeout: 70000 }
    )
}

export function startAccountIdRecoveryEmail(recoveryInfo) {
    return axiosClient.post(
        "/api/auth/account-id-recovery/email/start",
        recoveryInfo
    )
}

export function verifyAccountIdRecoveryEmail(verificationInfo) {
    return axiosClient.post(
        "/api/auth/account-id-recovery/email/verify",
        verificationInfo
    )
}

export function checkLoginId(loginId) {
    return axiosClient.get(
        "/api/auth/check-login-id",
        {
            params: { loginId }
        }
    )
}

export function checkPhone(phone) {
    return axiosClient.post(
        "/api/auth/check-phone",
        {
            value: phone
        }
    )
}

export function checkEmail(emailAddress) {
    return axiosClient.post(
        "/api/auth/check-email",
        {
            value: emailAddress
        }
    )
}

export function startIdentityVerification() {
    return axiosClient.post(
        "/api/auth/identity-verifications/start"
    )
}

export function completeIdentityVerification(verificationInfo) {
    return axiosClient.post(
        "/api/auth/identity-verifications/complete",
        verificationInfo,
        {
            // PortOne recommends at least a 60-second server read timeout.
            timeout: 70000
        }
    )
}

export function signup(signupInfo) {
    return axiosClient.post("/api/auth/signup", signupInfo)
}

export function getNaverAuthorization() {
    return axiosClient.get("/api/auth/naver/authorize")
}

export function completeNaverCallback(params) {
    return axiosClient.get("/api/auth/naver/callback", { params })
}

export function completeNaverSignup(signupInfo) {
    return axiosClient.post("/api/auth/naver/signup", signupInfo)
}

export function unlinkNaverAccount() {
    return axiosClient.delete("/api/customer/social/naver")
}

export function getSocialProviders() {
    return axiosClient.get("/api/auth/social/providers")
}

export function getSocialAuthorization(provider) {
    return axiosClient.get(`/api/auth/social/${provider}/authorize`)
}

export function completeSocialCallback(provider, params) {
    return axiosClient.get(
        `/api/auth/social/${provider}/callback`,
        { params }
    )
}

export function completeSocialSignup(provider, signupInfo) {
    return axiosClient.post(
        `/api/auth/social/${provider}/signup`,
        signupInfo
    )
}

export function unlinkSocialAccount(provider) {
    return axiosClient.delete(`/api/customer/social/${provider}`)
}

export function getLineNotificationStatus() {
    return axiosClient.get("/api/notifications/line/status")
}

export function getLineNotificationAuthorization() {
    return axiosClient.get("/api/notifications/line/authorize")
}

export function unlinkLineNotification() {
    return axiosClient.delete("/api/notifications/line/link")
}
