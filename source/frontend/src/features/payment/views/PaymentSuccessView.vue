<template>
    <main class="payment-result">
        <section class="result-panel">
            <header class="result-header">
                <p class="eyebrow">PAYMENT RESULT</p>
                <h1>결제 상태 확인</h1>
                <p class="result-help">
                    결제 요청의 현재 처리 상태입니다.
                </p>
            </header>

            <p
                v-if="paymentStore.loading"
                class="result-message"
            >
                결제 상태를 확인하고 있습니다.
            </p>

            <p
                v-if="paymentStore.error"
                class="result-message error"
            >
                {{ paymentStore.error }}
            </p>

            <section
                v-if="paymentStore.payment"
                class="payment-summary"
            >
                <div class="status-row">
                    <span>결제 상태</span>
                    <strong
                        class="status-badge"
                        :class="statusClass(paymentStore.payment.status)"
                    >
                        {{ statusLabel(paymentStore.payment.status) }}
                    </strong>
                </div>

                <dl class="payment-details">
                    <div>
                        <dt>주문번호</dt>
                        <dd>{{ paymentStore.payment.orderId || "-" }}</dd>
                    </div>
                    <div>
                        <dt>상품명</dt>
                        <dd>{{ paymentStore.payment.orderName || "-" }}</dd>
                    </div>
                    <div>
                        <dt>결제금액</dt>
                        <dd class="amount">
                            {{ formatAmount(paymentStore.payment.amount) }}
                        </dd>
                    </div>
                </dl>

                <p
                    v-if="paymentStore.payment.status === 'WAITING'"
                    class="waiting-message"
                >
                    결제 제공사의 처리 결과를 기다리는 중입니다.
                </p>

                <a
                    v-if="paymentStore.payment.receiptUrl"
                    class="receipt-link"
                    :href="paymentStore.payment.receiptUrl"
                    target="_blank"
                    rel="noreferrer"
                >
                    영수증 보기
                </a>
            </section>

            <RouterLink
                class="history-link"
                :to="{ name: 'customer-payments' }"
            >
                결제 내역
            </RouterLink>
        </section>
    </main>
</template>

<script setup>
import { onMounted, onUnmounted } from "vue"
import { useRoute } from "vue-router"
import { usePaymentStore } from "../stores/paymentStore"

const route = useRoute()
const paymentStore = usePaymentStore()
let pollTimer = null

onMounted(() => {
    fetchPaymentStatus()
    pollTimer = window.setInterval(fetchPaymentStatus, 3000)
})

onUnmounted(() => {
    if (pollTimer) {
        window.clearInterval(pollTimer)
    }
})

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString()}원`
}

function statusLabel(status) {
    if (status === "SUCCESS" || status === "DONE") {
        return "결제 성공"
    }

    if (status === "CANCELED") {
        return "결제 취소"
    }

    if (status === "FAILED") {
        return "결제 실패"
    }

    return "결제 대기"
}

function statusClass(status) {
    if (status === "SUCCESS" || status === "DONE") {
        return "success"
    }

    if (status === "CANCELED" || status === "FAILED") {
        return "failure"
    }

    return "waiting"
}

async function fetchPaymentStatus() {
    if (!route.query.paymentId) {
        paymentStore.error = "결제번호가 없습니다."
        return
    }

    const payment = await paymentStore.fetchPayment(route.query.paymentId)

    if (payment && payment.status !== "WAITING" && pollTimer) {
        window.clearInterval(pollTimer)
        pollTimer = null
    }
}
</script>

<style scoped>
.payment-result {
    min-height: 72vh;
    display: grid;
    place-items: start center;
    padding: 40px 16px;
    color: #071b3a;
    background: #f4f7fb;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
    box-sizing: border-box;
}

.result-panel {
    width: min(560px, 100%);
    display: grid;
    gap: 20px;
    padding: 34px;
    background: #ffffff;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
    box-sizing: border-box;
}

.result-header {
    display: grid;
    gap: 8px;
    text-align: left;
}

.eyebrow,
.result-header h1,
.result-help,
.result-message,
.waiting-message {
    margin: 0;
}

.eyebrow {
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

.result-header h1 {
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
    line-height: 1.2;
}

.result-help {
    color: #52637d;
    font-size: 14px;
}

.result-message {
    padding: 14px;
    color: #43536f;
    font-size: 14px;
    font-weight: 800;
    background: #f4f7fb;
    border-radius: 8px;
}

.result-message.error {
    color: #b42318;
    background: #fff1f1;
}

.payment-summary {
    display: grid;
    gap: 18px;
    padding: 20px;
    background: #f8fafc;
    border: 1px solid #dce5f1;
    border-radius: 8px;
}

.status-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    color: #52637d;
    font-size: 14px;
    font-weight: 800;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 88px;
    min-height: 36px;
    padding: 0 12px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 900;
    box-sizing: border-box;
}

.status-badge.success {
    color: #047857;
    background: #e8f5f0;
}

.status-badge.waiting {
    color: #475569;
    background: #e9eef5;
}

.status-badge.failure {
    color: #b42318;
    background: #fef0ed;
}

.payment-details {
    display: grid;
    gap: 0;
    margin: 0;
    background: #ffffff;
    border: 1px solid #e3eaf3;
    border-radius: 8px;
}

.payment-details > div {
    display: grid;
    grid-template-columns: 100px minmax(0, 1fr);
    gap: 16px;
    padding: 14px 16px;
    border-bottom: 1px solid #e3eaf3;
}

.payment-details > div:last-child {
    border-bottom: 0;
}

.payment-details dt {
    color: #64748b;
    font-size: 14px;
    font-weight: 800;
}

.payment-details dd {
    min-width: 0;
    margin: 0;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
    overflow-wrap: anywhere;
}

.payment-details .amount {
    color: #071b3a;
    font-size: 16px;
    font-weight: 900;
}

.waiting-message {
    color: #52637d;
    font-size: 13px;
    line-height: 1.5;
}

.receipt-link,
.history-link {
    color: #071b3a;
    font-size: 15px;
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 4px;
}

.receipt-link {
    justify-self: start;
}

.history-link {
    justify-self: stretch;
    min-height: 46px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #ffffff;
    background: #071b3a;
    border-radius: 8px;
    text-decoration: none;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
}

.history-link:hover {
    background: #0f2f5f;
}

@media (max-width: 640px) {
    .payment-result {
        padding-top: 20px;
    }

    .result-panel {
        padding: 24px 20px;
    }

    .result-header h1 {
        font-size: 32px;
    }

    .payment-details > div {
        grid-template-columns: 84px minmax(0, 1fr);
    }
}
</style>
