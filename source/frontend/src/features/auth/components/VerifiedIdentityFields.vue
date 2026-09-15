<template>
    <div class="verified-identity-fields">
        <div class="form-field">
            <span>이름</span>

            <input
                class="verified-identity-input"
                :value="name"
                type="text"
                autocomplete="off"
                readonly
                :disabled="!verified"
                aria-label="이름"
            >
        </div>

        <div class="form-field">
            <span>휴대폰 번호</span>

            <div class="phone-number-inputs">
                <template
                    v-for="(part, index) in phoneParts"
                    :key="index"
                >
                    <span
                        v-if="index > 0"
                        aria-hidden="true"
                    >
                        -
                    </span>

                    <input
                        class="verified-phone-input"
                        :value="part"
                        type="tel"
                        autocomplete="off"
                        readonly
                        :disabled="!verified"
                        :aria-label="phonePartLabels[index]"
                    >
                </template>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed } from "vue"
import { splitKoreanMobileNumber } from "../utils/signupValidators"

const props = defineProps({
    verified: {
        type: Boolean,
        default: false
    },
    name: {
        type: String,
        default: ""
    },
    phoneNumber: {
        type: String,
        default: ""
    }
})

const phonePartLabels = [
    "휴대폰 번호 앞자리",
    "휴대폰 번호 가운데 자리",
    "휴대폰 번호 뒷자리"
]

const phoneParts = computed(() =>
    splitKoreanMobileNumber(props.phoneNumber)
)
</script>

<style scoped>
.verified-identity-fields {
    display: grid;
    gap: 18px;
}

.form-field {
    display: grid;
    grid-template-columns: 120px 1fr;
    align-items: center;
    gap: 12px;
}

.form-field > span {
    color: #334155;
    font-size: 15px;
    font-weight: 800;
    white-space: nowrap;
}

.phone-number-inputs {
    display: grid;
    grid-template-columns:
        minmax(0, 3fr)
        12px
        minmax(0, 4fr)
        12px
        minmax(0, 4fr);
    align-items: center;
    gap: 6px;
}

.phone-number-inputs > span {
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

input {
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

.verified-identity-input:disabled,
.verified-phone-input:disabled {
    background: #eef2f6;
    color: #94a3b8;
    cursor: not-allowed;
}

@media (max-width: 720px) {
    .form-field {
        grid-template-columns: 1fr;
    }
}
</style>
