<template>
    <form
        class="auth-form"
        :class="{ compact }"
        autocomplete="off"
        @submit.prevent="login"
    >
        <div
            v-if="mode === 'home'"
            class="social-login"
        >
            <button
                class="social-button naver-button"
                type="button"
                :disabled="submitting || socialSubmitting || !socialProviders.naver"
                aria-label="네이버 계정으로 로그인"
                @click="loginWithSocial('naver')"
            >
                <b aria-hidden="true">N</b>
                {{ socialButtonText("naver", "네이버") }}
            </button>
            <button
                class="social-button google-button"
                type="button"
                :disabled="submitting || socialSubmitting || !socialProviders.google"
                aria-label="구글 계정으로 로그인"
                @click="loginWithSocial('google')"
            >
                <b aria-hidden="true">G</b>
                {{ socialButtonText("google", "구글") }}
            </button>
            <button
                class="social-button kakao-button"
                type="button"
                :disabled="submitting || socialSubmitting || !socialProviders.kakao"
                aria-label="카카오 계정으로 로그인"
                @click="loginWithSocial('kakao')"
            >
                <b aria-hidden="true">K</b>
                {{ socialButtonText("kakao", "카카오") }}
            </button>
            <span aria-hidden="true">또는</span>
        </div>

        <div
            v-if="mode === 'home'"
            class="form-actions"
        >
            <RouterLink
                class="primary-button"
                :to="{
                    name: 'login',
                    query: route.query.redirect
                        ? { redirect: route.query.redirect }
                        : {}
                }"
                @click.prevent="goToMemberLogin"
            >
                회원 로그인
            </RouterLink>

            <RouterLink
                class="secondary-button"
                :to="{ name: 'customer-signup' }"
            >
                회원가입
            </RouterLink>
        </div>

        <div
            v-if="mode === 'password'"
            id="password-login-fields"
            class="credential-login"
        >
            <label>
                <span>아이디</span>
                <input
                    v-model.trim="loginInfo.loginId"
                    name="loginId"
                    placeholder="Login ID"
                    autocomplete="off"
                    required
                    :disabled="submitting"
                >
            </label>

            <label>
                <span>비밀번호</span>
                <input
                    v-model="loginInfo.password"
                    name="password"
                    type="password"
                    placeholder="Password"
                    autocomplete="current-password"
                    required
                    :disabled="submitting"
                >
            </label>

            <button
                class="primary-button credential-submit"
                type="submit"
                :disabled="submitting"
            >
                {{ submitting ? "로그인 중..." : "로그인" }}
            </button>
        </div>

        <div
            v-if="mode === 'password'"
            class="password-help"
        >
            <RouterLink
                class="forgot-link"
                :to="{ name: 'forgot-password' }"
            >
                아이디·비밀번호를 잊으셨나요?
            </RouterLink>
        </div>

        <p
            v-if="error"
            class="error-message"
            role="alert"
        >
            {{ error }}
        </p>

        <p
            v-else-if="authStore.loginErrorCode === 'ACCOUNT_SUSPENDED'"
            class="error-message"
            role="alert"
        >
            정지된 계정입니다. 관리자에게 문의해주세요.
        </p>

        <p
            v-else-if="authStore.loginErrorCode === 'ACCOUNT_WITHDRAWN'"
            class="error-message"
            role="alert"
        >
            탈퇴된 회원입니다.
        </p>
    </form>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useVehicleStore } from "@/features/vehicle/stores/vehicleStore"
import { ROLE } from "@/shared/auth/roles"
import { useAuthStore } from "../stores/authStore"
import * as authApi from "../api/authApi"

const props = defineProps({
    compact: {
        type: Boolean,
        default: false
    },
    mode: {
        type: String,
        default: "home",
        validator: value => ["home", "password"].includes(value)
    }
})

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const vehicleStore = useVehicleStore()
const error = ref("")
const submitting = ref(false)
const socialSubmitting = ref("")
const socialProvidersLoaded = ref(false)
const socialProviders = reactive({
    naver: false,
    google: false,
    kakao: false
})

