import { ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import * as PortOne from "@portone/browser-sdk/v2"
import * as authApi from "../api/authApi"
import { isValidIdentityVerificationId } from "../utils/signupValidators"
import {
    PASSWORD_RECOVERY_METHOD,
    isIdentityPasswordRecoveryMethod
} from "../utils/passwordRecoveryMethods"

const PENDING_SESSION_KEY =
    "straffic.accountIdRecoveryIdentity.pending"

function firstQueryValue(value) {
    return Array.isArray(value) ? value[0] : value
}

function apiErrorMessage(error, fallback) {
    const responseData = error?.response?.data

    if (typeof responseData === "string" && responseData.trim()) {
        return responseData
    }

    return responseData?.message || error?.message || fallback
}

function isNonEmptyString(value, maxLength) {
    return typeof value === "string"
        && value.length > 0
        && value.length <= maxLength
}

function validateStartResult(result) {
    return result
        && isNonEmptyString(result.recoveryId, 100)
        && isValidIdentityVerificationId(
            result.identityVerificationId
        )
        && isNonEmptyString(result.stateToken, 2048)
        && isNonEmptyString(result.storeId, 200)
        && isNonEmptyString(result.channelKey, 200)
}

function validateRecoveryResult(result, method) {
    return result
        && /^[A-Za-z0-9]{1,50}$/.test(result.loginId || "")
        && result.method === method
}

export function useAccountIdRecoveryIdentityVerification() {
    const route = useRoute()
    const router = useRouter()
    const verifying = ref(false)
    const message = ref("")
    const testMode = ref(false)

    function clearPending() {
        sessionStorage.removeItem(PENDING_SESSION_KEY)
    }

    function savePending(result, method) {
        sessionStorage.setItem(
            PENDING_SESSION_KEY,
            JSON.stringify({
                recoveryId: result.recoveryId,
                identityVerificationId:
                    result.identityVerificationId,
                stateToken: result.stateToken,
                method,
                testMode: result.testMode === true
            })
        )
    }

    function loadPending() {
        const raw = sessionStorage.getItem(PENDING_SESSION_KEY)
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
                || !isIdentityPasswordRecoveryMethod(pending.method)
            ) {
                return null
            }
            return pending
        } catch (error) {
            return null
        }
    }

    function reset() {
        clearPending()
        verifying.value = false
        message.value = ""
        testMode.value = false
    }

    async function completePending(
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

        const result =
            await authApi.completeAccountIdRecoveryIdentity({
                recoveryId: pending.recoveryId,
                method: pending.method,
                identityVerificationId:
                    returnedIdentityVerificationId,
                stateToken: pending.stateToken
            })

        if (!validateRecoveryResult(result, pending.method)) {
            throw new Error(
                "본인인증과 일치하는 아이디를 확인하지 못했습니다."
            )
        }

        clearPending()
        message.value = "본인인증이 완료되었습니다."
        return result
    }

    async function start(method) {
        reset()

        if (!isIdentityPasswordRecoveryMethod(method)) {
            message.value = "지원하지 않는 인증 방식입니다."
            return null
        }

        verifying.value = true
        try {
            const startResult =
                await authApi.startAccountIdRecoveryIdentity({ method })

            if (!validateStartResult(startResult)) {
                throw new Error(
                    "본인인증 요청 정보를 확인하지 못했습니다."
                )
            }

            testMode.value = startResult.testMode === true
            savePending(startResult, method)

            const redirectUrl = new URL(
                router.resolve({
                    name: "forgot-password",
                    query: { purpose: "find-id" }
                }).href,
                window.location.origin
            ).toString()

            const request = {
                storeId: startResult.storeId,
                channelKey: startResult.channelKey,
                identityVerificationId:
                    startResult.identityVerificationId,
                redirectUrl
            }

            if (method === PASSWORD_RECOVERY_METHOD.EASY_AUTH) {
                request.bypass = {
                    inicisUnified: {
                        flgFixedUser: "N",
                        FRGNDInfo: "N"
                    }
                }
            }

            const response =
                await PortOne.requestIdentityVerification(request)

            if (!response) {
                return null
            }

            if (response.code !== undefined) {
                clearPending()
                throw new Error(
                    response.message
                    || "본인인증이 취소되었거나 실패했습니다."
                )
            }

            return await completePending(
                loadPending(),
                response.identityVerificationId
            )
        } catch (error) {
            clearPending()
            message.value = apiErrorMessage(
                error,
                "본인인증을 시작하지 못했습니다."
            )
            return null
        } finally {
            verifying.value = false
        }
    }

    async function completeRedirectedIdentity() {
        const returnedIdentityVerificationId = firstQueryValue(
            route.query.identityVerificationId
        )
        const errorCode = firstQueryValue(route.query.code)

        if (!returnedIdentityVerificationId && !errorCode) {
            return { handled: false, result: null }
        }

        const pending = loadPending()
        verifying.value = true
        testMode.value = pending?.testMode === true
        let result = null

        try {
            if (errorCode) {
                throw new Error(
                    firstQueryValue(route.query.message)
                    || "본인인증이 취소되었거나 실패했습니다."
                )
            }

            result = await completePending(
                pending,
                returnedIdentityVerificationId
            )
        } catch (error) {
            clearPending()
            message.value = apiErrorMessage(
                error,
                "본인인증 결과를 확인하지 못했습니다."
            )
        } finally {
            verifying.value = false
            await router.replace({
                name: "forgot-password",
                query: { purpose: "find-id" }
            })
        }

        return { handled: true, result }
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
