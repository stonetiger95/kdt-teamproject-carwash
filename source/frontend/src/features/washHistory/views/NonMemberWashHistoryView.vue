<template>
    <main class="non-member-wash-history-page">
        <section class="content">
            <header class="page-header">
                <div>
                    <p class="eyebrow">
                        비회원 조회
                    </p>

                    <h1>
                        비회원 세차 내역
                    </h1>
                </div>

                <button
                    type="button"
                    @click="goHome"
                >
                    홈으로
                </button>
            </header>

            <section class="search-panel">
                <NonMemberPlateLookup
                    :initial-plate-last4="initialPlateLast4"
                    :initial-plate-number="initialPlateNumber"
                    @selected="loadWashHistories"
                    @cleared="clearWashHistories"
                />
            </section>

            <p
                v-if="loading"
                class="notice-message"
            >
                전체 세차 내역을 불러오고 있습니다.
            </p>

            <p
                v-else-if="errorMessage"
                class="notice-message error"
            >
                {{ errorMessage }}
            </p>

            <p
                v-else-if="searched && washHistories.length === 0"
                class="notice-message"
            >
                선택한 차량번호의 세차 내역이 없습니다.
            </p>

            <section
                v-if="washHistories.length > 0"
                class="result-section"
            >
                <div class="result-heading">
                    <div class="result-summary">
                        <strong>{{ washHistories.length }}건</strong>

                        <button
                            type="button"
                            class="payment-history-button"
                            @click="goLatestPaymentHistory"
                        >
                            결제내역
                        </button>
                    </div>
                </div>

                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th scope="col">번호</th>
                                <th scope="col">차량번호</th>
                                <th scope="col">세차장</th>
                                <th scope="col">세차코스</th>
                                <th scope="col">결제금액</th>
                                <th scope="col">입차시간</th>
                                <th scope="col">출차시간</th>
                                <th scope="col">세차상태</th>
                                <th scope="col">결제상태</th>
                                <th scope="col" class="payment-column-heading">
                                    <span class="sr-only">결제 작업</span>
                                </th>
                            </tr>
                        </thead>

                        <tbody>
                            <tr
                                v-for="history in washHistories"
                                :key="history.washHistoryId"
                            >
                                <td class="history-number">
                                    {{ history.washHistoryId }}
                                </td>
                                <td>{{ displayValue(history.plateNumber) }}</td>
                                <td>{{ displayValue(history.siteName) }}</td>
                                <td>{{ displayValue(history.courseName) }}</td>
                                <td>{{ formatAmount(history.amount) }}</td>
                                <td>
                                    <span class="date-time">
                                        <span>{{ formatDate(history.entryTime) }}</span>
                                        <span>{{ formatTime(history.entryTime) }}</span>
                                    </span>
                                </td>
                                <td>
                                    <span class="date-time">
                                        <span>{{ formatDate(history.exitTime) }}</span>
                                        <span>{{ formatTime(history.exitTime) }}</span>
                                    </span>
                                </td>
                                <td>
                                    <span
                                        class="status-badge"
                                        :class="washStatusClass(history.washStatus)"
                                    >
                                        {{ displayWashStatus(history.washStatus) }}
                                    </span>
                                </td>
                                <td>
                                    <span
                                        class="status-badge"
                                        :class="paymentStatusClass(history.paymentStatus)"
                                    >
                                        {{ displayPaymentStatus(history.paymentStatus) }}
                                    </span>
                                </td>
                                <td class="payment-cell">
                                    <button
                                        v-if="isPaymentRequired(history.paymentStatus)"
                                        type="button"
                                        class="payment-button"
                                        @click="goPayment(history.washHistoryId)"
                                    >
                                        결제하기
                                    </button>
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
import { ref } from "vue"
import {
    useRoute,
    useRouter
} from "vue-router"

import * as washHistoryApi
    from "../api/washHistoryApi"
import NonMemberPlateLookup
    from "../components/NonMemberPlateLookup.vue"


const router = useRouter()
const route = useRoute()

const washHistories = ref([])
const loading = ref(false)
const searched = ref(false)
const errorMessage = ref("")

let historyRequestId = 0

const initialPlateLast4 =
    typeof route.query.plateLast4 === "string"
        ? route.query.plateLast4
        : ""

const initialPlateNumber =
    typeof route.query.plateNumber === "string"
        ? route.query.plateNumber
        : ""


async function loadWashHistories({
    plateNumber
}) {
    const requestId = ++historyRequestId

    washHistories.value = []
    searched.value = false
    errorMessage.value = ""

    if (!plateNumber) {
        errorMessage.value =
            "조회할 차량번호를 선택해주세요."
        return
    }

    loading.value = true

    try {
        const result = await washHistoryApi
            .getNonMemberWashHistoryByPlate(
                plateNumber
            )

        if (requestId !== historyRequestId) {
            return
        }

        washHistories.value =
            Array.isArray(result)
                ? result
                : []
        searched.value = true

    } catch (error) {
        if (requestId !== historyRequestId) {
            return
        }

        console.error(
            "비회원 전체 세차 내역 조회 실패:",
            error
        )

        errorMessage.value =
            "비회원 세차 내역을 불러오지 못했습니다."

    } finally {
        if (requestId === historyRequestId) {
            loading.value = false
        }
    }
}


