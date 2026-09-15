import {
    computed,
    onBeforeUnmount,
    ref
} from "vue"
import * as authApi from "../api/authApi"
import { isValidEmailAddress } from "../utils/signupValidators"
import { PASSWORD_RECOVERY_METHOD } from "../utils/passwordRecoveryMethods"

const DEFAULT_RESEND_SECONDS = 60
const EMAIL_OTP_PATTERN = /^\d{6}$/

function apiErrorMessage(error, fallback) {
    const responseData = error?.response?.data
    if (typeof responseData === "string" && responseData.trim()) {
        return responseData
    }
    return responseData?.message || error?.message || fallback
}

function normalizedResendSeconds(value) {
    const seconds = Number(value)
    if (!Number.isFinite(seconds)) {
        return DEFAULT_RESEND_SECONDS
    }
    return Math.min(3600, Math.max(0, Math.floor(seconds)))
}

export function useEmailAccountIdRecovery() {
    const challengeId = ref("")
    const code = ref("")
    const sending = ref(false)
    const verifying = ref(false)
    const resendSeconds = ref(0)
    const message = ref("")
    const messageType = ref("")
    let countdownTimer = null

    const challengeStarted = computed(() =>
        challengeId.value.length > 0
    )

    function clearCountdown() {
        if (countdownTimer !== null) {
            clearInterval(countdownTimer)
            countdownTimer = null
        }
    }

    function startCountdown(seconds) {
        clearCountdown()
        resendSeconds.value = normalizedResendSeconds(seconds)
        if (resendSeconds.value === 0) {
            return
        }

        countdownTimer = setInterval(() => {
            resendSeconds.value = Math.max(0, resendSeconds.value - 1)
            if (resendSeconds.value === 0) {
                clearCountdown()
            }
        }, 1000)
    }

    function setCode(value) {
        code.value = String(value ?? "")
            .replace(/\D/g, "")
            .slice(0, 6)
        message.value = ""
        messageType.value = ""
    }

    function reset() {
        clearCountdown()
        challengeId.value = ""
        code.value = ""
        sending.value = false
        verifying.value = false
        resendSeconds.value = 0
        message.value = ""
        messageType.value = ""
    }

    async function start(emailAddress) {
        message.value = ""
        messageType.value = ""
        if (!isValidEmailAddress(emailAddress)) {
            message.value = "이메일 형식을 확인해주세요."
            messageType.value = "error"
            return false
        }
        if (sending.value || resendSeconds.value > 0) {
            return false
        }

        sending.value = true
        try {
            const result = await authApi.startAccountIdRecoveryEmail({
                emailAddress
            })
            if (
                typeof result?.challengeId !== "string"
                || !result.challengeId
                || result.challengeId.length > 100
            ) {
                throw new Error(
                    "이메일 인증 요청 정보를 확인하지 못했습니다."
                )
            }

            challengeId.value = result.challengeId
            code.value = ""
            startCountdown(result.resendAfterSeconds)
            message.value = result.message
                || "입력한 이메일과 일치하는 계정이 있으면 인증번호가 발송됩니다."
            messageType.value = "success"
            return true
        } catch (error) {
            message.value = apiErrorMessage(
                error,
                "인증번호 발송 요청을 처리하지 못했습니다."
            )
            messageType.value = "error"
            return false
        } finally {
            sending.value = false
        }
    }

    async function verify() {
        message.value = ""
        messageType.value = ""
        if (!challengeStarted.value) {
            message.value = "먼저 인증번호를 요청해주세요."
            messageType.value = "error"
            return null
        }
        if (!EMAIL_OTP_PATTERN.test(code.value)) {
            message.value = "인증번호 6자리를 입력해주세요."
            messageType.value = "error"
            return null
        }
        if (verifying.value) {
            return null
        }

        verifying.value = true
        try {
            const result = await authApi.verifyAccountIdRecoveryEmail({
                challengeId: challengeId.value,
                code: code.value
            })
            if (
                !/^[A-Za-z0-9]{1,50}$/.test(result?.loginId || "")
                || result?.method !== PASSWORD_RECOVERY_METHOD.EMAIL_OTP
            ) {
                throw new Error(
                    "이메일과 일치하는 아이디를 확인하지 못했습니다."
                )
            }

            clearCountdown()
            message.value = "이메일 인증이 완료되었습니다."
            messageType.value = "success"
            return result
        } catch (error) {
            message.value = apiErrorMessage(
                error,
                "인증번호가 올바르지 않거나 만료되었습니다."
            )
            messageType.value = "error"
            return null
        } finally {
            verifying.value = false
        }
    }

    onBeforeUnmount(clearCountdown)

    return {
        code,
        sending,
        verifying,
        resendSeconds,
        message,
        messageType,
        challengeStarted,
        setCode,
        start,
        verify,
        reset
    }
}
