<template>
    <div class="identity-verification-card">
        <div>
            <strong>휴대폰 본인확인</strong>
            <p>
                {{
                    testMode
                        ? "테스트용"
                        : "PASS 또는 통신사 SMS 인증"
                }}
            </p>
        </div>

        <button
            type="button"
            :disabled="verifying || verified"
            @click="$emit('start')"
        >
            {{ buttonLabel }}
        </button>

        <small
            v-if="message"
            class="identity-message"
            :class="verified ? 'success' : 'error'"
        >
            {{ message }}
        </small>

        <small
            v-if="verified && testMode"
            class="identity-test-warning"
        >
            테스트라서 실제 법적 본인확인이 아닙니다.
        </small>
    </div>
</template>

<script setup>
import { computed } from "vue"

const props = defineProps({
    verifying: {
        type: Boolean,
        default: false
    },
    verified: {
        type: Boolean,
        default: false
    },
    testMode: {
        type: Boolean,
        default: false
    },
    message: {
        type: String,
        default: ""
    }
})

defineEmits(["start"])

const buttonLabel = computed(() => {
    if (props.verifying) {
        return "인증 확인 중..."
    }

    return props.verified
        ? "본인인증 완료"
        : "본인인증 시작"
})
</script>

<style scoped>
.identity-verification-card {
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: center;
    gap: 12px 18px;
    padding: 18px;
    border: 1px solid #cbd5e1;
    border-radius: 12px;
    background: #f8fafc;
}

.identity-verification-card strong {
    color: #071b3a;
    font-size: 16px;
}

.identity-verification-card p {
    margin: 6px 0 0;
    color: #52637d;
    font-size: 13px;
    line-height: 1.5;
}

.identity-verification-card > button {
    min-width: 142px;
    height: 42px;
    padding: 0 16px;
    border: 0;
    border-radius: 8px;
    background: #071b3a;
    color: #fff;
    font-weight: 900;
    cursor: pointer;
}

.identity-verification-card > button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

.identity-message,
.identity-test-warning {
    grid-column: 1 / -1;
    font-size: 13px;
}

.identity-message.success {
    color: #067647;
}

.identity-message.error,
.identity-test-warning {
    color: #b42318;
}

@media (max-width: 720px) {
    .identity-verification-card {
        grid-template-columns: 1fr;
    }

    .identity-verification-card > button {
        width: 100%;
    }
}
</style>
