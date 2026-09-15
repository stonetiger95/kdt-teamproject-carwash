<template>
    <div class="mode-switch" role="group" aria-label="예지보전 데이터 모드">
        <button type="button" :class="{ active: modelValue === 'LIVE' }" @click="$emit('update:modelValue', 'LIVE')"><i></i>운영</button>
        <button type="button" :class="{ active: modelValue === 'DEMO' }" @click="$emit('update:modelValue', 'DEMO')"><i></i>시연</button>
    </div>
</template>

<script setup>
defineProps({ modelValue: { type: String, default: "LIVE" } })
defineEmits(["update:modelValue"])
</script>

<style scoped>
.mode-switch{display:flex;flex:0 0 auto;padding:3px;border-radius:9px;background:#e7edf5}.mode-switch button{display:flex;flex:0 0 auto;align-items:center;gap:5px;padding:6px 10px;border:0;border-radius:7px;background:transparent;color:#64748b;font-size:10px;font-weight:900;white-space:nowrap;cursor:pointer}.mode-switch button.active{background:#fff;color:#17243a;box-shadow:0 2px 7px #17243a18}.mode-switch i{flex:0 0 auto;width:6px;height:6px;border-radius:50%;background:#94a3b8}.mode-switch button:first-child.active i{background:#20a66a}.mode-switch button:last-child.active i{background:#7655d9}
</style>
