<template>
    <main class="callback-page">
        <section class="callback-panel" aria-live="polite">
            <span class="naver-mark" aria-hidden="true">N</span>
            <h1>{{ errorMessage ? "로그인을 완료하지 못했습니다" : "네이버 로그인 확인 중" }}</h1>
            <p v-if="!errorMessage">잠시만 기다려주세요.</p>
            <p v-else class="error-message" role="alert">{{ errorMessage }}</p>
            <RouterLink
                v-if="errorMessage"
                class="retry-link"
                :to="{ name: 'login' }"
            >
                로그인 화면으로
            </RouterLink>
        </section>
    </main>
</template>

<script setup>
import { onMounted, ref } from "vue"
import { RouterLink, useRoute, useRouter } from "vue-router"
import * as authApi from "../api/authApi"
import { useAuthStore } from "../stores/authStore"

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const errorMessage = ref("")

onMounted(async () => {
    const state = stringQuery("state")
    const code = stringQuery("code")
    const error = stringQuery("error")
    const errorDescription = stringQuery("error_description")

    if (!state || (!code && !error)) {
        errorMessage.value = "네이버 로그인 응답값이 올바르지 않습니다."
        return
    }

    try {
        const response = await authApi.completeNaverCallback({
            code: code || undefined,
            state,
            error: error || undefined,
            error_description: errorDescription || undefined
        })

        if (response.status === "LOGIN_SUCCESS" && response.login) {
            authStore.applyLoginResponse(response.login)
            const redirect = safeRedirect(
                sessionStorage.getItem("naverLoginRedirect")
            )
            sessionStorage.removeItem("naverLoginRedirect")
            await router.replace(redirect || { name: "customer-dashboard" })
            return
        }

        if (
            response.status === "REGISTRATION_REQUIRED"
            && response.registrationToken
        ) {
            sessionStorage.setItem(
                "naverRegistrationToken",
                response.registrationToken
            )
            sessionStorage.setItem(
                "naverRegistrationProfile",
                JSON.stringify(response.profile || {})
            )
            await router.replace({ name: "naver-signup" })
            return
        }

        errorMessage.value = "네이버 로그인 처리 결과를 확인할 수 없습니다."
    } catch (requestError) {
        errorMessage.value = requestError.response?.data?.message
            || "네이버 로그인을 완료하지 못했습니다."
    }
})

function stringQuery(name) {
    const value = route.query[name]
    return typeof value === "string" ? value : ""
}

function safeRedirect(value) {
    return typeof value === "string"
        && value.startsWith("/")
        && !value.startsWith("//")
        ? value
        : null
}
</script>

<style scoped>
.callback-page {
    min-height: 70vh;
    display: grid;
    place-items: center;
    padding: 32px 18px;
    color: #071b3a;
    background: #f7f9fc;
}

.callback-panel {
    width: min(460px, 100%);
    display: grid;
    justify-items: center;
    gap: 14px;
    padding: 40px 30px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-sizing: border-box;
    text-align: center;
}

.naver-mark {
    width: 48px;
    height: 48px;
    display: grid;
    place-items: center;
    border-radius: 6px;
    color: #ffffff;
    background: #03c75a;
    font-size: 25px;
    font-weight: 900;
}

h1,
p {
    margin: 0;
}

h1 {
    font-size: 24px;
    letter-spacing: 0;
}

p {
    color: #64748b;
}

.error-message {
    color: #b42318;
    line-height: 1.55;
}

.retry-link {
    min-height: 42px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0 18px;
    border-radius: 7px;
    color: #ffffff;
    background: #071b3a;
    font-weight: 800;
    text-decoration: none;
}
</style>
