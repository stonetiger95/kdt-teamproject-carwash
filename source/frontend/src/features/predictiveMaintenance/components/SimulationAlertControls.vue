<template>
    <div :class="['simulation-alert-controls', { compact }]">
        <label :class="['alert-toggle', { active: modelValue }]">
            <input :checked="modelValue" type="checkbox" @change="$emit('update:modelValue', $event.target.checked)">
            <span>알림 발송</span>
            <b v-if="!compact">{{ modelValue ? 'ON' : 'OFF' }}</b>
        </label>
        <button :class="['simulation-button', { running }]" type="button" :disabled="busy" @click="$emit('toggle-simulation')">
            {{ busy ? '처리 중' : running ? '정지' : '시연 시작' }}
        </button>
    </div>
</template>

<script setup>
defineProps({
    modelValue: { type: Boolean, default: false },
    running: { type: Boolean, default: false },
    busy: { type: Boolean, default: false },
    compact: { type: Boolean, default: false }
})

defineEmits(["update:modelValue", "toggle-simulation"])
</script>

<style scoped>
.simulation-alert-controls{display:flex;flex:0 0 auto;align-items:center;gap:10px}.alert-toggle{display:flex;flex:0 0 auto;align-items:center;gap:7px;padding:7px 10px;border:1px solid #d8e1ed;border-radius:8px;background:#fff;color:#64748b;font-size:11px;font-weight:800;white-space:nowrap;cursor:pointer}.alert-toggle.active{border-color:#85b4ee;background:#eff6ff;color:#2563eb}.alert-toggle input{flex:0 0 auto;width:14px;height:14px;margin:0;accent-color:#2563eb}.alert-toggle b{font-size:10px}.simulation-button{flex:0 0 auto;padding:9px 13px;border:0;border-radius:8px;background:#7655d9;color:#fff;font-size:11px;font-weight:800;white-space:nowrap;cursor:pointer}.simulation-button.running{background:#d9485f}.simulation-button:disabled{cursor:wait;opacity:.6}.compact{gap:7px}.compact .alert-toggle{gap:4px;padding:0;border:0;background:transparent;font-size:10px}.compact .alert-toggle input{width:13px;height:13px}.compact .simulation-button{padding:6px 9px;border-radius:7px;font-size:10px}
</style>