function clearWashHistories() {
    historyRequestId += 1
    washHistories.value = []
    loading.value = false
    searched.value = false
    errorMessage.value = ""
}

function goPayment(washHistoryId) {
    if (
        washHistoryId === null
        || washHistoryId === undefined
    ) {
        errorMessage.value =
            "세차 이용 번호가 없습니다."
        return
    }

    router.push({
        name: "non-member-payment-checkout",
        params: {
            washHistoryId: String(washHistoryId)
        }
    })
}


function goPaymentHistory(washHistoryId) {
    if (
        washHistoryId === null
        || washHistoryId === undefined
    ) {
        errorMessage.value =
            "세차 이용 번호가 없습니다."
        return
    }

    router.push({
        name: "non-member-payment-list",
        params: {
            washHistoryId: String(washHistoryId)
        }
    })
}


function goLatestPaymentHistory() {
    goPaymentHistory(
        washHistories.value[0]?.washHistoryId
    )
}


function goHome() {
    router.push({
        name: "home"
    })
}


function isPaymentRequired(status) {
    const normalized = String(status ?? "")
        .toUpperCase()

    return [
        "READY",
        "WAITING"
    ].includes(normalized)
}


function displayValue(value) {
    return value ?? "-"
}


function displayWashStatus(status) {
    const labels = {
        ENTERED: "입차",
        WAITING: "입차",
        WASHING: "세차중",
        WASH_DONE: "세차완료",
        EXITED: "출차"
    }

    return labels[status] ?? status ?? "-"
}


function displayPaymentStatus(status) {
    const labels = {
        READY: "결제대기",
        WAITING: "결제대기",
        SUCCESS: "결제완료",
        DONE: "결제완료",
        FAILED: "결제실패",
        CANCELED: "결제취소"
    }

    return labels[status] ?? status ?? "-"
}


function washStatusClass(status) {
    if (
        status === "EXITED"
        || status === "WASH_DONE"
    ) {
        return "success"
    }

    if (status === "WASHING") {
        return "active"
    }

    return "neutral"
}


function paymentStatusClass(status) {
    if (
        status === "SUCCESS"
        || status === "DONE"
    ) {
        return "success"
    }

    if (
        status === "FAILED"
        || status === "CANCELED"
    ) {
        return "danger"
    }

    return "waiting"
}


function formatAmount(amount) {
    if (
        amount === null
        || amount === undefined
    ) {
        return "-"
    }

    return `${Number(amount)
        .toLocaleString("ko-KR")}원`
}


function parseDateTime(value) {
    if (!value) {
        return null
    }

    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
        return null
    }

    return date
}


function formatDate(value) {
    const date = parseDateTime(value)

    if (!date) {
        return value || "-"
    }

    return date.toLocaleDateString("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    })
}


function formatTime(value) {
    const date = parseDateTime(value)

    if (!date) {
        return ""
    }

    return date.toLocaleTimeString("ko-KR", {
        hour: "2-digit",
        minute: "2-digit"
    })
}
</script>

<style scoped>
.non-member-wash-history-page {
    min-height: 100vh;
    padding: 48px 20px 72px;
    color: #061a3a;
    background: #f4f7fb;
    box-sizing: border-box;
}

.content {
    width: 100%;
    max-width: var(--app-content-width, 1280px);
    margin: 0 auto;
}

.page-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 20px;
    position: relative;
    margin-bottom: 24px;
    text-align: center;
}

.page-header > button {
    position: absolute;
    right: 0;
}

.eyebrow {
    margin: 0 0 8px;
    color: #52647f;
    font-size: 14px;
    font-weight: 800;
}

h1 {
    margin: 0;
    color: #061a3a;
    font-size: 32px;
    font-weight: 800;
    line-height: 1.25;
    letter-spacing: 0;
}

.page-header button,
.payment-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 40px;
    padding: 0 18px;
    color: #ffffff;
    font-size: 14px;
    font-weight: 800;
    background: #061a3a;
    border: 1px solid #061a3a;
    border-radius: 8px;
    box-shadow: 0 8px 16px rgba(6, 26, 58, 0.16);
    cursor: pointer;
}

.page-header button:hover,
.payment-button:hover {
    background: #0f2f5f;
    border-color: #0f2f5f;
}

.search-panel {
    padding: 28px 32px;
    background: #ffffff;
    border: 1px solid #dce4ef;
    border-radius: 14px;
    box-shadow: 0 14px 32px rgba(15, 47, 95, 0.08);
}

.search-panel p {
    margin: 0 0 18px;
    color: #43536f;
    font-size: 15px;
    line-height: 1.6;
}

.lookup-combobox {
    position: relative;
}

.lookup-combobox input {
    width: 100%;
    height: 42px;
    padding: 0 14px;
    color: #061a3a;
    font-size: 15px;
    background: #ffffff;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    outline: none;
    box-sizing: border-box;
}

