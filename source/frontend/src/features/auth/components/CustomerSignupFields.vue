<template>
    <div class="customer-signup-fields">
        <GenderSelector
            :model-value="gender"
            :disabled="submitting"
            @update:model-value="$emit('update:gender', $event)"
        />

        <div class="login-id-row">
            <span>아이디</span>

            <div class="login-id-inputs">
                <input
                    :value="loginId"
                    type="text"
                    required
                    maxlength="50"
                    autocomplete="username"
                    autocapitalize="none"
                    spellcheck="false"
                    placeholder="영문 및 숫자"
                    :disabled="submitting"
                    @input="handleLoginIdInput"
                >

                <button
                    class="check-button"
                    type="button"
                    :disabled="submitting || checkingLoginId || !loginId"
                    @click="$emit('checkLoginId')"
                >
                    {{ checkingLoginId ? "확인 중..." : "중복 확인" }}
                </button>
            </div>

            <small
                v-if="loginIdMessage"
                class="field-message"
                :class="loginIdAvailable ? 'success' : 'error'"
                role="status"
            >
                {{ loginIdMessage }}
            </small>
        </div>

        <div class="password-row">
            <span>비밀번호</span>

            <div class="password-inputs">
                <input
                    :value="password"
                    type="password"
                    required
                    minlength="4"
                    maxlength="72"
                    autocomplete="new-password"
                    placeholder="비밀번호"
                    :disabled="submitting"
                    @input="$emit('update:password', $event.target.value)"
                >

                <input
                    :value="confirmPassword"
                    type="password"
                    required
                    minlength="4"
                    maxlength="72"
                    autocomplete="new-password"
                    placeholder="비밀번호 확인"
                    :disabled="submitting"
                    @input="$emit('update:confirmPassword', $event.target.value)"
                >
            </div>

            <small
                v-if="passwordsMismatch"
                class="field-message error"
            >
                비밀번호가 다릅니다.
            </small>
        </div>

        <div class="email-row">
            <span>이메일</span>

            <div class="email-inputs">
                <input
                    :value="emailId"
                    type="text"
                    required
                    placeholder="이메일 아이디"
                    :disabled="submitting"
                    @input="$emit('update:emailId', $event.target.value)"
                >

                <span>@</span>

                <input
                    v-if="emailDomainOption === 'direct'"
                    :value="emailDomain"
                    type="text"
                    required
                    placeholder="직접 입력"
                    :disabled="submitting"
                    @input="$emit('update:emailDomain', $event.target.value)"
                >

                <input
                    v-else
                    :value="emailDomainOption"
                    type="text"
                    readonly
                    :disabled="submitting"
                >

                <select
                    :value="emailDomainOption"
                    :disabled="submitting"
                    @change="$emit('update:emailDomainOption', $event.target.value)"
                >
                    <option value="naver.com">
                        naver.com
                    </option>
                    <option value="gmail.com">
                        gmail.com
                    </option>
                    <option value="daum.net">
                        daum.net
                    </option>
                    <option value="hanmail.net">
                        hanmail.net
                    </option>
                    <option value="direct">
                        직접 입력
                    </option>
                </select>
            </div>

            <small
                v-if="emailMessage"
                class="field-message error"
            >
                {{ emailMessage }}
            </small>
        </div>
    </div>
</template>

<script setup>
import GenderSelector from "./GenderSelector.vue"
import { sanitizeLoginId } from "../utils/signupValidators"

defineProps({
    loginId: {
        type: String,
        default: ""
    },
    password: {
        type: String,
        default: ""
    },
    confirmPassword: {
        type: String,
        default: ""
    },
    gender: {
        type: String,
        default: ""
    },
    emailId: {
        type: String,
        default: ""
    },
    emailDomainOption: {
        type: String,
        default: "naver.com"
    },
    emailDomain: {
        type: String,
        default: ""
    },
    loginIdMessage: {
        type: String,
        default: ""
    },
    checkingLoginId: {
        type: Boolean,
        default: false
    },
    loginIdAvailable: {
        type: Boolean,
        default: false
    },
    emailMessage: {
        type: String,
        default: ""
    },
    passwordsMismatch: {
        type: Boolean,
        default: false
    },
    submitting: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits([
    "update:loginId",
    "update:password",
    "update:confirmPassword",
    "update:gender",
    "update:emailId",
    "update:emailDomainOption",
    "update:emailDomain",
    "checkLoginId"
])

function handleLoginIdInput(event) {
    const rawValue = event.target.value

    event.target.value = sanitizeLoginId(rawValue)
    emit("update:loginId", rawValue)
}
</script>

<style scoped>
.customer-signup-fields {
    display: grid;
    gap: 18px;
}

.login-id-row,
.password-row,
.email-row {
    display: grid;
    grid-template-columns: 120px 1fr;
    align-items: center;
    gap: 12px;
}

.login-id-row > span,
.password-row > span,
.email-row > span {
    color: #334155;
    font-size: 15px;
    font-weight: 800;
    white-space: nowrap;
}

.password-inputs {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
}

.login-id-inputs {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
}

.check-button {
    min-width: 96px;
    height: 42px;
    padding: 0 14px;
    border: 1px solid #071b3a;
    border-radius: 8px;
    color: #071b3a;
    background: #fff;
    font-size: 14px;
    font-weight: 800;
    white-space: nowrap;
    cursor: pointer;
}

.check-button:hover:not(:disabled) {
    color: #fff;
    background: #071b3a;
}

.check-button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

input,
select {
    width: 100%;
    height: 42px;
    padding: 0 14px;
    border: 1px solid #d7deea;
    border-radius: 8px;
    background: #f8fafc;
    color: #071b3a;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;
}

input:focus,
select:focus {
    border-color: #071b3a;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(7, 27, 58, 0.08);
}

input:disabled,
select:disabled {
    cursor: not-allowed;
}

.login-id-row small,
.password-row > .field-message,
.email-row > .field-message {
    grid-column: 2;
    margin: -4px 0 0;
    font-size: 13px;
}

.field-message.error {
    color: #b42318;
}

.field-message.success {
    color: #067647;
}

.email-inputs {
    display: grid;
    grid-template-columns: 1fr 18px 1fr 130px;
    align-items: center;
    gap: 8px;
}

.email-inputs span {
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

@media (max-width: 720px) {
    .login-id-row,
    .login-id-inputs,
    .password-row,
    .password-inputs,
    .email-row,
    .email-inputs {
        grid-template-columns: 1fr;
    }

    .login-id-row small,
    .password-row > .field-message,
    .email-row > .field-message {
        grid-column: auto;
    }

    .email-inputs span {
        display: none;
    }
}
</style>
