<template>
    <main class="signup-page">
        <section class="signup-panel">
            <header>
                <span
                    class="provider-mark"
                    :style="providerMarkStyle"
                    aria-hidden="true"
                >{{ providerInfo.mark }}</span>
                <div>
                    <p :style="{ color: providerInfo.accent }">
                        {{ providerInfo.label }} 로그인
                    </p>
                    <h1>가입 정보 확인</h1>
                </div>
            </header>

            <p class="guide-text">
                서비스 이용에 필요한 정보를 확인해주세요. 별도의 비밀번호는 만들지 않습니다.
            </p>

            <form v-if="registrationToken" @submit.prevent="submitSignup">
                <label class="login-id-field">
                    <span>아이디</span>
                    <span class="field-control">
                        <input
                            v-model.trim="form.loginId"
                            maxlength="50"
                            autocomplete="username"
                            required
                            @input="resetLoginIdCheck"
                        >
                        <button
                            type="button"
                            :disabled="checkingLoginId || !form.loginId"
                            @click="checkLoginId"
                        >
                            {{ checkingLoginId ? "확인 중" : "중복 확인" }}
                        </button>
                    </span>
                    <small :class="{ success: loginIdAvailable }">{{ loginIdMessage }}</small>
                </label>

                <label>
                    <span>이름</span>
                    <input v-model.trim="form.userName" maxlength="50" required>
                </label>

                <label>
                    <span>생년월일</span>
                    <input v-model="form.birthDate" type="date" required>
                </label>

                <label>
                    <span>휴대전화번호</span>
                    <input
                        v-model.trim="form.phone"
                        inputmode="numeric"
                        maxlength="13"
                        placeholder="01012345678"
                        required
                    >
                </label>

                <label>
                    <span>이메일</span>
                    <input v-model.trim="form.emailAddress" type="email" maxlength="100" required>
                </label>

                <fieldset>
                    <legend>성별</legend>
                    <label v-for="option in genderOptions" :key="option.value" class="gender-option">
                        <input v-model="form.gender" type="radio" :value="option.value" required>
                        <span>{{ option.label }}</span>
                    </label>
                </fieldset>

                <label class="terms-field">
                    <input v-model="form.termsAccepted" type="checkbox" required>
                    <span>서비스 이용 및 개인정보 처리에 동의합니다.</span>
                </label>

                <p v-if="errorMessage" class="error-message" role="alert">
                    {{ errorMessage }}
                </p>

                <div class="actions">
                    <button class="primary-button" type="submit" :disabled="submitting">
                        {{ submitting ? "가입 중..." : "동의하고 가입" }}
                    </button>
                    <button class="cancel-button" type="button" :disabled="submitting" @click="cancel">
                        취소
                    </button>
                </div>
            </form>

            <div v-else class="expired-state" role="alert">
                <p>가입 세션이 없거나 만료되었습니다.</p>
                <RouterLink :to="{ name: 'login' }">다시 로그인</RouterLink>
            </div>
        </section>
    </main>
</template>

<script setup>
import { computed, reactive, ref } from "vue"
import { RouterLink, useRouter } from "vue-router"
import * as authApi from "../api/authApi"
import { useAuthStore } from "../stores/authStore"

const router = useRouter()
const authStore = useAuthStore()
const props = defineProps({
    provider: {
        type: String,
        default: "naver"
    }
})
const provider = computed(() => {
    const value = String(props.provider || "naver").toLowerCase()
    return ["naver", "google", "kakao"].includes(value) ? value : "naver"
})
const providerInfo = computed(() => ({
    naver: { label: "네이버", mark: "N", accent: "#03a94d", background: "#03c75a", color: "#ffffff" },
    google: { label: "Google", mark: "G", accent: "#3159a7", background: "#ffffff", color: "#4285f4" },
    kakao: { label: "카카오", mark: "K", accent: "#806f00", background: "#fee500", color: "#191919" }
}[provider.value]))
const providerMarkStyle = computed(() => ({
    color: providerInfo.value.color,
    background: providerInfo.value.background,
    border: `1px solid ${providerInfo.value.accent}`
}))
const storagePrefix = provider.value
const registrationToken = sessionStorage.getItem(
    `${storagePrefix}RegistrationToken`
) || ""
const profile = readProfile()
const checkingLoginId = ref(false)
const loginIdAvailable = ref(false)
const checkedLoginId = ref("")
const loginIdMessage = ref("")
const submitting = ref(false)
const errorMessage = ref("")

