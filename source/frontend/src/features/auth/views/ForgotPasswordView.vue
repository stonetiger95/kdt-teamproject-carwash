<template>
    <main class="recovery-page">
        <section class="recovery-card">
            <header class="recovery-header">
                <h1>계정 찾기</h1>
                <p>
                    아이디 찾기 또는 비밀번호 재설정을 선택해주세요.
                </p>
            </header>

            <div
                class="purpose-selector"
                role="tablist"
                aria-label="계정 찾기 종류"
            >
                <button
                    type="button"
                    role="tab"
                    :aria-selected="purpose === purposes.FIND_ID"
                    :class="{ selected: purpose === purposes.FIND_ID }"
                    :disabled="busy"
                    @click="selectPurpose(purposes.FIND_ID)"
                >
                    아이디 찾기
                </button>
                <button
                    type="button"
                    role="tab"
                    :aria-selected="purpose === purposes.RESET_PASSWORD"
                    :class="{
                        selected: purpose === purposes.RESET_PASSWORD
                    }"
                    :disabled="busy"
                    @click="selectPurpose(purposes.RESET_PASSWORD)"
                >
                    비밀번호 재설정
                </button>
            </div>

            <section
                v-if="recoveredLoginId"
                class="recovered-result"
                aria-live="polite"
            >
                <span>인증된 계정의 아이디</span>
                <strong>{{ recoveredLoginId }}</strong>
                <p>인증이 완료된 경우에만 아이디를 표시합니다.</p>

                <p
                    v-if="recoveredWithTestIdentity"
                    class="status-message warning"
                >
                    테스트 인증은 실제 법적 본인확인이 아닙니다.
                </p>

                <div class="result-actions">
                    <button
                        type="button"
                        class="primary-button"
                        @click="continueToPasswordReset"
                    >
                        비밀번호 재설정
                    </button>
                    <RouterLink
                        class="secondary-link"
                        :to="{ name: 'home' }"
                    >
                        로그인으로
                    </RouterLink>
                </div>
            </section>

            <form
                v-else
                class="recovery-form"
                @submit.prevent
            >
                <label
                    v-if="purpose === purposes.RESET_PASSWORD"
                    for="recovery-login-id"
                >
                    <span>아이디</span>
                    <input
                        id="recovery-login-id"
                        :value="loginId"
                        type="text"
                        autocomplete="username"
                        maxlength="50"
                        placeholder="영문 및 숫자"
                        :disabled="busy"
                        @input="updateLoginId"
                    >
                </label>

                <PasswordRecoveryMethodSelector
                    :model-value="selectedMethod"
                    :disabled="busy"
                    @update:model-value="selectMethod"
                    @activate="activateMethod"
                />

                <EmailOtpVerificationPanel
                    v-if="
                        selectedMethod === methods.EMAIL_OTP
                        && purpose === purposes.FIND_ID
                    "
                    :email-address="emailAddress"
                    :code="accountIdEmailCode"
                    :challenge-started="accountIdEmailChallengeStarted"
                    :sending="accountIdEmailSending"
                    :verifying="accountIdEmailVerifying"
                    :resend-seconds="accountIdEmailResendSeconds"
                    :message="accountIdEmailMessage"
                    :message-type="accountIdEmailMessageType"
                    :disabled="identityVerifying"
                    @update:email-address="updateEmailAddress"
                    @update:code="setAccountIdEmailCode"
                    @send="sendEmailCode"
                    @verify="verifyEmailCode"
                />

                <EmailOtpVerificationPanel
                    v-else-if="selectedMethod === methods.EMAIL_OTP"
                    :email-address="emailAddress"
                    :code="passwordEmailCode"
                    :challenge-started="passwordEmailChallengeStarted"
                    :sending="passwordEmailSending"
                    :verifying="passwordEmailVerifying"
                    :resend-seconds="passwordEmailResendSeconds"
                    :message="passwordEmailMessage"
                    :message-type="passwordEmailMessageType"
                    :disabled="!loginIdValid || identityVerifying"
                    @update:email-address="updateEmailAddress"
                    @update:code="setPasswordEmailCode"
                    @send="sendEmailCode"
                    @verify="verifyEmailCode"
                />

                <section
                    v-else-if="identityMessage || identityTestMode"
                    class="identity-panel"
                >
                    <p
                        v-if="identityMessage"
                        class="status-message error"
                        role="alert"
                    >
                        {{ identityMessage }}
                    </p>

                    <p
                        v-if="identityTestMode"
                        class="status-message warning"
                    >
                        테스트 인증은 실제 법적 본인확인이 아닙니다.
                    </p>
                </section>

                <p
                    v-if="pageMessage"
                    class="status-message error"
                    role="alert"
                >
                    {{ pageMessage }}
                </p>

                <RouterLink
                    class="home-link"
                    :to="{ name: 'home' }"
                >
                    홈으로
                </RouterLink>
            </form>
        </section>
    </main>
