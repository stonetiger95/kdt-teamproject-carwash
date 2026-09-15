<template>
    <div class="gender-row">
        <span>성별</span>

        <div
            class="gender-options"
            role="radiogroup"
            aria-label="성별"
        >
            <label
                v-for="option in genderOptions"
                :key="option.value"
                class="gender-option"
                :class="{
                    selected: modelValue === option.value,
                    disabled
                }"
            >
                <input
                    type="radio"
                    name="gender"
                    :value="option.value"
                    :checked="modelValue === option.value"
                    :disabled="disabled"
                    required
                    @change="$emit('update:modelValue', option.value)"
                >
                <span>{{ option.label }}</span>
            </label>
        </div>
    </div>
</template>

<script setup>
defineProps({
    modelValue: {
        type: String,
        default: ""
    },
    disabled: {
        type: Boolean,
        default: false
    }
})

defineEmits(["update:modelValue"])

const genderOptions = [
    { value: "M", label: "남성" },
    { value: "F", label: "여성" },
    { value: "N", label: "선택 안 함" }
]
</script>

<style scoped>
.gender-row {
    display: grid;
    grid-template-columns: 120px 1fr;
    align-items: center;
    gap: 12px;
}

.gender-row > span {
    color: #334155;
    font-size: 15px;
    font-weight: 800;
    white-space: nowrap;
}

.gender-options {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.gender-option {
    padding: 10px 18px;
    border: 1px solid #d7deea;
    border-radius: 8px;
    background: #fff;
    color: #52637d;
    font-weight: 800;
    cursor: pointer;
}

.gender-option input {
    position: absolute;
    width: 1px;
    height: 1px;
    opacity: 0;
}

.gender-option.selected {
    border-color: #071b3a;
    background: #071b3a;
    color: #fff;
}

.gender-option:focus-within {
    box-shadow: 0 0 0 3px rgba(7, 27, 58, 0.12);
}

.gender-option.disabled {
    background: #eef2f6;
    color: #94a3b8;
    cursor: not-allowed;
}

@media (max-width: 720px) {
    .gender-row {
        grid-template-columns: 1fr;
    }
}
</style>
