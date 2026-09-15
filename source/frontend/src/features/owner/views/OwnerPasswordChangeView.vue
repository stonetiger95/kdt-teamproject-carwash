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
                        ? "변경 중..."
                        : "비밀번호 변경"
                }}
            </button>

        </form>

    </main>

</template>


<script setup>

import { reactive, ref } from "vue"
import { useRouter } from "vue-router"

import * as ownerApi
    from "@/features/owner/api/ownerApi"
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

        await ownerApi.changePassword({

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

    } catch (error) {

        errorMessage.value =
            error.response?.data?.message ||
            "비밀번호 변경에 실패했습니다."

    } finally {

        loading.value = false
    }
}

</script>


<style scoped>

* {
    box-sizing: border-box;
}

main {
    max-width: 700px;
    margin: 50px auto;
    padding: 0 20px;
    font-family: "Pretendard", sans-serif;
}

h1 {
    font-size: 30px;
    color: #1e3a5f;
    font-weight: 700;
    margin-bottom: 35px;
}

form {
    background: #fff;
    border-radius: 18px;
    padding: 35px;
    border: 1px solid #e7ecf3;
    box-shadow: 0 10px 25px rgba(0,0,0,.08);
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
    width: 170px;
    font-weight: 700;
    color: #1e3a5f;
    flex-shrink: 0;
}

input {
    flex: 1;
    height: 46px;
    padding: 0 14px;
    border: 1px solid #ccd5e0;
    border-radius: 10px;
    background: #fafbfd;
    font-size: 15px;
    transition: .25s;
}

input:focus {
    outline: none;
    border-color: #1e3a5f;
    box-shadow: 0 0 0 3px rgba(30,58,95,.12);
    background: #fff;
}

button {
    display: block;
    width: 50%;
    margin: 35px auto 0;
    height: 48px;
    border: none;
    border-radius: 10px;
    background: #1e3a5f;
    color: white;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: .25s;
}

button:hover:not(:disabled) {
    background: #284d77;
    transform: translateY(-2px);
}

button:disabled {
    background: #8ca1ba;
    cursor: not-allowed;
}

p {
    margin-top: 18px;
    text-align: center;
    font-size: 14px;
    font-weight: 600;
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
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
    }

    label {
        width: 100%;
    }

    input {
        width: 100%;
    }

}

</style>