</template>

<script setup>
import {
    computed,
    nextTick,
    onMounted,
    ref
} from "vue"
import { useRoute, useRouter } from "vue-router"
import * as authApi from "../api/authApi"
import EmailOtpVerificationPanel from "../components/EmailOtpVerificationPanel.vue"
import PasswordRecoveryMethodSelector from "../components/PasswordRecoveryMethodSelector.vue"
import { useAccountIdRecoveryIdentityVerification } from "../composables/useAccountIdRecoveryIdentityVerification"
import { useEmailAccountIdRecovery } from "../composables/useEmailAccountIdRecovery"
import { useEmailPasswordRecovery } from "../composables/useEmailPasswordRecovery"
import { usePasswordRecoveryIdentityVerification } from "../composables/usePasswordRecoveryIdentityVerification"
import {
    isValidLoginId,
    sanitizeLoginId
} from "../utils/signupValidators"
import { PASSWORD_RECOVERY_METHOD } from "../utils/passwordRecoveryMethods"

const RECOVERY_PURPOSE = Object.freeze({
    FIND_ID: "FIND_ID",
    RESET_PASSWORD: "RESET_PASSWORD"
})

const route = useRoute()
const router = useRouter()
const purposes = RECOVERY_PURPOSE
const methods = PASSWORD_RECOVERY_METHOD
const purpose = ref(
    route.query.purpose === "find-id"
        ? purposes.FIND_ID
        : purposes.RESET_PASSWORD
)
const loginId = ref("")
const emailAddress = ref("")
const selectedMethod = ref(methods.PHONE_IDENTITY)
const recoveredLoginId = ref("")
const recoveredWithTestIdentity = ref(false)
const pageMessage = ref("")

const passwordIdentity = usePasswordRecoveryIdentityVerification()
const {
    verifying: passwordIdentityVerifying,
    message: passwordIdentityMessage,
    testMode: passwordIdentityTestMode,
    start: startPasswordIdentity,
    completeRedirectedIdentity: completeRedirectedPasswordIdentity,
    reset: resetPasswordIdentity
} = passwordIdentity

const accountIdIdentity = useAccountIdRecoveryIdentityVerification()
const {
    verifying: accountIdIdentityVerifying,
    message: accountIdIdentityMessage,
    testMode: accountIdIdentityTestMode,
    start: startAccountIdIdentity,
    completeRedirectedIdentity: completeRedirectedAccountIdIdentity,
    reset: resetAccountIdIdentity
} = accountIdIdentity

const passwordEmail = useEmailPasswordRecovery()
const {
    code: passwordEmailCode,
    sending: passwordEmailSending,
    verifying: passwordEmailVerifying,
    resendSeconds: passwordEmailResendSeconds,
    message: passwordEmailMessage,
    messageType: passwordEmailMessageType,
    challengeStarted: passwordEmailChallengeStarted,
    setCode: setPasswordEmailCode,
    start: startPasswordEmail,
    verify: verifyPasswordEmail,
    reset: resetPasswordEmail
} = passwordEmail

const accountIdEmail = useEmailAccountIdRecovery()
const {
    code: accountIdEmailCode,
    sending: accountIdEmailSending,
    verifying: accountIdEmailVerifying,
    resendSeconds: accountIdEmailResendSeconds,
    message: accountIdEmailMessage,
    messageType: accountIdEmailMessageType,
    challengeStarted: accountIdEmailChallengeStarted,
    setCode: setAccountIdEmailCode,
    start: startAccountIdEmail,
    verify: verifyAccountIdEmail,
    reset: resetAccountIdEmail
} = accountIdEmail

