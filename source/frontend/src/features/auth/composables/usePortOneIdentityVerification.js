import { computed, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import * as PortOne from "@portone/browser-sdk/v2"
import * as authApi from "../api/authApi"
import {
    isValidIdentityVerificationId,
    isValidKoreanMobileNumber
} from "../utils/signupValidators"

const IDENTITY_PENDING_SESSION_KEY =
    "straffic.signupIdentity.pending"

function firstQueryValue(value) {
    return Array.isArray(value) ? value[0] : value
}

function apiErrorMessage(error, fallback) {
    return error?.response?.data?.message
        || error?.message
        || fallback
}

export function usePortOneIdentityVerification() {
    const router = useRouter()
    const route = useRoute()

    const verifying = ref(false)
    const verified = ref(false)
    const verificationId = ref("")
    const proof = ref("")
    const stateToken = ref("")
    const message = ref("")
    const testMode = ref(false)
    const name = ref("")
    const phoneNumber = ref("")

    const isReady = computed(() =>
        verified.value
        && isValidIdentityVerificationId(verificationId.value)
        && proof.value.length > 0
        && proof.value.length <= 4096
        && stateToken.value.length > 0
        && stateToken.value.length <= 2048
        && name.value.trim().length > 0
        && name.value.trim().length <= 50
        && isValidKoreanMobileNumber(phoneNumber.value)
    )

    function clearPendingIdentity() {
        sessionStorage.removeItem(
            IDENTITY_PENDING_SESSION_KEY
        )
    }

    function savePendingIdentity(startResult) {
        sessionStorage.setItem(
            IDENTITY_PENDING_SESSION_KEY,
            JSON.stringify({
                identityVerificationId:
                    startResult.identityVerificationId,
                stateToken: startResult.stateToken,
                testMode: startResult.testMode === true
            })
        )
    }

    function loadPendingIdentity() {
        const raw = sessionStorage.getItem(
            IDENTITY_PENDING_SESSION_KEY
        )

        if (!raw) {
            return null
        }

        try {
            const pending = JSON.parse(raw)

            if (
                typeof pending.identityVerificationId !== "string"
                || typeof pending.stateToken !== "string"
            ) {
                return null
            }

            return pending
        } catch (error) {
            return null
        }
    }

    function clearIdentityResult() {
        verified.value = false
        verificationId.value = ""
        proof.value = ""
        stateToken.value = ""
        testMode.value = false
        name.value = ""
        phoneNumber.value = ""
    }

    function reset() {
        clearPendingIdentity()
        clearIdentityResult()
        message.value = ""
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
            await authApi.completeIdentityVerification({
                identityVerificationId:
                    returnedIdentityVerificationId,
                stateToken: pending.stateToken
            })

        if (
            typeof completion?.identityProof !== "string"
            || completion.identityProof.length === 0
            || completion.identityProof.length > 4096
            || typeof completion.name !== "string"
            || !completion.name.trim()
            || completion.name.length > 50
            || typeof completion.phoneNumber !== "string"
            || !isValidKoreanMobileNumber(
                completion.phoneNumber
            )
        ) {
            throw new Error(
                "본인인증 정보를 확인하지 못했습니다. 다시 시도해주세요."
            )
        }

        verificationId.value =
            returnedIdentityVerificationId
        proof.value = completion.identityProof
        stateToken.value = pending.stateToken
        name.value = completion.name.trim()
        phoneNumber.value = completion.phoneNumber
        verified.value = true
        testMode.value =
            completion.testMode === true
            || pending.testMode === true
        message.value = "본인인증이 완료되었습니다."

        clearPendingIdentity()
    }

    async function start() {
        reset()
        verifying.value = true

        try {
            const startResult =
                await authApi.startIdentityVerification()

            testMode.value = startResult.testMode === true
            savePendingIdentity(startResult)

            const redirectUrl = new URL(
                router.resolve({
                    name: "customer-signup"
                }).href,
                window.location.origin
            ).toString()

            const response =
                await PortOne.requestIdentityVerification({
                    storeId: startResult.storeId,
                    channelKey: startResult.channelKey,
                    identityVerificationId:
                        startResult.identityVerificationId,
                    redirectUrl
                })

            // A mobile full-page redirect unloads this page. Popup and iframe
            // completions resolve here without reloading the current page.
            if (!response) {
                return
            }

            if (response.code !== undefined) {
                clearPendingIdentity()
                throw new Error(
                    response.message
                    || "본인인증이 취소되었거나 실패했습니다."
                )
            }

            await completePendingIdentity(
                loadPendingIdentity(),
                response.identityVerificationId
            )
        } catch (error) {
            clearPendingIdentity()
            clearIdentityResult()
            message.value = apiErrorMessage(
                error,
                "본인인증을 시작하지 못했습니다."
            )
        } finally {
            verifying.value = false
        }
    }

    async function completeRedirectedIdentity() {
        const returnedIdentityVerificationId =
            firstQueryValue(
                route.query.identityVerificationId
            )

        const errorCode = firstQueryValue(
            route.query.code
        )

        if (!returnedIdentityVerificationId && !errorCode) {
            return
        }

        const pending = loadPendingIdentity()
        verifying.value = true

        try {
            if (errorCode) {
                throw new Error(
                    firstQueryValue(route.query.message)
                    || "본인인증이 취소되었거나 실패했습니다."
                )
            }

            await completePendingIdentity(
                pending,
                returnedIdentityVerificationId
            )
        } catch (error) {
            clearPendingIdentity()
            clearIdentityResult()
            message.value = apiErrorMessage(
                error,
                "본인인증 결과를 확인하지 못했습니다."
            )
        } finally {
            verifying.value = false

            await router.replace({
                name: "customer-signup"
            })
        }
    }

    function getSignupProof() {
        return {
            identityVerificationId: verificationId.value,
            identityProof: proof.value,
            identityStateToken: stateToken.value
        }
    }

    return {
        verifying,
        verified,
        verificationId,
        proof,
        stateToken,
        message,
        testMode,
        name,
        phoneNumber,
        isReady,
        start,
        completeRedirectedIdentity,
        getSignupProof,
        reset
    }
}
