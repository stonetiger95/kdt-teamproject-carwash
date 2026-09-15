<template>
    <fieldset
        class="method-selector"
        :disabled="disabled"
    >
        <legend>인증 방법</legend>

        <div class="method-options">
            <button
                v-for="option in methodOptions"
                :key="option.value"
                type="button"
                role="radio"
                class="method-option"
                :aria-checked="modelValue === option.value"
                :disabled="disabled"
                :class="{
                    selected: modelValue === option.value
                }"
                @click="activateOption(option.value)"
            >
                <strong>{{ option.label }}</strong>
            </button>
        </div>
    </fieldset>
</template>

<script setup>
import { PASSWORD_RECOVERY_METHOD } from "../utils/passwordRecoveryMethods"

const props = defineProps({
    modelValue: {
        type: String,
        required: true
    },
    disabled: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits([
    "update:modelValue",
    "activate"
])

function activateOption(value) {
    if (props.disabled) {
        return
    }

    if (props.modelValue !== value) {
        emit("update:modelValue", value)
    }

    emit("activate", value)
}

const methodOptions = [
    {
        value: PASSWORD_RECOVERY_METHOD.PHONE_IDENTITY,
        label: "휴대폰 본인인증"
    },
    {
        value: PASSWORD_RECOVERY_METHOD.EASY_AUTH,
        label: "간편인증"
    },
    {
        value: PASSWORD_RECOVERY_METHOD.EMAIL_OTP,
        label: "이메일 인증"
    }
]
</script>

<style scoped>
.method-selector {
    min-width: 0;
    margin: 0;
    padding: 0;
    border: 0;
}

.method-selector legend {
    margin-bottom: 9px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

.method-options {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
}

.method-option {
    display: flex;
    justify-content: center;
    align-items: center;
    min-width: 0;
    min-height: 52px;
    padding: 10px 8px;
    border: 1px solid #cbd6e6;
    border-radius: 10px;
    background: #ffffff;
    font: inherit;
    cursor: pointer;
    transition: border-color 0.16s ease, background 0.16s ease;
}

.method-option.selected {
    border-color: #1c3d70;
    background: #f2f6fc;
}

.method-option:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.method-option strong {
    color: #071b3a;
    font-size: 14px;
    text-align: center;
    word-break: keep-all;
}

.method-selector:disabled .method-option {
    cursor: not-allowed;
    opacity: 0.65;
}

@media (max-width: 430px) {
    .method-options {
        gap: 6px;
    }

    .method-option {
        padding-inline: 5px;
    }

    .method-option strong {
        font-size: 12px;
    }
}
</style>