const loginIdValid = computed(() => isValidLoginId(loginId.value))

const identityVerifying = computed(() =>
    passwordIdentityVerifying.value
    || accountIdIdentityVerifying.value
)

const busy = computed(() =>
    identityVerifying.value
    || passwordEmailSending.value
    || passwordEmailVerifying.value
    || accountIdEmailSending.value
    || accountIdEmailVerifying.value
)

const identityMessage = computed(() =>
    purpose.value === purposes.FIND_ID
        ? accountIdIdentityMessage.value
        : passwordIdentityMessage.value
)

const identityTestMode = computed(() =>
    purpose.value === purposes.FIND_ID
        ? accountIdIdentityTestMode.value
        : passwordIdentityTestMode.value
)

function clearPageMessage() {
    pageMessage.value = ""
}

function resetFlows() {
    resetPasswordIdentity()
    resetAccountIdIdentity()
    resetPasswordEmail()
    resetAccountIdEmail()
}

function selectPurpose(nextPurpose) {
    if (!Object.values(purposes).includes(nextPurpose)) {
        return
    }

    purpose.value = nextPurpose
    recoveredLoginId.value = ""
    recoveredWithTestIdentity.value = false
    clearPageMessage()
    resetFlows()

    router.replace({
        name: "forgot-password",
        query: nextPurpose === purposes.FIND_ID
            ? { purpose: "find-id" }
            : {}
    })
}

function updateLoginId(event) {
    loginId.value = sanitizeLoginId(event.target.value)
    event.target.value = loginId.value
    clearPageMessage()
    resetFlows()
}

function updateEmailAddress(value) {
    emailAddress.value = String(value ?? "")
    clearPageMessage()
    resetPasswordEmail()
    resetAccountIdEmail()
}

function selectMethod(method) {
    if (!Object.values(methods).includes(method)) {
        return
    }

    selectedMethod.value = method
    clearPageMessage()
    resetFlows()
}

async function activateMethod(method) {
    if (!Object.values(methods).includes(method) || busy.value) {
        return
    }

    if (method === methods.EMAIL_OTP) {
        await nextTick()
        document.getElementById("recovery-email-address")?.focus()
        return
    }

    await startIdentityVerification(method)
}

function showRecoveredAccount(result) {
    if (!/^[A-Za-z0-9]{1,50}$/.test(result?.loginId || "")) {
        pageMessage.value = "인증된 아이디를 확인하지 못했습니다."
        return
    }

    recoveredLoginId.value = result.loginId
    recoveredWithTestIdentity.value = result.testMode === true
}

async function goToResetPassword() {
    await router.replace({ name: "reset-password" })
}

async function continueToPasswordReset() {
    const recovered = recoveredLoginId.value
    selectPurpose(purposes.RESET_PASSWORD)
    loginId.value = recovered
}

async function startIdentityVerification(method = selectedMethod.value) {
    clearPageMessage()

    if (purpose.value === purposes.FIND_ID) {
        const result = await startAccountIdIdentity(
            method
        )
        if (result) {
            showRecoveredAccount(result)
        }
        return
    }

    if (!loginIdValid.value) {
        pageMessage.value = "아이디를 확인해주세요."
        return
    }

    const authorized = await startPasswordIdentity(
        loginId.value,
        method
    )
    if (authorized) {
        await goToResetPassword()
    }
}

async function sendEmailCode() {
    clearPageMessage()
    const normalizedEmail = emailAddress.value.trim().toLowerCase()

    if (purpose.value === purposes.FIND_ID) {
        await startAccountIdEmail(normalizedEmail)
    } else {
        await startPasswordEmail(loginId.value, normalizedEmail)
    }
}

async function verifyEmailCode() {
    clearPageMessage()

    if (purpose.value === purposes.FIND_ID) {
        const result = await verifyAccountIdEmail()
        if (result) {
            showRecoveredAccount(result)
        }
    } else if (await verifyPasswordEmail()) {
        await goToResetPassword()
    }
}

