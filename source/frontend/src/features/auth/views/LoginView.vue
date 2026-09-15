<template>
    <main class="login-page">
        <section class="login-card" aria-labelledby="login-title">
            <header class="login-heading">
                <p>MEMBER</p>
                <h1 id="login-title">회원 로그인</h1>
                <span>아이디와 비밀번호를 입력해주세요.</span>
            </header>

            <LoginForm mode="password" />

            <RouterLink
                class="home-link"
                :to="{ name: 'home' }"
            >
                홈으로 돌아가기
            </RouterLink>
        </section>
    </main>
</template>

<script setup>
import { RouterLink } from "vue-router"
import LoginForm from "@/features/auth/components/LoginForm.vue"
</script>

<style scoped>
.login-page {
    min-height: max(100svh, var(--app-shell-height, 640px));
    display: grid;
    place-items: center;
    padding: 32px 20px;
    color: #071b3a;
    background: #f4f7fb;
    box-sizing: border-box;
    font-family: "Jua", "Apple SD Gothic Neo", "맑은 고딕", sans-serif;
}

.login-card {
    width: min(100%, 480px);
    padding: 36px;
    border: 1px solid #d9e3f0;
    border-top: 3px solid #1c63b7;
    border-radius: 12px;
    background: #ffffff;
    box-shadow: 0 18px 42px rgba(7, 27, 58, 0.1);
    box-sizing: border-box;
}

.login-heading {
    margin-bottom: 26px;
    text-align: center;
}

.login-heading p {
    margin: 0 0 6px;
    color: #557296;
    font-size: 12px;
    font-weight: 900;
}

.login-heading h1 {
    margin: 0;
    font-size: 28px;
}

.login-heading span {
    display: block;
    margin-top: 10px;
    color: #687991;
    font-size: 14px;
}

.home-link {
    display: block;
    width: max-content;
    margin: 22px auto 0;
    color: #415370;
    font-size: 13px;
    font-weight: 800;
    text-decoration: none;
    border-bottom: 1px solid #9aa9bc;
}

.home-link:hover {
    color: #071b3a;
    border-color: #071b3a;
}

@media (max-width: 520px) {
    .login-page {
        padding: 18px 14px;
    }

    .login-card {
        padding: 28px 20px;
    }
}
</style>