const loginInfo = reactive({
    loginId: "",
    password: ""
})

onMounted(() => {
    if (props.mode === "home") {
        loadSocialProviders()
    }
})

async function loadSocialProviders() {
    try {
        const configured = await authApi.getSocialProviders()
        socialProviders.naver = configured.naver === true
        socialProviders.google = configured.google === true
        socialProviders.kakao = configured.kakao === true
    } catch {
        socialProviders.naver = false
        socialProviders.google = false
        socialProviders.kakao = false
    } finally {
        socialProvidersLoaded.value = true
    }
}

function socialButtonText(provider, label) {
    if (!socialProvidersLoaded.value) {
        return `${label} 확인 중...`
    }
    if (!socialProviders[provider]) {
        return `${label} 로그인 (설정 필요)`
    }
    return socialSubmitting.value === provider
        ? `${label}로 이동 중...`
        : `${label} 로그인`
}

async function loginWithSocial(provider) {
    if (socialSubmitting.value) {
        return
    }

    if (!socialProviders[provider]) {
        error.value = `${providerLabel(provider)} 로그인 환경설정이 필요합니다.`
        return
    }

    error.value = ""
    socialSubmitting.value = provider
    try {
        const redirect = route.query.redirect
        const redirectKey = `${provider}LoginRedirect`
        if (
            typeof redirect === "string"
            && redirect.startsWith("/")
            && !redirect.startsWith("//")
        ) {
            sessionStorage.setItem(redirectKey, redirect)
        } else {
            sessionStorage.removeItem(redirectKey)
        }

        const response = provider === "naver"
            ? await authApi.getNaverAuthorization()
            : await authApi.getSocialAuthorization(provider)
        if (provider !== "naver") {
            sessionStorage.setItem(`${provider}LoginState`, response.state)
        }
        window.location.assign(response.authorizationUrl)
    } catch (requestError) {
        error.value = requestError.response?.data?.message
            || `${providerLabel(provider)} 로그인을 시작하지 못했습니다.`
        socialSubmitting.value = ""
    }
}

function providerLabel(provider) {
    return {
        naver: "네이버",
        google: "Google",
        kakao: "카카오"
    }[provider] || "소셜"
}

function goToMemberLogin() {
    const target = router.resolve({
        name: "login",
        query: route.query.redirect
            ? { redirect: route.query.redirect }
            : {}
    })

    window.location.assign(target.href)
}

async function login() {
    if (submitting.value) {
        return
    }

    error.value = ""
    submitting.value = true

    try {
        const success = await authStore.login(loginInfo)

        if (!success) {
            if (
                authStore.loginErrorCode !== "ACCOUNT_SUSPENDED"
                && authStore.loginErrorCode !== "ACCOUNT_WITHDRAWN"
            ) {
                error.value = "아이디 또는 비밀번호를 확인해주세요."
            }

            return
        }

        if (
            authStore.role === ROLE.CUSTOMER
            && !authStore.isSuspended
            && typeof vehicleStore.hasRegisteredVehicle === "function"
        ) {
            const hasVehicle = await vehicleStore.hasRegisteredVehicle()

            if (hasVehicle === false) {
                window.alert("차량 번호를 등록해주세요.")

                await router.replace({
                    name: "customer-vehicles-registration"
                })

                return
            }
        }

        if (authStore.isSuspended) {
            await router.replace({
                name: "customer-wash-history"
            })
            return
        }

        const redirect = route.query.redirect

        if (
            typeof redirect === "string"
            && redirect.startsWith("/")
            && !redirect.startsWith("//")
        ) {
            await router.push(redirect)
            return
        }

        switch (authStore.role) {
            case ROLE.ADMIN:
                await router.push("/admin")
                break

            case ROLE.OWNER:
                await router.push("/owner")
                break

            default:
                await router.push("/customer")
        }
    } finally {
        submitting.value = false
    }
}
</script>

