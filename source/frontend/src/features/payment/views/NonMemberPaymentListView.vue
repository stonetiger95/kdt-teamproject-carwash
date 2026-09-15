
<template>
    <main class="payment-list">
        <section class="content">
            <header class="payment-list-header">
                <div>
                    <p class="eyebrow">비회원 결제</p>
                    <h1>결제 내역</h1>
                </div>

                <div class="header-actions">
                    <button
                        type="button"
                        class="home-button"
                        @click="goHome"
                    >
                        홈으로
                    </button>
                </div>
            </header>

            <p
                v-if="returnStatusMessage"
                class="return-status"
                :class="returnStatusClass"
                aria-live="polite"
            >
                {{ returnStatusMessage }}
            </p>

            <p
                v-if="loading"
                class="notice-message"
            >
                불러오는 중입니다.
            </p>

            <p
                v-if="errorMessage"
                class="notice-message error"
            >
                {{ errorMessage }}
            </p>

            <section
                v-if="!loading && !errorMessage"
                class="result-section"
            >
                <div class="result-heading">
                    <div class="result-summary">
                        <strong>{{ payments.length }}건</strong>

                        <button
                            type="button"
                            class="secondary-button"
                            @click="goWashHistory"
                        >
                            세차 내역
                        </button>
                    </div>
                </div>

                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>번호</th>
                                <th>상품명</th>
                                <th>구매자</th>
                                <th>금액</th>
                                <th>결제수단</th>
                                <th>상태</th>
                                <th>승인일</th>
                            </tr>
                        </thead>

                        <tbody>
                            <tr
                                v-for="payment in payments"
                                :key="payment.paymentId"
                            >
                                <td class="payment-number">
                                    {{ payment.paymentId }}
                                </td>
                                <td>{{ payment.orderName || "-" }}</td>
                                <td>{{ payment.customerName || "비회원" }}</td>
                                <td>{{ formatAmount(payment.amount) }}</td>
                                <td>{{ payment.method || "-" }}</td>
                                <td>
                                    <span
                                        class="status-badge"
                                        :class="paymentStatusClass(payment.status)"
                                    >
                                        {{ statusLabel(payment.status) }}
                                    </span>
                                </td>
                                <td>{{ formatDate(payment.approvedAt) }}</td>
                            </tr>

                            <tr v-if="payments.length === 0">
                                <td
                                    colspan="7"
                                    class="empty-cell"
                                >
                                    결제 내역이 없습니다.
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>
        </section>
    </main>
</template>

<script setup>
import {
    computed,
    onMounted,
    onUnmounted,
    ref
} from "vue"
import {
    useRoute,
    useRouter
} from "vue-router"
import * as paymentApi from "../api/paymentApi"

const route = useRoute()
const router = useRouter()

const payments = ref([])
const plateNumber = ref(
    typeof route.query.plateNumber === "string"
        ? route.query.plateNumber
        : ""
)
const loading = ref(false)
const errorMessage = ref("")
const pollTimedOut = ref(false)

let pollTimer = null
let pollRequestActive = false
let pollAttempts = 0

const MAX_POLL_ATTEMPTS = 20

const washHistoryId = Number(
    route.params.washHistoryId
)
const returnedPaymentId = Number(
    route.query.paymentId
)
const hasReturnedPaymentId =
    Number.isInteger(returnedPaymentId)
    && returnedPaymentId > 0

const returnedPayment = computed(() =>
    hasReturnedPaymentId
        ? payments.value.find(
            payment =>
                Number(payment.paymentId)
                === returnedPaymentId
        )
        : null
)

const returnStatusMessage = computed(() => {
    if (!hasReturnedPaymentId) {
        return ""
    }

    const status = String(
        returnedPayment.value?.status || ""
    ).toUpperCase()

    if (status === "SUCCESS" || status === "DONE") {
        return "결제가 완료되었습니다."
    }

    if (status === "CANCELED") {
        return "결제가 취소되었습니다."
    }

    if (status === "FAILED") {
        return "결제 요청에 실패했습니다. 다시 시도해주세요."
    }

    if (pollTimedOut.value) {
        return "결제 결과 확인이 지연되고 있습니다. 잠시 후 다시 확인해주세요."
    }

    return "PayApp 결제 결과를 확인하고 있습니다."
})

