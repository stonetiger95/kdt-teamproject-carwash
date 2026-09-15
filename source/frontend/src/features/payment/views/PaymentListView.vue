<template>
    <main class="payment-list">
        <header class="payment-list-header">
            <div>
                <p class="eyebrow">PAYMENT HISTORY</p>
                <h1>결제 내역</h1>
                <p class="page-copy">
                    충전과 세차 결제 내역을 확인할 수 있습니다.
                </p>
            </div>

            <RouterLink
                v-if="checkoutRouteName && canStartPayment"
                class="checkout-link"
                :to="{ name: checkoutRouteName }"
            >
                충전하기
            </RouterLink>
        </header>

        <p v-if="paymentStore.loading" class="status-text">
            결제 내역을 불러오는 중입니다.
        </p>
        <p v-if="paymentStore.error" class="status-text error">
            {{ paymentStore.error }}
        </p>

        <section class="table-card">
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>번호</th>
                            <th>구분</th>
                            <th>상품명</th>
                            <th>고객</th>
                            <th>금액</th>
                            <th>결제수단</th>
                            <th>상태</th>
                            <th>승인일</th>
                            <th>관리</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="payment in paymentStore.payments"
                            :key="`${payment.purpose || 'PAYMENT'}-${payment.paymentId}`"
                        >
                            <td>{{ payment.orderId || payment.paymentId }}</td>
                            <td>{{ displayPurpose(payment) }}</td>
                            <td>{{ payment.orderName || "-" }}</td>
                            <td>{{ payment.customerName || payment.customerId || "-" }}</td>
                            <td>{{ formatActivityAmount(payment) }}</td>
                            <td>{{ displayMethod(payment.method) }}</td>
                            <td>
                                <span class="status-badge" :data-status="payment.status">
                                    {{ displayStatus(payment.status) }}
                                </span>
                            </td>
                            <td>{{ formatDate(payment.approvedAt) }}</td>
                            <td>
                                <RouterLink
                                    v-if="canRequestRefund(payment)"
                                    class="refund-link"
                                    :to="refundInquiryRoute(payment)"
                                >
                                    환불 문의
                                </RouterLink>
                                <button
                                    v-else-if="canCancel(payment)"
                                    class="cancel-button"
                                    type="button"
                                    :disabled="cancelingPaymentId === refundTargetId(payment)"
                                    @click="cancelPayment(payment)"
                                >
                                    {{ cancelingPaymentId === refundTargetId(payment) ? "처리 중" : "직접 환불" }}
                                </button>
                                <span v-else>-</span>
                            </td>
                        </tr>

                        <tr v-if="!paymentStore.loading && paymentStore.payments.length === 0">
                            <td class="empty-cell" colspan="9">
                                결제 내역이 없습니다.
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue"
import { useRoute } from "vue-router"
import { useAuthStore } from "@/features/auth/stores/authStore"
import * as benefitApi from "@/features/benefit/api/benefitApi"
import { usePaymentStore } from "../stores/paymentStore"

const props = defineProps({
    viewerRole: {
        type: String,
        default: "CUSTOMER"
    },
    checkoutRouteName: {
        type: String,
        default: "customer-payment-checkout"
    }
})

const paymentStore = usePaymentStore()
const authStore = useAuthStore()
const route = useRoute()
const cancelingPaymentId = ref(null)
let pollTimer = null

const canStartPayment = computed(() =>
    props.viewerRole === "CUSTOMER"
    && authStore.canUseFeature("payment")
)

onMounted(() => {
    const searchCondition = getRouteSearchCondition()
    paymentStore.fetchPaymentList({ searchCondition })
    pollTimer = window.setInterval(() => {
        paymentStore.fetchPaymentList({
            silent: true,
            searchCondition
        })
    }, 3000)
})

onUnmounted(() => {
    if (pollTimer) {
        window.clearInterval(pollTimer)
    }
})

function canRequestRefund(payment) {
    return props.viewerRole === "CUSTOMER"
        && payment.status === "SUCCESS"
        && Boolean(payment.refundablePaymentId)
}

function getRouteSearchCondition() {
    const purpose = String(route.query.purpose || "").toUpperCase()

    if (["CHARGE", "WASH_PAYMENT"].includes(purpose)) {
        return { purpose }
    }

    return {}
}

function canCancel(payment) {
    return props.viewerRole !== "CUSTOMER"
        && payment.status === "SUCCESS"
        && Boolean(refundTargetId(payment))
}

function refundTargetId(payment) {
    return payment.refundablePaymentId || payment.paymentId
}

function refundInquiryRoute(payment) {
    return {
        name: "customer-inquiry-create",
        query: {
            type: "REFUND",
            paymentHistoryId: payment.refundablePaymentId,
            orderName: payment.orderName || "결제",
            amount: payment.amount
        }
    }
}