async function resumeRecovery() {
    if (purpose.value === purposes.FIND_ID) {
        const redirect = await completeRedirectedAccountIdIdentity()
        if (redirect.result) {
            showRecoveredAccount(redirect.result)
        }
        return
    }

    const redirectResult = await completeRedirectedPasswordIdentity()
    if (redirectResult.authorized) {
        await goToResetPassword()
        return
    }
    if (redirectResult.handled) {
        return
    }

    try {
        const session = await authApi.getPasswordRecoverySession()
        if (session?.authorized === true) {
            await goToResetPassword()
        }
    } catch (error) {
        const status = error?.response?.status
        if (status !== 401 && status !== 403) {
            pageMessage.value =
                "비밀번호 재설정 인증 상태를 확인하지 못했습니다."
        }
    }
}

onMounted(resumeRecovery)
</script>

<style scoped>
.recovery-page {
    min-height: 72vh;
    display: grid;
    place-items: start center;
    padding: 48px 20px 24px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.recovery-card {
    width: min(660px, 100%);
    display: grid;
    gap: 24px;
    padding: 38px 34px;
    border: 1px solid #dce5f1;
    border-radius: 12px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
    box-sizing: border-box;
}

.recovery-header {
    display: grid;
    gap: 8px;
    text-align: center;
}

.recovery-header h1 {
    margin: 0;
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
    line-height: 1.2;
}

.recovery-header p {
    margin: 0;
    color: #52637d;
    font-size: 14px;
}

.purpose-selector {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 4px;
    border-radius: 10px;
    background: #edf2f8;
}

.purpose-selector button {
    min-height: 46px;
    border: 0;
    border-radius: 8px;
    color: #52637d;
    background: transparent;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.purpose-selector button.selected {
    color: #071b3a;
    background: #ffffff;
    box-shadow: 0 2px 8px rgba(7, 27, 58, 0.1);
}

.purpose-selector button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
}

.recovery-form {
    display: grid;
    gap: 18px;
}

.recovery-form > label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

.recovery-form > label input {
    width: 100%;
    min-height: 46px;
    padding: 0 13px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
    outline: none;
}

.recovery-form > label input:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.identity-panel {
    display: grid;
    gap: 12px;
    padding: 18px;
    border: 1px solid #dce5f1;
    border-radius: 10px;
    background: #f8fafc;
}

.primary-button {
    min-width: 138px;
    min-height: 46px;
    padding: 0 16px;
    border: 0;
    border-radius: 8px;
    color: #ffffff;
    background: #071b3a;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.primary-button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
}

.status-message {
    grid-column: 1 / -1;
    margin: 0;
    padding: 11px 12px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 800;
    line-height: 1.5;
    text-align: center;
}

.status-message.error {
    color: #b42318;
    background: #fff1f1;
}

.status-message.warning {
    color: #8a4b08;
    background: #fff7e6;
}

.home-link {
    justify-self: center;
    color: #071b3a;
    font-size: 15px;
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 4px;
}

.recovered-result {
    display: grid;
    justify-items: center;
    gap: 12px;
    padding: 26px 20px;
    border: 1px solid #bedacb;
    border-radius: 12px;
    background: #f1faf5;
    text-align: center;
}

.recovered-result > span {
    color: #52637d;
    font-size: 14px;
    font-weight: 800;
}

.recovered-result > strong {
    color: #071b3a;
    font-size: 28px;
    letter-spacing: 0.02em;
}

.recovered-result > p:not(.status-message) {
    margin: 0;
    color: #52637d;
    font-size: 13px;
}

.result-actions {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-top: 8px;
}

.secondary-link {
    min-width: 138px;
    min-height: 46px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0 16px;
    border: 1px solid #9fb0c9;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font-size: 14px;
    font-weight: 900;
    text-decoration: none;
    box-sizing: border-box;
}

@media (max-width: 560px) {
    .recovery-page {
        padding-top: 28px;
    }

    .recovery-card {
        padding: 28px 20px;
    }

    .recovery-header h1 {
        font-size: 32px;
    }

    .result-actions {
        width: 100%;
        flex-direction: column;
    }

    .result-actions .primary-button,
    .secondary-link {
        width: 100%;
    }
}
</style>