const returnStatusClass = computed(() => {
    const status = String(
        returnedPayment.value?.status || ""
    ).toUpperCase()

    if (status === "SUCCESS" || status === "DONE") {
        return "success"
    }

    if (status === "CANCELED" || status === "FAILED") {
        return "failure"
    }

    return "pending"
})

onMounted(async () => {
    await loadPayments()

    if (shouldPollReturnedPayment()) {
        startReturnPolling()
    }
})

onUnmounted(() => {
    if (pollTimer) {
        window.clearInterval(pollTimer)
    }
})

async function loadPayments(silent = false) {
    if (!washHistoryId) {
        errorMessage.value =
            "세차 이용 번호가 올바르지 않습니다."
        return
    }

    if (!silent) {
        loading.value = true
    }

    errorMessage.value = ""

    try {
        const result =
            await paymentApi
                .getNonMemberPaymentList(
                    washHistoryId
                )

        payments.value =
            Array.isArray(result)
                ? result
                : []

        const responsePlateNumber =
            payments.value.find(
                payment => payment.plateNumber
            )?.plateNumber

        if (responsePlateNumber) {
            plateNumber.value = responsePlateNumber
        }

        if (!shouldPollReturnedPayment()) {
            stopReturnPolling()
        }
    } catch (error) {
        console.error(
            "비회원 결제 내역 조회 실패:",
            error
        )

        errorMessage.value =
            "비회원 결제 내역을 불러오지 못했습니다."
    } finally {
        if (!silent) {
            loading.value = false
        }
    }
}

function startReturnPolling() {
    stopReturnPolling()

    pollTimer = window.setInterval(async () => {
        if (pollRequestActive) {
            return
        }

        if (pollAttempts >= MAX_POLL_ATTEMPTS) {
            pollTimedOut.value = true
            stopReturnPolling()
            return
        }

        pollRequestActive = true
        pollAttempts += 1

        try {
            await loadPayments(true)
        } finally {
            pollRequestActive = false
        }
    }, 3000)
}

function stopReturnPolling() {
    if (pollTimer) {
        window.clearInterval(pollTimer)
        pollTimer = null
    }
}

function shouldPollReturnedPayment() {
    if (!hasReturnedPaymentId) {
        return false
    }

    const status = String(
        returnedPayment.value?.status || ""
    ).toUpperCase()

    return ![
        "SUCCESS",
        "DONE",
        "CANCELED",
        "FAILED"
    ].includes(status)
}

function goHome() {
    router.push({ name: "home" })
}