const genderOptions = [
    { value: "M", label: "남성" },
    { value: "F", label: "여성" },
    { value: "N", label: "선택 안 함" }
]

const form = reactive({
    loginId: "",
    userName: provider.value === "google" ? "" : profile.name || "",
    birthDate: profile.birthDate || "",
    phone: profile.phone || "",
    emailAddress: profile.emailAddress || "",
    gender: profile.gender || "N",
    termsAccepted: false
})

function readProfile() {
    try {
        return JSON.parse(
            sessionStorage.getItem(`${storagePrefix}RegistrationProfile`) || "{}"
        )
    } catch {
        return {}
    }
}

function resetLoginIdCheck() {
    form.loginId = form.loginId.replace(/[^A-Za-z0-9]/g, "")
    loginIdAvailable.value = false
    checkedLoginId.value = ""
    loginIdMessage.value = ""
}

async function checkLoginId() {
    const loginId = form.loginId
    if (!loginId) {
        loginIdMessage.value = "아이디를 입력해주세요."
        return
    }

    checkingLoginId.value = true
    try {
        const available = await authApi.checkLoginId(loginId)
        if (form.loginId !== loginId) {
            return
        }
        loginIdAvailable.value = available
        checkedLoginId.value = available ? loginId : ""
        loginIdMessage.value = available
            ? "사용 가능한 아이디입니다."
            : "이미 사용 중인 아이디입니다."
    } catch {
        loginIdMessage.value = "중복 확인에 실패했습니다."
    } finally {
        checkingLoginId.value = false
    }
}

async function submitSignup() {
    errorMessage.value = ""
    if (!loginIdAvailable.value || checkedLoginId.value !== form.loginId) {
        errorMessage.value = "아이디 중복 확인을 해주세요."
        return
    }

    submitting.value = true
    try {
        const signupInfo = { registrationToken, ...form }
        const login = provider.value === "naver"
            ? await authApi.completeNaverSignup(signupInfo)
            : await authApi.completeSocialSignup(provider.value, signupInfo)
        authStore.applyLoginResponse(login)
        clearRegistration()
        window.alert("가입이 완료되었습니다. 차량 번호를 등록해주세요.")
        await router.replace({ name: "customer-vehicles-registration" })
    } catch (requestError) {
        const code = requestError.response?.data?.code
        if (code === "NAVER_DUPLICATE_ACCOUNT" || code === "SOCIAL_DUPLICATE_ACCOUNT") {
            errorMessage.value = "같은 연락처 또는 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인해주세요."
        } else if (
            code === "NAVER_REGISTRATION_EXPIRED"
            || code === "SOCIAL_REGISTRATION_EXPIRED"
        ) {
            clearRegistration()
            errorMessage.value = `가입 시간이 만료되었습니다. ${providerInfo.value.label} 로그인을 다시 진행해주세요.`
        } else {
            errorMessage.value = requestError.response?.data?.message
                || `${providerInfo.value.label} 회원가입에 실패했습니다.`
        }
    } finally {
        submitting.value = false
    }
}

async function cancel() {
    clearRegistration()
    await router.replace({ name: "login" })
}

function clearRegistration() {
    sessionStorage.removeItem(`${storagePrefix}RegistrationToken`)
    sessionStorage.removeItem(`${storagePrefix}RegistrationProfile`)
}
</script>

