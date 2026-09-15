<template>

    <main>

        <h1>비밀번호 변경</h1>

        <form @submit.prevent="submitChangePassword">

            <div>
                <label>
                    현재 비밀번호
                </label>

                <input
                    v-model="form.currentPassword"
                    type="password"
                    required
                >
            </div>


            <div>
                <label>
                    새 비밀번호
                </label>

                <input
                    v-model="form.newPassword"
                    type="password"
                    required
                >
            </div>


            <div>
                <label>
                    새 비밀번호 확인
                </label>

                <input
                    v-model="form.confirmPassword"
                    type="password"
                    required
                >
            </div>


            <p v-if="message">
                {{ message }}
            </p>


            <p v-if="errorMessage">
                {{ errorMessage }}
            </p>


            <button
                type="submit"
                :disabled="loading"
            >
                {{
                    loading
                        ? "저장 중..."
                        : "비밀번호 저장"
                }}
            </button>

        </form>

    </main>

</template>


<script setup>

import { reactive, ref } from "vue"
import { useRouter } from "vue-router"

import * as customerApi
    from "@/features/customer/api/customerApi"
import { useAuthStore }
    from "@/features/auth/stores/authStore"


const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)

const message = ref("")

const errorMessage = ref("")


const form = reactive({

    currentPassword: "",

    newPassword: "",

    confirmPassword: ""

})


async function submitChangePassword() {

    message.value = ""

    errorMessage.value = ""


    if (
        form.newPassword !==
        form.confirmPassword
    ) {

        errorMessage.value =
            "새 비밀번호가 일치하지 않습니다."

        return
    }


    if (form.newPassword.length < 4) {

        errorMessage.value =
            "새 비밀번호는 4자 이상이어야 합니다."

        return
    }


    loading.value = true


    try {

        await customerApi.changePassword({

            currentPassword:
                form.currentPassword,

            newPassword:
                form.newPassword

        })


        message.value =
            "비밀번호가 변경되었습니다."


        form.currentPassword = ""

        form.newPassword = ""

        form.confirmPassword = ""


        await authStore.logout()

        await router.replace({
            name: "home"
        })


    } catch (e) {

        errorMessage.value =
            e.response?.data?.message ||
            e.response?.data ||
            "비밀번호 변경에 실패했습니다."

    } finally {

        loading.value = false
    }
}

</script>


<style scoped>
.password-page {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    padding: 32px 16px;
    background: #f5f7fb;
}

.password-card {
    width: 100%;
    max-width: 420px;
    padding: 36px 32px;
    background: #ffffff;
    border: 1px solid #e5e8ef;
    border-radius: 8px;
    box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}

h1 {
    margin: 0 0 28px;
    color: #111827;
    font-size: 26px;
    font-weight: 700;
    text-align: center;
}

form {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.form-field {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

label {
    color: #374151;
    font-size: 14px;
    font-weight: 600;
}

input {
    width: 100%;
    height: 46px;
    padding: 0 14px;
    color: #111827;
    font-size: 15px;
    background: #ffffff;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    outline: none;
    transition:
        border-color 0.2s ease,
        box-shadow 0.2s ease;
}

input:focus {
    border-color: #2563eb;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.message {
    margin: 0;
    padding: 12px 14px;
    font-size: 14px;
    line-height: 1.5;
    border-radius: 6px;
}

.success {
    color: #047857;
    background: #ecfdf5;
    border: 1px solid #a7f3d0;
}

.error {
    color: #b91c1c;
    background: #fef2f2;
    border: 1px solid #fecaca;
}

button {
    height: 48px;
    margin-top: 4px;
    color: #ffffff;
    font-size: 15px;
    font-weight: 700;
    background: #2563eb;
    border: 0;
    border-radius: 6px;
    cursor: pointer;
    transition:
        background 0.2s ease,
        transform 0.15s ease,
        box-shadow 0.2s ease;
}

button:hover:not(:disabled) {
    background: #1d4ed8;
    box-shadow: 0 8px 18px rgba(37, 99, 235, 0.24);
    transform: translateY(-1px);
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.65;
}

@media (max-width: 480px) {
    .password-card {
        padding: 28px 22px;
    }

    h1 {
        font-size: 23px;
    }
}

/* Keep the customer password form visually consistent with the owner form. */
* {
    box-sizing: border-box;
}

main {
    max-width: 700px;
    min-height: auto;
    margin: 50px auto;
    padding: 0 20px;
    font-family: "Pretendard", sans-serif;
    background: transparent;
}

h1 {
    margin: 0 0 35px;
    color: #1e3a5f;
    font-size: 30px;
    font-weight: 700;
    text-align: left;
}

form {
    display: block;
    padding: 35px;
    background: #fff;
    border: 1px solid #e7ecf3;
    border-radius: 18px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
}

form > div {
    display: flex;
    align-items: center;
    padding: 18px 0;
    border-bottom: 1px solid #eef2f7;
}

form > div:last-of-type {
    margin-bottom: 10px;
}

label {
    flex-shrink: 0;
    width: 170px;
    color: #1e3a5f;
    font-weight: 700;
}

input {
    flex: 1;
    width: auto;
    height: 46px;
    padding: 0 14px;
    font-size: 15px;
    background: #fafbfd;
    border: 1px solid #ccd5e0;
    border-radius: 10px;
    transition: 0.25s;
}

input:focus {
    background: #fff;
    border-color: #1e3a5f;
    outline: none;
    box-shadow: 0 0 0 3px rgba(30, 58, 95, 0.12);
}

button {
    display: block;
    width: 50%;
    height: 48px;
    margin: 35px auto 0;
    color: white;
    font-size: 15px;
    font-weight: 600;
    background: #1e3a5f;
    border: none;
    border-radius: 10px;
    transition: 0.25s;
}

button:hover:not(:disabled) {
    background: #284d77;
    box-shadow: none;
    transform: translateY(-2px);
}

button:disabled {
    background: #8ca1ba;
    opacity: 1;
}

p {
    margin-top: 18px;
    font-size: 14px;
    font-weight: 600;
    text-align: center;
}

p:first-of-type {
    color: #1b7f3b;
}

p:last-of-type {
    color: #d93025;
}

@media (max-width: 768px) {
    form {
        padding: 24px;
    }

    form > div {
        align-items: flex-start;
        flex-direction: column;
        gap: 10px;
    }

    label,
    input {
        width: 100%;
    }
}
</style>
