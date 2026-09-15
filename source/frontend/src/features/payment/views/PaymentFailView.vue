<template>
    <main class="payment-result">
        <h1>결제 실패</h1>

        <p>코드: {{ route.query.code || "-" }}</p>
        <p>메시지: {{ route.query.message || "결제가 취소되었거나 실패했습니다." }}</p>

        <RouterLink
            v-if="canStartPayment"
            :to="{ name: 'customer-payment-checkout' }"
        >
            다시 결제
        </RouterLink>
    </main>
</template>

<script setup>
import { computed } from "vue"
import { useRoute } from "vue-router"
import { useAuthStore } from "@/features/auth/stores/authStore"

const route = useRoute()
const authStore = useAuthStore()
const canStartPayment = computed(() => authStore.canUseFeature("payment"))
</script>

<style scoped>
.payment-result {
    display: grid;
    gap: 14px;
    padding: 40px 20px;
}
</style>
