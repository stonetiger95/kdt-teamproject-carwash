<template>
    <div class="owner-layout">
        <header class="owner-page-title">
            비즈니스 파트너 페이지
        </header>
        <AppHeader />
    </div>
</template>

<script setup>
import AppHeader from "@/shared/components/AppHeader.vue"
</script>

<style scoped>
.owner-layout {
    min-height: 100vh;
    background: #f8fafc;
}

.owner-page-title {
    padding: 10px 16px 6px;
    color: #071b3a;
    background: #ffffff;
    border-bottom: 1px solid #eef2f6;
    font-size: 16px;
    font-weight: 700;
    text-align: center;
    letter-spacing: 0;
}
</style>