<style scoped>
.auth-form {
    display: grid;
    gap: 14px;
    width: 100%;
}

label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
    text-align: left;
}

.compact > label {
    width: 50%;
    justify-self: center;
}

.credential-login {
    display: grid;
    gap: 14px;
    width: 100%;
}

.compact .credential-login {
    width: 50%;
    justify-self: center;
}

input {
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

input:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

input:disabled {
    color: #6b7890;
    background: #f5f7fa;
    cursor: wait;
}

.password-help {
    display: flex;
    justify-content: center;
    padding-top: 14px;
    border-top: 1px solid #e4eaf2;
}

.social-login {
    display: grid;
    gap: 12px;
    color: #77869d;
    font-size: 12px;
    text-align: center;
}

.social-login > span {
    display: grid;
    grid-template-columns: 1fr max-content 1fr;
    gap: 10px;
    align-items: center;
}

.social-login > span::before,
.social-login > span::after {
    height: 1px;
    background: #e4eaf2;
    content: "";
}

.social-button {
    min-height: 46px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    border-radius: 8px;
    font: inherit;
    font-size: 15px;
    font-weight: 900;
    cursor: pointer;
}

.social-button b {
    font-size: 19px;
}

.naver-button {
    border: 1px solid #03c75a;
    color: #ffffff;
    background: #03c75a;
}

.naver-button:hover:not(:disabled) {
    background: #02b351;
}

.google-button {
    border: 1px solid #cbd6e6;
    color: #1f2937;
    background: #ffffff;
}

.google-button:hover:not(:disabled) {
    background: #f6f8fb;
}

.kakao-button {
    border: 1px solid #fee500;
    color: #191919;
    background: #fee500;
}

.kakao-button:hover:not(:disabled) {
    background: #f5dc00;
}

.social-button:disabled {
    opacity: 0.65;
    cursor: not-allowed;
}

.forgot-link {
    color: #415370;
    font-size: 13px;
    font-weight: 800;
    text-decoration: none;
    border-bottom: 1px solid #9aa9bc;
    transition: color 0.18s ease, border-color 0.18s ease;
}

.forgot-link:hover {
    color: #071b3a;
    border-color: #071b3a;
}

.compact .password-help {
    width: 50%;
    justify-self: center;
}

.compact .social-login {
    width: 50%;
    justify-self: center;
}

.form-actions {
    display: grid;
    gap: 10px;
}

.compact .form-actions {
    width: 50%;
    justify-self: center;
    grid-template-columns: 1fr;
}

.primary-button,
.secondary-button {
    min-height: 46px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    font: inherit;
    font-size: 15px;
    font-weight: 900;
    text-decoration: none;
    box-sizing: border-box;
    cursor: pointer;
    transition: transform 0.18s ease, background 0.18s ease;
}

.primary-button {
    border: 0;
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
}

.primary-button:hover:not(:disabled) {
    background: #0f2f5f;
    transform: translateY(-1px);
}

.primary-button:disabled {
    opacity: 0.7;
    cursor: wait;
}

.credential-submit {
    width: 100%;
}

.secondary-button {
    color: #071b3a;
    background: #ffffff;
    border: 1px solid #cbd6e6;
}

.secondary-button:hover {
    background: #f5f8fc;
}

.error-message {
    margin: 0;
    padding: 12px 14px;
    border-radius: 8px;
    color: #b42318;
    background: #fff1f1;
    font-size: 14px;
    font-weight: 800;
    line-height: 1.5;
    text-align: center;
}

.compact .error-message {
    width: 50%;
    justify-self: center;
    box-sizing: border-box;
}

@media (max-width: 520px) {
    .compact > label,
    .compact .credential-login,
    .compact .password-help,
    .compact .social-login,
    .compact .form-actions,
    .compact .error-message {
        width: 100%;
    }
}

</style>