async function cancelPayment(payment) {
    if (cancelingPaymentId.value !== null) {
        return
    }

    let refundPolicy = null
    try {
        refundPolicy = await benefitApi.getPaymentBenefitRefundPolicy(
            props.viewerRole,
            refundTargetId(payment)
        )
    } catch (error) {
        refundPolicy = null
    }

    let amountText
    if (refundPolicy && refundPolicy.partialRefundAllowed === false) {
        window.alert("마일리지를 사용한 결제는 부분 환불할 수 없어 전액 환불로 진행합니다.")
        amountText = String(refundPolicy.payableAmount ?? payment.amount ?? 0)
    } else {
        amountText = window.prompt(
            "환불 금액을 입력하세요.",
            String(refundPolicy?.payableAmount ?? payment.amount ?? "")
        )
    }

    if (amountText === null) {
        return
    }

    const refundAmount = Number(amountText)
    if (!Number.isInteger(refundAmount) || refundAmount < 0) {
        window.alert("환불 금액을 올바르게 입력하세요.")
        return
    }

    const cancelReason = window.prompt("환불 사유를 입력하세요.", "관리자 환불")
    if (!cancelReason?.trim()) {
        return
    }

    cancelingPaymentId.value = refundTargetId(payment)

    try {
        const success = await paymentStore.cancelPayment(refundTargetId(payment), {
            refundAmount,
            cancelReason: cancelReason.trim()
        })

        if (success) {
            await paymentStore.fetchPaymentList()
        }
    } finally {
        cancelingPaymentId.value = null
    }
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString()}원`
}

function formatActivityAmount(payment) {
    const amount = formatAmount(payment.amount)
    return payment.purpose === "WASH_PAYMENT" ? `-${amount}` : amount
}

function displayPurpose(payment) {
    if (payment.purpose === "CHARGE") {
        return "충전"
    }

    if (payment.purpose === "WASH_PAYMENT" || payment.washHistoryId) {
        return "세차 결제"
    }

    return "결제"
}

function displayMethod(value) {
    const labels = {
        CARD: "카드",
        PREPAID: "선불 지갑",
        PAYAPP: "PayApp"
    }
    return labels[value] || value || "-"
}

function displayStatus(value) {
    const labels = {
        WAITING: "결제 대기",
        SUCCESS: "결제 완료",
        CANCELED: "취소 완료",
        FAILED: "결제 실패"
    }
    return labels[value] || value || "-"
}

function formatDate(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(0, 16)
}
</script>

<style scoped>
.payment-list {
    display: grid;
    gap: 20px;
    padding: 32px 0 8px;
    color: #172033;
}

.payment-list-header {
    position: relative;
    padding: 28px 34px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

h1 {
    margin: 0;
    color: #071b3a;
    font-size: 38px;
    font-weight: 900;
}

.page-copy {
    margin: 10px 0 0;
    color: #52637d;
}

.checkout-link {
    position: absolute;
    right: 34px;
    bottom: 28px;
}

.checkout-link,
.refund-link,
.cancel-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 36px;
    padding: 0 14px;
    border: 1px solid #071b3a;
    border-radius: 8px;
    background: #071b3a;
    color: #ffffff;
    font: inherit;
    font-size: 14px;
    font-weight: 800;
    text-decoration: none;
    cursor: pointer;
}

.refund-link {
    border-color: #166534;
    background: #166534;
}

.cancel-button {
    border-color: #b42318;
    background: #b42318;
}

.table-card {
    overflow: hidden;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    min-width: 940px;
    border-collapse: collapse;
}

th,
td {
    padding: 15px 14px;
    border-bottom: 1px solid #d8e2ef;
    color: #334766;
    font-size: 14px;
    text-align: left;
    white-space: nowrap;
}

th {
    color: #071b3a;
    background: #f5f8fc;
    font-weight: 900;
}

.status-badge {
    display: inline-flex;
    min-height: 28px;
    align-items: center;
    padding: 0 10px;
    border-radius: 999px;
    background: #e8eef8;
    color: #1c3d70;
    font-size: 12px;
    font-weight: 900;
}

.status-badge[data-status="SUCCESS"] {
    background: #dcfce7;
    color: #166534;
}

.status-badge[data-status="CANCELED"],
.status-badge[data-status="FAILED"] {
    background: #fee2e2;
    color: #991b1b;
}

.empty-cell {
    padding: 36px 14px;
    text-align: center;
}

.status-text {
    margin: 0;
    padding: 12px 16px;
    border-radius: 8px;
    background: #e8eef8;
    color: #1c3d70;
    font-weight: 800;
}

.status-text.error {
    background: #fff1f1;
    color: #b42318;
}

@media (max-width: 720px) {
    .payment-list-header {
        padding: 24px 20px;
    }

    .checkout-link {
        position: static;
        width: 100%;
        margin-top: 20px;
    }

    h1 {
        font-size: 30px;
    }
}
</style>
