<template>
    <section class="email-panel">
        <label for="recovery-email-address">
            <span>가입 이메일</span>

            <div class="request-row">
                <input
                    id="recovery-email-address"
                    :value="emailAddress"
                    type="email"
                    autocomplete="email"
                    maxlength="100"
                    placeholder="name@example.com"
                    :disabled="disabled || sending || verifying"
                    @input="$emit(
                        'update:emailAddress',
                        $event.target.value
                    )"
                >

                <button
                    type="button"
                    class="secondary-button"
                    :disabled="requestDisabled"
                    @click="$emit('send')"
                >
                    {{ requestButtonLabel }}
                </button>
            </div>
        </label>

        <label
            v-if="challengeStarted"
            for="recovery-email-code"
        >
            <span>인증번호</span>

            <div class="request-row">
                <input
                    id="recovery-email-code"
                    :value="code"
                    type="text"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    maxlength="6"
                    placeholder="6자리 인증번호"
                    :disabled="disabled || verifying"
                    @input="$emit(
                        'update:code',
                        $event.target.value
                    )"
                >

                <button
                    type="button"
                    class="primary-button"
                    :disabled="verifyDisabled"
                    @click="$emit('verify')"
                >
                    {{ verifying ? "확인 중..." : "인증번호 확인" }}
                </button>
            </div>
        </label>

        <p
            v-if="message"
            class="panel-message"
            :class="messageType"
            role="status"
        >
            {{ message }}
        </p>
    </section>
</template>

<script setup>
import { computed } from "vue"

const props = defineProps({
    emailAddress: {
        type: String,
        default: ""
    },
    code: {
        type: String,
        default: ""
    },
    challengeStarted: {
        type: Boolean,
        default: false
    },
    sending: {
        type: Boolean,
        default: false
    },
    verifying: {
        type: Boolean,
        default: false
    },
    resendSeconds: {
        type: Number,
        default: 0
    },
    message: {
        type: String,
        default: ""
    },
    messageType: {
        type: String,
        default: ""
    },
    disabled: {
        type: Boolean,
        default: false
    }
})

defineEmits([
    "update:emailAddress",
    "update:code",
    "send",
    "verify"
])

const requestDisabled = computed(() =>
    props.disabled
    || props.sending
    || props.resendSeconds > 0
    || !props.emailAddress.trim()
)

const verifyDisabled = computed(() =>
    props.disabled
    || props.verifying
    || !/^\d{6}$/.test(props.code)
)

const requestButtonLabel = computed(() => {
    if (props.sending) {
        return "발송 중..."
    }

    if (props.resendSeconds > 0) {
        return `재발송 ${props.resendSeconds}초`
    }

    return props.challengeStarted
        ? "인증번호 재발송"
        : "인증번호 받기"
})
</script>

<style scoped>
.email-panel {
    display: grid;
    gap: 14px;
    padding: 18px;
    border: 1px solid #dce5f1;
    border-radius: 10px;
    background: #f8fafc;
}

label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

.request-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 10px;
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

button {
    min-width: 138px;
    min-height: 46px;
    padding: 0 14px;
    border-radius: 8px;
    font: inherit;
    font-size: 13px;
    font-weight: 900;
    cursor: pointer;
}

.primary-button {
    border: 0;
    color: #ffffff;
    background: #071b3a;
}

.secondary-button {
    border: 1px solid #9fb0c9;
    color: #071b3a;
    background: #ffffff;
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
}

.panel-message {
    margin: 0;
    padding: 11px 12px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 800;
    line-height: 1.5;
    text-align: center;
}

.panel-message.success {
    color: #067647;
    background: #e7f7ef;
}

.panel-message.error {
    color: #b42318;
    background: #fff1f1;
}

@media (max-width: 560px) {
    .request-row {
        grid-template-columns: 1fr;
    }

    button {
        width: 100%;
    }
}
</style>
