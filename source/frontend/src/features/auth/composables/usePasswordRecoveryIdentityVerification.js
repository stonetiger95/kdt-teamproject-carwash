import { ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import * as PortOne from "@portone/browser-sdk/v2"
import * as authApi from "../api/authApi"
import { isValidIdentityVerificationId } from "../utils/signupValidators"
import {
    PASSWORD_RECOVERY_METHOD,
    isIdentityPasswordRecoveryMethod
} from "../utils/passwordRecoveryMethods"

const RECOVERY_IDENTITY_PENDING_SESSION_KEY =
    "straffic.passwordRecoveryIdentity.pending"

function firstQueryValue(value) {
    return Array.isArray(value) ? value[0] : value
}

function apiErrorMessage(error, fallback) {
    const responseData = error?.response?.data

    if (typeof responseData === "string" && responseData.trim()) {
        return responseData
    }

    return responseData?.message
        || error?.message
        || fallback
}

function isNonEmptyString(value, maxLength) {
    return typeof value === "string"
        && value.length > 0
        && value.length <= maxLength
}

function validateStartResult(startResult) {
    return startResult
        && isNonEmptyString(startResult.recoveryId, 100)
        && isValidIdentityVerificationId(
            startResult.identityVerificationId
        )
        && isNonEmptyString(startResult.stateToken, 2048)
        && isNonEmptyString(startResult.storeId, 200)
        && isNonEmptyString(startResult.channelKey, 200)
}

export function usePasswordRecoveryIdentityVerification() {
    const route = useRoute()
    const router = useRouter()

    const verifying = ref(false)
    const message = ref("")
    const testMode = ref(false)

    function clearPendingIdentity() {
        sessionStorage.removeItem(
            RECOVERY_IDENTITY_PENDING_SESSION_KEY
        )
    }

    function savePendingIdentity(startResult, method) {
        sessionStorage.setItem(
            RECOVERY_IDENTITY_PENDING_SESSION_KEY,
            JSON.stringify({
                recoveryId: startResult.recoveryId,
                identityVerificationId:
                    startResult.identityVerificationId,
                stateToken: startResult.stateToken,
                method,
                testMode: startResult.testMode === true
            })
        )
    }

    function loadPendingIdentity() {
        const raw = sessionStorage.getItem(
            RECOVERY_IDENTITY_PENDING_SESSION_KEY
        )

        if (!raw) {
            return null
        }

        try {
            const pending = JSON.parse(raw)

            if (
                !isNonEmptyString(pending.recoveryId, 100)
                || !isValidIdentityVerificationId(
                    pending.identityVerificationId
                )
                || !isNonEmptyString(pending.stateToken, 2048)
                || !isIdentityPasswordRecoveryMethod(
                    pending.method
                )
            ) {
                return null
            }

            return pending
        } catch (error) {
            return null
        }
    }

    function reset() {
        clearPendingIdentity()
        verifying.value = false
        message.value = ""
        testMode.value = false
    }

    async function completePendingIdentity(
        pending,
        returnedIdentityVerificationId
    ) {
        if (
            !pending
            || pending.identityVerificationId
                !== returnedIdentityVerificationId
        ) {
            throw new Error(
                "본인인증 요청 정보가 일치하지 않습니다. 다시 시도해주세요."
            )
        }

        const completion =
            await authApi.completePasswordRecoveryIdentity({
                recoveryId: pending.recoveryId,
                method: pending.method,
                identityVerificationId:
                    returnedIdentityVerificationId,
                stateToken: pending.stateToken
            })

        if (
            completion?.authorized !== true
            || completion.method !== pending.method
        ) {
            throw new Error(
                "비밀번호 재설정 권한을 확인하지 못했습니다. 다시 시도해주세요."
            )
        }

        clearPendingIdentity()
        message.value = "본인인증이 완료되었습니다."

        return true
    }

    async function start(loginId, method) {
        reset()

        if (!isIdentityPasswordRecoveryMethod(method)) {
            message.value = "지원하지 않는 인증 방식입니다."
            return false
        }

        verifying.value = true

        try {
            const startResult =
                await authApi.startPasswordRecoveryIdentity({
                    loginId,
                    method
                })

            if (!validateStartResult(startResult)) {
                throw new Error(
                    "본인인증 요청 정보를 확인하지 못했습니다."
                )
            }

            testMode.value = startResult.testMode === true
            savePendingIdentity(startResult, method)

            const redirectUrl = new URL(
                router.resolve({
                    name: "forgot-password"
                }).href,
                window.location.origin
            ).toString()

            const portOneRequest = {
                storeId: startResult.storeId,
                channelKey: startResult.channelKey,
                identityVerificationId:
                    startResult.identityVerificationId,
                redirectUrl
            }

            if (method === PASSWORD_RECOVERY_METHOD.EASY_AUTH) {
                portOneRequest.bypass = {
                    inicisUnified: {
                        flgFixedUser: "N",
                        // Gender/foreigner input offered by some agencies is
                        // not provider-verified and is unnecessary for reset.
                        FRGNDInfo: "N"
                    }
                }
            }

            const response =
                await PortOne.requestIdentityVerification(
                    portOneRequest
                )

            // A full-page mobile redirect unloads the current page. In that
            // case completion resumes from completeRedirectedIdentity().
            if (!response) {
                return false
            }

            if (response.code !== undefined) {
                clearPendingIdentity()
                throw new Error(
                    response.message
                    || "본인인증이 취소되었거나 실패했습니다."
                )
            }

            return await completePendingIdentity(
                loadPendingIdentity(),
                response.identityVerificationId
            )
        } catch (error) {
            clearPendingIdentity()
            message.value = apiErrorMessage(
                error,
                "본인인증을 시작하지 못했습니다."
            )
            return false
        } finally {
            verifying.value = false
        }
    }

    async function completeRedirectedIdentity() {
        const returnedIdentityVerificationId =
            firstQueryValue(
                route.query.identityVerificationId
            )
        const errorCode = firstQueryValue(route.query.code)

        if (!returnedIdentityVerificationId && !errorCode) {
            return {
                handled: false,
                authorized: false
            }
        }

        const pending = loadPendingIdentity()
        verifying.value = true
        testMode.value = pending?.testMode === true
        let authorized = false

        try {
            if (errorCode) {
                throw new Error(
                    firstQueryValue(route.query.message)
                    || "본인인증이 취소되었거나 실패했습니다."
                )
            }

            authorized = await completePendingIdentity(
                pending,
                returnedIdentityVerificationId
            )
        } catch (error) {
            clearPendingIdentity()
            message.value = apiErrorMessage(
                error,
                "본인인증 결과를 확인하지 못했습니다."
            )
        } finally {
            verifying.value = false

            if (!authorized) {
                await router.replace({
                    name: "forgot-password"
                })
            }
        }

        return {
            handled: true,
            authorized
        }
    }

    return {
        verifying,
        message,
        testMode,
        start,
        completeRedirectedIdentity,
        reset
    }
}
