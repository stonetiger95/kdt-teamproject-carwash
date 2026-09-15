<template>
    <main class="reset-page">
        <section class="reset-card">
            <header class="reset-header">
                <h1>새 비밀번호 설정</h1>
                <p>
                    인증된 계정에 사용할 새 비밀번호를 입력해주세요.
                </p>
            </header>

            <p
                v-if="checkingSession"
                class="status-message info"
                role="status"
            >
                인증 상태를 확인하고 있습니다.
            </p>

            <form
                v-else-if="authorized"
                class="reset-form"
                @submit.prevent="resetPassword"
            >
                <label for="new-password">
                    <span>새 비밀번호</span>
                    <input
                        id="new-password"
                        v-model="newPassword"
                        type="password"
                        autocomplete="new-password"
                        minlength="8"
                        maxlength="72"
                        required
                        :disabled="resetting"
                        @input="clearMessage"
                    >
                </label>

                <label for="confirm-password">
                    <span>새 비밀번호 확인</span>
                    <input
                        id="confirm-password"
                        v-model="confirmPassword"
                        type="password"
                        autocomplete="new-password"
                        minlength="8"
                        maxlength="72"
                        required
                        :disabled="resetting"
                        @input="clearMessage"
                    >
                </label>

                <p
                    v-if="passwordMismatch"
                    class="status-message error"
                    role="alert"
                >
                    새 비밀번호가 일치하지 않습니다.
                </p>

                <p
                    v-if="message"
                    class="status-message error"
                    role="alert"
                >
                    {{ message }}
                </p>

                <button
                    class="primary-button"
                    type="submit"
                    :disabled="!canSubmit"
                >
                    {{ resetting ? "변경 중..." : "비밀번호 변경" }}
                </button>
            </form>

            <div
                v-else
                class="invalid-session"
            >
                <p
                    class="status-message error"
                    role="alert"
                >
                    {{
                        message
                        || "비밀번호 재설정 인증이 필요합니다."
                    }}
                </p>

                <button
                    class="secondary-button"
                    type="button"
                    @click="goToVerification"
                >
                    다시 인증하기
                </button>
            </div>

            <RouterLink
                class="home-link"
                :to="{ name: 'home' }"
            >
                홈으로
            </RouterLink>
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
import * as authApi from "../api/authApi"
import {
    PASSWORD_RECOVERY_METHOD,
    isValidPasswordRecoveryPassword
} from "../utils/passwordRecoveryMethods"
import { useAuthStore } from "../stores/authStore"

const router = useRouter()
const authStore = useAuthStore()
const checkingSession = ref(true)
const authorized = ref(false)
const resetting = ref(false)
const newPassword = ref("")
const confirmPassword = ref("")
const message = ref("")

const passwordMismatch = computed(() =>
    confirmPassword.value.length > 0
    && newPassword.value !== confirmPassword.value
)

const canSubmit = computed(() =>
    authorized.value
    && !resetting.value
    && isValidPasswordRecoveryPassword(newPassword.value)
    && newPassword.value === confirmPassword.value
)

function apiErrorMessage(error, fallback) {
    const responseData = error?.response?.data

    if (typeof responseData === "string" && responseData.trim()) {
        return responseData
    }

    return responseData?.message || fallback
}

function clearMessage() {
    message.value = ""
}

async function goToVerification() {
    await router.replace({
        name: "forgot-password"
    })
}

async function checkRecoverySession() {
    checkingSession.value = true
    message.value = ""

    try {
        const session =
            await authApi.getPasswordRecoverySession()
        const supportedMethod = Object.values(
            PASSWORD_RECOVERY_METHOD
        ).includes(session?.method)

        authorized.value =
            session?.authorized === true
            && supportedMethod

        if (!authorized.value) {
            await goToVerification()
        }
    } catch (error) {
        authorized.value = false
        const status = error?.response?.status

        if (status === 401 || status === 403) {
            await goToVerification()
            return
        }

        message.value = apiErrorMessage(
            error,
            "인증 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요."
        )
    } finally {
        checkingSession.value = false
    }
}

async function resetPassword() {
    clearMessage()

    if (!canSubmit.value) {
        message.value =
            "새 비밀번호를 확인하고 다시 입력해주세요."
        return
    }

    resetting.value = true

    try {
        const result = await authApi.resetForgottenPassword({
            newPassword: newPassword.value
        })

        window.alert(
            result?.message
            || "비밀번호가 변경되었습니다."
        )

        // The database increments credential_version during reset, so any
        // token that was already in this browser is stale immediately.
        authStore.clearAuth()

        await router.replace({
            name: "home"
        })
    } catch (error) {
        const status = error?.response?.status

        if (status === 401 || status === 403) {
            window.alert(
                "비밀번호 재설정 인증이 만료되었습니다. 다시 인증해주세요."
            )
            await goToVerification()
            return
        }

        message.value = apiErrorMessage(
            error,
            "비밀번호 변경에 실패했습니다."
        )
    } finally {
        resetting.value = false
    }
}

onMounted(checkRecoverySession)
</script>

<style scoped>
.reset-page {
    min-height: 72vh;
    display: grid;
    place-items: start center;
    padding: 48px 20px 24px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.reset-card {
    width: min(560px, 100%);
    display: grid;
    gap: 24px;
    padding: 38px 34px;
    border: 1px solid #dce5f1;
    border-radius: 12px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
    box-sizing: border-box;
}

.reset-header {
    display: grid;
    gap: 8px;
    text-align: center;
}

.reset-header h1 {
    margin: 0;
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
    line-height: 1.2;
}

.reset-header p {
    margin: 0;
    color: #52637d;
    font-size: 14px;
}

.reset-form,
.invalid-session {
    display: grid;
    gap: 14px;
}

.reset-form label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
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

.primary-button,
.secondary-button {
    min-height: 46px;
    padding: 0 16px;
    border-radius: 8px;
    font: inherit;
    font-size: 15px;
    font-weight: 900;
    cursor: pointer;
}

.primary-button {
    border: 0;
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
}

.secondary-button {
    border: 1px solid #9fb0c9;
    color: #071b3a;
    background: #ffffff;
}

.primary-button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
}

.status-message {
    margin: 0;
    padding: 12px 14px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 800;
    line-height: 1.5;
    text-align: center;
}

.status-message.info {
    color: #1c3d70;
    background: #eef4fc;
}

.status-message.error {
    color: #b42318;
    background: #fff1f1;
}

.home-link {
    justify-self: center;
    color: #071b3a;
    font-size: 15px;
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 4px;
}

.home-link:hover {
    color: #0f2f5f;
}

@media (max-width: 560px) {
    .reset-page {
        padding-top: 28px;
    }

    .reset-card {
        padding: 28px 20px;
    }

    .reset-header h1 {
        font-size: 32px;
    }
}
</style>