function goWashHistory() {
    const normalizedPlateNumber =
        String(plateNumber.value || "")

    const plateLast4 =
        normalizedPlateNumber
            .match(/(\d{4})$/)?.[1]
        || ""

    if (!plateLast4) {
        errorMessage.value =
            "세차 내역을 조회할 차량번호가 없습니다."
        return
    }

    router.push({
        name: "non-member-wash-history",
        query: {
            plateLast4,
            plateNumber: normalizedPlateNumber
        }
    })
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString()}원`
}

function formatDate(value) {
    if (!value) {
        return "-"
    }

    return String(value)
        .replace("T", " ")
        .slice(0, 16)
}

function statusLabel(status) {
    if (status === "WAITING") {
        return "결제 대기"
    }

    if (
        status === "SUCCESS"
        || status === "DONE"
    ) {
        return "결제 완료"
    }

    if (status === "CANCELED") {
        return "결제 취소"
    }

    if (status === "FAILED") {
        return "결제 실패"
    }

    return status || "-"
}

function paymentStatusClass(status) {
    if (
        status === "SUCCESS"
        || status === "DONE"
    ) {
        return "success"
    }

    if (
        status === "CANCELED"
        || status === "FAILED"
    ) {
        return "danger"
    }

    return "waiting"
}
</script>

<style scoped>
.payment-list {
    min-height: 100vh;
    padding: 48px 20px 72px;
    color: #061a3a;
    background: #f4f7fb;
    box-sizing: border-box;
}

.content {
    width: 100%;
    max-width: var(--app-content-width, 1080px);
    margin: 0 auto;
}

.payment-list-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 20px;
    position: relative;
    margin-bottom: 40px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #52647f;
    font-size: 14px;
    font-weight: 800;
}

.payment-list-header h1 {
    margin: 0;
    color: #061a3a;
    font-size: 32px;
    font-weight: 800;
    line-height: 1.25;
    letter-spacing: 0;
}

.header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    position: absolute;
    right: 0;
}

.home-button,
.secondary-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 8px 14px;
    font-size: 14px;
    font-weight: 800;
    line-height: 1;
    white-space: nowrap;
    border-radius: 8px;
    box-sizing: border-box;
    cursor: pointer;
}

.home-button {
    padding-inline: 18px;
    color: #ffffff;
    background: #061a3a;
    border: 1px solid #061a3a;
    box-shadow: 0 8px 16px rgba(6, 26, 58, 0.16);
}

.home-button:hover {
    background: #0f2f5f;
    border-color: #0f2f5f;
}

.secondary-button {
    color: #061a3a;
    background: #ffffff;
    border: 1px solid #b8c7da;
}

.secondary-button:hover {
    background: #edf3fb;
    border-color: #7f96b2;
}

.result-section {
    margin-top: 40px;
}

.result-heading {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    margin-bottom: 18px;
}

.result-summary {
    display: flex;
    align-items: center;
    gap: 12px;
}

.result-summary > strong {
    color: #061a3a;
    font-size: 24px;
}

.table-wrap {
    width: 100%;
    overflow-x: auto;
    background: #ffffff;
    border: 1px solid #dce4ef;
    border-radius: 8px;
}

table {
    width: 100%;
    min-width: 920px;
    border-collapse: collapse;
    table-layout: fixed;
}

th,
td {
    padding: 18px 12px;
    color: #30415e;
    font-size: 13px;
    text-align: left;
    vertical-align: middle;
    border-bottom: 1px solid #e3eaf3;
    box-sizing: border-box;
}

th {
    height: 64px;
    color: #263750;
    font-weight: 800;
    white-space: nowrap;
    background: #f7f9fc;
}

tbody tr:last-child td {
    border-bottom: 0;
}

tbody tr:hover {
    background: #fbfcfe;
}

th:nth-child(1),
td:nth-child(1) {
    width: 72px;
}

th:nth-child(2),
td:nth-child(2) {
    width: 210px;
}

th:nth-child(3),
td:nth-child(3) {
    width: 100px;
}

th:nth-child(4),
td:nth-child(4) {
    width: 100px;
}

th:nth-child(5),
td:nth-child(5) {
    width: 130px;
}

th:nth-child(6),
td:nth-child(6) {
    width: 120px;
}

th:nth-child(7),
td:nth-child(7) {
    width: 150px;
}

.payment-number {
    color: #061a3a;
    font-weight: 800;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 72px;
    min-height: 34px;
    padding: 0 10px;
    font-size: 13px;
    font-weight: 800;
    white-space: nowrap;
    border-radius: 8px;
    box-sizing: border-box;
}

.status-badge.success {
    color: #047857;
    background: #e8f5f0;
}

.status-badge.waiting {
    color: #475569;
    background: #eef2f7;
}

.status-badge.danger {
    color: #b42318;
    background: #fef0ed;
}

.empty-cell {
    height: 180px;
    color: #52647f;
    text-align: center;
}

.notice-message {
    padding: 14px 16px;
    margin: 18px 0 0;
    color: #43536f;
    font-size: 14px;
    line-height: 1.5;
    background: #ffffff;
    border: 1px solid #dce4ef;
    border-radius: 8px;
    box-sizing: border-box;
}

.notice-message.error {
    color: #b91c1c;
    background: #fef2f2;
    border-color: #fecaca;
}

.return-status {
    margin: 0 0 18px;
    padding: 12px 14px;
    border: 1px solid #bfd0e8;
    border-radius: 8px;
    background: #f4f8fd;
    color: #24456d;
    font-weight: 600;
}

.return-status.success {
    border-color: #b8dfca;
    background: #eef9f3;
    color: #17663b;
}

.return-status.failure {
    border-color: #efc0bc;
    background: #fff3f2;
    color: #a52a22;
}

@media (max-width: 720px) {
    .payment-list {
        padding: 32px 16px 48px;
    }

    .payment-list-header {
        flex-direction: column;
        margin-bottom: 28px;
    }

    .header-actions {
        position: static;
    }

    .result-heading {
        align-items: center;
    }
}
</style>
