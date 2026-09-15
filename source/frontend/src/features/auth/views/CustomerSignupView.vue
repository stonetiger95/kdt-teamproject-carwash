<template>
    <main class="signup-page">
        <section class="signup-card">
            <h1>회원가입</h1>

            <form @submit.prevent="submitSignup">
                <IdentityVerificationPanel
                    :verifying="identityVerifying"
                    :verified="identityVerified"
                    :test-mode="identityTestMode"
                    :message="identityMessage"
                    @start="handleIdentityVerificationStart"
                />

                <VerifiedIdentityFields
                    :verified="identityVerified"
                    :name="verifiedName"
                    :phone-number="verifiedPhoneNumber"
                />

                <CustomerSignupFields
                    :login-id="form.loginId"
                    :password="form.password"
                    :confirm-password="confirmPassword"
                    :gender="form.gender"
                    :email-id="emailId"
                    :email-domain-option="emailDomainOption"
                    :email-domain="emailDomain"
                    :login-id-message="loginIdMessage"
                    :checking-login-id="checkingLoginId"
                    :login-id-available="loginIdAvailable"
                    :email-message="emailMessage"
                    :passwords-mismatch="passwordsMismatch"
                    :submitting="submitting"
                    @update:login-id="updateLoginId"
                    @update:password="updatePassword"
                    @update:confirm-password="updateConfirmPassword"
                    @update:gender="updateGender"
                    @update:email-id="updateEmailId"
                    @update:email-domain-option="updateEmailDomainOption"
                    @update:email-domain="updateEmailDomain"
                    @check-login-id="checkLoginId"
                />

                <p
                    v-if="message"
                    class="signup-result"
                    :class="messageType"
                    role="alert"
                >
                    {{ message }}
                </p>

                <div class="signup-actions">
                    <button
                        class="primary-button"
                        type="submit"
                        :disabled="submitting || !canSubmitSignup"
                    >
                        {{ submitting ? "가입 중..." : "회원가입" }}
                    </button>

                    <button
                        class="secondary-button"
                        type="button"
                        :disabled="submitting"
                        @click="goHome"
                    >
                        홈으로
                    </button>
                </div>
            </form>
        </section>
    </main>
</template>

<script setup>
import {
    computed,
    onMounted,
    ref
} from "vue"
import { useRouter } from "vue-router"
import CustomerSignupFields from "../components/CustomerSignupFields.vue"
import IdentityVerificationPanel from "../components/IdentityVerificationPanel.vue"
import VerifiedIdentityFields from "../components/VerifiedIdentityFields.vue"
import { useCustomerSignupForm } from "../composables/useCustomerSignupForm"
import { usePortOneIdentityVerification } from "../composables/usePortOneIdentityVerification"
import { useAuthStore } from "../stores/authStore"

const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const message = ref("")
const messageType = ref("")

const identity = usePortOneIdentityVerification()
const {
    verifying: identityVerifying,
    verified: identityVerified,
    message: identityMessage,
    testMode: identityTestMode,
    name: verifiedName,
    phoneNumber: verifiedPhoneNumber,
    isReady: identityReady,
    start: startIdentityVerification,
    completeRedirectedIdentity,
    getSignupProof
} = identity

const signupForm = useCustomerSignupForm()
const {
    form,
    confirmPassword,
    checkingLoginId,
    loginIdAvailable,
    loginIdMessage,
    emailMessage,
    emailId,
    emailDomainOption,
    emailDomain,
    passwordsMismatch,
    isComplete: formComplete,
    updateLoginId,
    checkLoginId,
    updatePassword,
    updateConfirmPassword,
    updateGender,
    updateEmailId,
    updateEmailDomainOption,
    updateEmailDomain,
    saveRedirectState,
    clearRedirectState,
    validateForSubmit,
    buildSignupPayload
} = signupForm

const canSubmitSignup = computed(() =>
    identityReady.value && formComplete.value
)

onMounted(completeRedirectedIdentity)

async function handleIdentityVerificationStart() {
    saveRedirectState()

    try {
        await startIdentityVerification()
    } finally {
        clearRedirectState()
    }
}

async function submitSignup() {
    message.value = ""
    messageType.value = ""

    const validation = validateForSubmit()

    if (!validation.valid) {
        if (validation.message) {
            message.value = validation.message
            messageType.value = "error"
        }

        return
    }

    if (!identityReady.value) {
        message.value =
            "회원가입 전에 휴대폰 본인인증을 완료해주세요."
        messageType.value = "error"
        return
    }

    submitting.value = true

    try {
        const result = await authStore.signup(
            buildSignupPayload(getSignupProof())
        )

        if (result.success) {
            message.value = "회원가입이 완료되었습니다."
            messageType.value = "success"

            window.alert("차량 번호를 등록해주세요.")

            await router.replace({
                name: "customer-vehicles-registration"
            })

            return
        }

        message.value =
            result.message || "회원가입에 실패했습니다."
        messageType.value = "error"
    } finally {
        submitting.value = false
    }
}

function goHome() {
    router.push({ name: "home" })
}
</script>

<style scoped>
.signup-page {
    min-height: 100vh;
    padding: 70px 20px;
    background: #f7f9fc;
    color: #071b3a;
    font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.signup-card {
    width: min(100%, 720px);
    margin: 0 auto;
    padding: 46px 48px;
    background: #fff;
    border: 1px solid #e4e9f1;
    border-radius: 20px;
    box-shadow: 0 30px 70px rgba(7, 27, 58, 0.09);
    box-sizing: border-box;
}

h1 {
    margin: 0 0 36px;
    color: #071b3a;
    font-size: 38px;
    font-weight: 900;
    text-align: center;
}

form {
    display: grid;
    gap: 18px;
}

.signup-result {
    margin: 4px 0 0;
    color: #071b3a;
    font-weight: 700;
    text-align: center;
}

.signup-result.success {
    color: #067647;
}

.signup-result.error {
    color: #b42318;
}

.signup-actions {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-top: 18px;
}

.signup-actions button {
    min-width: 150px;
    height: 44px;
    border-radius: 8px;
    font-size: 15px;
    font-weight: 900;
    cursor: pointer;
}

.primary-button {
    border: 0;
    background: #071b3a;
    color: #fff;
}

.secondary-button {
    border: 1px solid #aebbd0;
    background: #fff;
    color: #071b3a;
}

.signup-actions button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

@media (max-width: 720px) {
    .signup-page {
        padding: 30px 16px;
    }

    .signup-card {
        padding: 34px 22px;
    }

    h1 {
        font-size: 32px;
    }

    .signup-actions {
        flex-direction: column;
    }

    .signup-actions button {
        width: 100%;
    }
}
</style>