.lookup-combobox input:focus {
    border-color: #0f2f5f;
    box-shadow: 0 0 0 3px rgba(15, 47, 95, 0.14);
}

.plate-option-list {
    position: absolute;
    z-index: 10;
    top: calc(100% + 6px);
    right: 0;
    left: 0;
    max-height: 220px;
    padding: 6px;
    margin: 0;
    overflow-y: auto;
    list-style: none;
    background: #ffffff;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    box-shadow: 0 14px 28px rgba(15, 47, 95, 0.16);
}

.plate-option-list button {
    width: 100%;
    min-height: 42px;
    padding: 0 12px;
    color: #061a3a;
    font-size: 15px;
    font-weight: 700;
    text-align: left;
    background: #ffffff;
    border: 0;
    border-radius: 6px;
    cursor: pointer;
}

.plate-option-list button:hover,
.plate-option-list button:focus {
    background: #edf3fb;
    outline: none;
}

.search-panel .lookup-status {
    margin: 14px 0 0;
    color: #52647f;
    font-size: 14px;
}

.error-message,
.empty-message {
    margin: 18px 0 0;
    padding: 14px 16px;
    font-size: 14px;
    font-weight: 800;
}

h1,
h2 {
    margin: 0;
    color: #061a3a;
    font-weight: 800;
    letter-spacing: 0;
}

h1 {
    font-size: 32px;
    line-height: 1.25;
}

h2 {
    font-size: 28px;
}

.search-panel {
    max-width: 920px;
    padding: 28px 32px;
    margin: 0 auto;
    background: #ffffff;
    border: 1px solid #dce4ef;
    border-radius: 8px;
    box-shadow: 0 14px 32px rgba(15, 47, 95, 0.08);
}

.notice-message {
    max-width: 920px;
    padding: 14px 16px;
    margin: 18px auto 0;
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
    min-width: 1080px;
    border-collapse: collapse;
    table-layout: fixed;
}

th,
td {
    padding: 18px 10px;
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
    background: #f7f9fc;
    white-space: nowrap;
}

tbody tr:last-child td {
    border-bottom: 0;
}

tbody tr:hover {
    background: #fbfcfe;
}

th:nth-child(1),
td:nth-child(1) {
    width: 60px;
}

th:nth-child(2),
td:nth-child(2) {
    width: 92px;
}

th:nth-child(3),
td:nth-child(3) {
    width: 180px;
    white-space: nowrap;
}

th:nth-child(4),
td:nth-child(4) {
    width: 130px;
}

th:nth-child(5),
td:nth-child(5) {
    width: 84px;
}

th:nth-child(6),
td:nth-child(6),
th:nth-child(7),
td:nth-child(7) {
    width: 126px;
}

.date-time {
    display: inline-flex;
    flex-direction: column;
    gap: 2px;
}

.date-time > span {
    white-space: nowrap;
}

th:nth-child(8),
td:nth-child(8),
th:nth-child(9),
td:nth-child(9) {
    width: 90px;
}

.payment-column-heading,
.payment-cell {
    width: 102px;
}

.history-number {
    color: #061a3a;
    font-weight: 800;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 68px;
    min-height: 34px;
    padding: 0 10px;
    font-size: 13px;
    font-weight: 800;
    border-radius: 8px;
    box-sizing: border-box;
    white-space: nowrap;
}

.status-badge.success {
    color: #047857;
    background: #e8f5f0;
}

.status-badge.active {
    color: #1d4ed8;
    background: #eaf1ff;
}

.status-badge.waiting,
.status-badge.neutral {
    color: #475569;
    background: #eef2f7;
}

.status-badge.danger {
    color: #b42318;
    background: #fef0ed;
}

.payment-cell {
    text-align: right;
}

.payment-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 82px;
    height: 38px;
    padding: 0 8px;
    color: #ffffff;
    font-size: 13px;
    font-weight: 800;
    line-height: 1;
    white-space: nowrap;
    background: #0f766e;
    border: 1px solid #0f766e;
    border-radius: 8px;
    box-sizing: border-box;
    cursor: pointer;
}

.payment-history-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 8px 14px;
    color: #061a3a;
    font-size: 14px;
    font-weight: 800;
    white-space: nowrap;
    background: #ffffff;
    border: 1px solid #b8c7da;
    border-radius: 8px;
    box-sizing: border-box;
    cursor: pointer;
}

.payment-history-button:hover {
    background: #edf3fb;
    border-color: #7f96b2;
}

.sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}

@media (max-width: 640px) {
    .non-member-wash-history-page {
        padding: 34px 16px 52px;
    }

    .page-header {
        flex-direction: column;
        align-items: stretch;
    }

    .page-header > button {
        position: static;
    }

    .search-panel {
        padding: 22px 18px;
    }

    h1 {
        font-size: 28px;
    }

    .result-heading {
        align-items: center;
    }

    .result-heading > strong {
        font-size: 20px;
    }
}
</style>