<style scoped>
.signup-page {
    min-height: 78vh;
    padding: 42px 18px;
    color: #071b3a;
    background: #f7f9fc;
    box-sizing: border-box;
}

.signup-panel {
    width: min(620px, 100%);
    margin: 0 auto;
    padding: 34px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-sizing: border-box;
}

header {
    display: flex;
    align-items: center;
    gap: 14px;
}

header p,
header h1,
.guide-text {
    margin: 0;
}

header p {
    color: #03a94d;
    font-size: 12px;
    font-weight: 900;
}

header h1 {
    margin-top: 2px;
    font-size: 28px;
    letter-spacing: 0;
}

.provider-mark {
    width: 44px;
    height: 44px;
    display: grid;
    flex: 0 0 auto;
    place-items: center;
    border-radius: 6px;
    font-size: 23px;
    font-weight: 900;
}

.guide-text {
    margin-top: 18px;
    color: #64748b;
    font-size: 14px;
    line-height: 1.55;
}

form {
    display: grid;
    gap: 16px;
    margin-top: 24px;
}

form > label,
fieldset {
    display: grid;
    gap: 7px;
}

form > label > span,
legend {
    color: #334155;
    font-size: 13px;
    font-weight: 800;
}

input {
    width: 100%;
    min-height: 43px;
    padding: 0 12px;
    border: 1px solid #cbd6e6;
    border-radius: 7px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
}

input:focus-visible,
button:focus-visible,
a:focus-visible {
    outline: 3px solid rgba(3, 199, 90, 0.28);
    outline-offset: 2px;
}

.field-control {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 104px;
    gap: 8px;
}

.field-control button {
    border: 1px solid #071b3a;
    border-radius: 7px;
    color: #071b3a;
    background: #ffffff;
    font-weight: 800;
    cursor: pointer;
}

small {
    min-height: 18px;
    color: #b42318;
}

small.success {
    color: #08783c;
}

fieldset {
    grid-template-columns: repeat(3, 1fr);
    margin: 0;
    padding: 0;
    border: 0;
}

legend {
    grid-column: 1 / -1;
    margin-bottom: 7px;
}

.gender-option {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 42px;
    border: 1px solid #cbd6e6;
    border-radius: 7px;
    cursor: pointer;
}

.gender-option input {
    width: auto;
    min-height: auto;
    margin-right: 7px;
}

.terms-field {
    grid-template-columns: max-content 1fr;
    align-items: center;
    padding: 13px;
    border: 1px solid #dce5f1;
    border-radius: 7px;
    background: #f8fafc;
}

.terms-field input {
    width: 18px;
    min-height: 18px;
}

.error-message {
    margin: 0;
    padding: 11px 13px;
    border-radius: 7px;
    color: #b42318;
    background: #fff1f1;
    font-size: 13px;
    line-height: 1.5;
}

.actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 9px;
}

.actions button,
.expired-state a {
    min-height: 44px;
    border-radius: 7px;
    font: inherit;
    font-weight: 900;
    cursor: pointer;
}

.primary-button {
    border: 1px solid #03c75a;
    color: #ffffff;
    background: #03c75a;
}

.cancel-button {
    border: 1px solid #cbd6e6;
    color: #334155;
    background: #ffffff;
}

button:disabled {
    opacity: 0.6;
    cursor: wait;
}

.expired-state {
    display: grid;
    justify-items: start;
    gap: 14px;
    margin-top: 24px;
}

.expired-state p {
    margin: 0;
    color: #b42318;
}

.expired-state a {
    display: inline-flex;
    align-items: center;
    padding: 0 16px;
    color: #ffffff;
    background: #071b3a;
    text-decoration: none;
}

@media (max-width: 560px) {
    .signup-page {
        padding: 20px 12px;
    }

    .signup-panel {
        padding: 24px 18px;
    }

    .field-control,
    .actions {
        grid-template-columns: 1fr;
    }

    fieldset {
        grid-template-columns: 1fr;
    }
}
</style>
