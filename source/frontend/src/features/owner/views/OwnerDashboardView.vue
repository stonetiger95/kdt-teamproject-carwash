<template>
    <main class="owner-dashboard">
        <header class="dashboard-heading">
            <div>
                <h1>대시보드</h1>
            </div>
            <time>{{ dashboardPeriodLabel }}</time>
        </header>

        <section
            class="dashboard-section reservation-section"
            aria-labelledby="reservation-title"
        >
            <header class="section-header">
                <div>
                    <h2 id="reservation-title">오늘 예약</h2>
                </div>
                <RouterLink
                    class="section-link"
                    :to="{ name: 'owner-reservations' }"
                >
                    전체 예약 보기
                </RouterLink>
            </header>

            <p v-if="reservationLoading" class="section-state">
                오늘 예약을 불러오는 중입니다.
            </p>
            <p v-else-if="reservationError" class="section-state error-state">
                {{ reservationError }}
            </p>
            <template v-else-if="reservationDashboard">
                <div class="reservation-summary-grid">
                    <div
                        v-for="item in reservationSummaryItems"
                        :key="item.key"
                        class="reservation-summary-card"
                    >
                        <span>{{ item.label }}</span>
                        <strong>{{ formatNumber(item.value) }}</strong>
                        <small>건</small>
                    </div>
                </div>

                <div class="reservation-list-heading">
                    <h3>다음 예약</h3>
                    <span>가까운 순서 최대 5건</span>
                </div>
                <p
                    v-if="upcomingReservations.length === 0"
                    class="section-state compact-state"
                >
                    남은 예약이 없습니다.
                </p>
                <div v-else class="history-table-wrap">
                    <table class="reservation-table">
                        <thead>
                            <tr>
                                <th>예약 시간</th>
                                <th>세차장</th>
                                <th>차량번호</th>
                                <th>코스</th>
                                <th>상태</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr
                                v-for="reservation in upcomingReservations"
                                :key="reservation.reservationId"
                                class="clickable-row"
                                tabindex="0"
                                @click="openReservationList"
                                @keydown.enter="openReservationList"
                            >
                                <td>{{ formatDateTime(reservation.startTime) }}</td>
                                <td>{{ reservation.siteName || `#${reservation.washSiteId}` }}</td>
                                <td class="plate-column">{{ reservation.plateNumber || "-" }}</td>
                                <td>{{ reservation.courseName || "-" }}</td>
                                <td>
                                    <span :class="['status-badge', statusClass(reservation.status)]">
                                        {{ displayReservationStatus(reservation.status) }}
                                    </span>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </template>
        </section>

        <section
            class="dashboard-section settlement-section"
            aria-labelledby="settlement-title"
        >
            <header class="section-header">
                <div>
                    <h2 id="settlement-title">정산 추이</h2>
                </div>

                <div class="section-actions">
                    <div
                        class="period-selector"
                        role="group"
                        aria-label="정산 조회 기간"
                    >
                        <button
                            v-for="days in periodOptions"
                            :key="days"
                            type="button"
                            :class="{ selected: selectedDays === days }"
                            :aria-pressed="selectedDays === days"
                            :disabled="settlementLoading"
                            @click="changePeriod(days)"
                        >
                            {{ days }}일
                        </button>
                    </div>

                    <RouterLink
                        class="section-link"
                        :to="{ name: 'owner-settlement' }"
                    >
                        전체 정산 보기
                    </RouterLink>
                </div>
            </header>

            <p
                v-if="settlementLoading"
                class="section-state"
            >
                정산 데이터를 불러오는 중입니다.
            </p>

            <p
                v-else-if="settlementError"
                class="section-state error-state"
            >
                {{ settlementError }}
            </p>

            <div
                v-else-if="settlementSummary"
                class="chart-layout"
            >
                <div class="chart-summary">
                    <span>결제 완료 금액</span>
                    <strong>{{ formatAmount(displayedTotalPaidAmount) }}</strong>
                    <small>세차 이용 {{ formatNumber(displayedTotalUseCount) }}건</small>
                </div>

                <div class="chart-scroll">
                    <div
                        class="revenue-chart"
                        role="img"
                        :aria-label="`${selectedDays}일간 일별 결제 완료 금액 그래프`"
                        :style="{ '--point-count': chartPoints.length }"
                    >
                        <svg
                            v-if="chartPoints.length > 1"
                            class="chart-lines"
                            viewBox="0 0 1000 100"
                            preserveAspectRatio="none"
                            aria-hidden="true"
                        >
                            <line
                                v-if="selectedDays === 14"
                                class="week-divider"
                                x1="500"
                                y1="0"
                                x2="500"
                                y2="100"
                            />
                            <polyline
                                class="dot-connector"
                                :points="chartPolylinePoints"
                            />
                        </svg>
                        <div
                            v-for="point in chartPoints"
                            :key="point.periodLabel"
                            class="chart-point"
                            :title="`${formatFullDate(point.periodLabel)} ${formatAmount(point.totalPaidAmount)}`"
                        >
                            <span class="point-value">{{ compactAmount(point.totalPaidAmount) }}</span>
                            <span class="dot-track">
                                <span
                                    class="dot-marker"
                                    :style="{ bottom: `${dotPosition(point.totalPaidAmount)}%` }"
                                />
                            </span>
                            <time>{{ formatChartDate(point.periodLabel) }}</time>
                        </div>
                    </div>
                </div>
            </div>
        </section>

        <section
            class="dashboard-section history-section"
            aria-labelledby="history-title"
        >
            <header class="section-header">
                <div>
                    <h2 id="history-title">당일 세차내역</h2>
                </div>

                <RouterLink
                    class="section-link"
                    :to="{ name: 'owner-wash-history' }"
                >
                    전체 세차내역 보기
                </RouterLink>
            </header>

            <p
                v-if="historyLoading"
                class="section-state"
            >
                최근 세차내역을 불러오는 중입니다.
            </p>

            <p
                v-else-if="historyError"
                class="section-state error-state"
            >
                {{ historyError }}
            </p>

            <p
                v-else-if="recentHistories.length === 0"
                class="section-state"
            >
                표시할 세차내역이 없습니다.
            </p>

            <div
                v-else
                class="history-table-wrap"
            >
                <table>
                    <thead>
                        <tr>
                            <th>입차시간</th>
                            <th>세차장</th>
                            <th>차량번호</th>
                            <th>고객구분</th>
                            <th>세차코스</th>
                            <th class="amount-column">결제금액</th>
                            <th>세차상태</th>
                            <th>결제상태</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr
                            v-for="history in recentHistories"
                            :key="history.washHistoryId"
                        >
                            <td>
                                <RouterLink
                                    class="detail-link"
                                    :to="{
                                        name: 'owner-wash-history-detail',
                                        params: { washHistoryId: history.washHistoryId }
                                    }"
                                >
                                    {{ formatDateTime(history.entryTime) }}
                                </RouterLink>
                            </td>
                            <td>{{ history.siteName || `#${history.washSiteId}` }}</td>
                            <td class="plate-column">{{ history.plateNumber || "-" }}</td>
                            <td>{{ displayCustomerType(history.customerType) }}</td>
                            <td>{{ history.courseName || "-" }}</td>
                            <td class="amount-column">{{ formatAmount(history.amount) }}</td>
                            <td>
                                <span :class="['status-badge', statusClass(history.washStatus)]">
                                    {{ displayWashStatus(history.washStatus) }}
                                </span>
                            </td>
                            <td>
                                <span :class="['status-badge', statusClass(history.paymentStatus)]">
                                    {{ displayPaymentStatus(history.paymentStatus) }}
                                </span>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue"
import { useRouter } from "vue-router"
import { useAuthStore } from "@/features/auth/stores/authStore"
import {
    createOwnerTwoSettlementDashboard,
    filterOwnerTwoWashHistories
} from "@/features/washHistory/utils/ownerTwoDummyData"
import * as settlementApi from "@/features/settlement/api/settlementApi"
import * as washHistoryApi from "@/features/washHistory/api/washHistoryApi"
import * as reservationApi from "@/features/reservation/api/reservationApi"

const periodOptions = [7, 14]
const historyDays = 1
const authStore = useAuthStore()
const router = useRouter()
const isOwnerTwo = computed(() => String(authStore.ownerId) === "2")
const selectedDays = ref(7)
const selectedRange = ref(getDateRange(7))
const settlementSummary = ref(null)
const recentHistories = ref([])
const settlementLoading = ref(false)
const historyLoading = ref(false)
const settlementError = ref("")
const historyError = ref("")
const reservationDashboard = ref(null)
const reservationLoading = ref(false)
const reservationError = ref("")

const upcomingReservations = computed(() => {
    return reservationDashboard.value?.upcomingReservations || []
})

const reservationSummaryItems = computed(() => [
    { key: "total", label: "전체", value: reservationDashboard.value?.totalCount || 0 },
    { key: "confirmed", label: "이용 예정", value: reservationDashboard.value?.confirmedCount || 0 },
    { key: "fulfilled", label: "이용 완료", value: reservationDashboard.value?.fulfilledCount || 0 },
    { key: "canceled", label: "취소", value: reservationDashboard.value?.canceledCount || 0 },
    { key: "noShow", label: "노쇼", value: reservationDashboard.value?.noShowCount || 0 }
])

const dashboardPeriodLabel = computed(() => {
    return `${formatFullDate(selectedRange.value.startDate)} - ${formatFullDate(selectedRange.value.endDate)}`
})

const chartPoints = computed(() => {
    if (!settlementSummary.value) {
        return []
    }

    const trendMap = new Map(
        (settlementSummary.value.trends || []).map(point => [point.periodLabel, point])
    )

    return dateKeysBetween(
        selectedRange.value.startDate,
        selectedRange.value.endDate
    ).map(periodLabel => ({
        periodLabel,
        totalPaidAmount: Number(trendMap.get(periodLabel)?.totalPaidAmount || 0),
        totalUseCount: Number(trendMap.get(periodLabel)?.totalUseCount || 0)
    }))
})

const displayedTotalPaidAmount = computed(() => {
    return settlementSummary.value?.totalPaidAmount || 0
})

const displayedTotalUseCount = computed(() => {
    return settlementSummary.value?.totalUseCount || 0
})

const maxPaidAmount = computed(() => {
    return Math.max(1, ...chartPoints.value.map(point => point.totalPaidAmount))
})

const chartPolylinePoints = computed(() => {
    const count = chartPoints.value.length
    if (count === 0) {
        return ""
    }

    return chartPoints.value.map((point, index) => {
        const x = (index + 0.5) * 1000 / count
        const y = 100 - dotPosition(point.totalPaidAmount)
        return `${x},${y}`
    }).join(" ")
})

onMounted(async () => {
    await Promise.all([
        loadSettlement(),
        loadRecentHistories(),
        loadReservationDashboard()
    ])
})

async function loadReservationDashboard() {
    reservationLoading.value = true
    reservationError.value = ""

    try {
        reservationDashboard.value =
            await reservationApi.getOwnerReservationDashboard(
                toDateKey(new Date())
            )
    } catch (error) {
        reservationDashboard.value = null
        reservationError.value = "오늘 예약을 불러오지 못했습니다."
    } finally {
        reservationLoading.value = false
    }
}

function openReservationList() {
    router.push({ name: "owner-reservations" })
}

async function changePeriod(days) {
    if (selectedDays.value === days) {
        return
    }

    selectedDays.value = days
    selectedRange.value = getDateRange(days)
    await loadSettlement()
}

async function loadSettlement() {
    settlementLoading.value = true
    settlementError.value = ""

    try {
        if (isOwnerTwo.value) {
            settlementSummary.value = createOwnerTwoSettlementDashboard({
                periodType: "DAILY",
                startDate: selectedRange.value.startDate,
                endDate: selectedRange.value.endDate
            })
            return
        }

        settlementSummary.value = await settlementApi.getSettlementDashboard(
            "OWNER",
            {
                periodType: "DAILY",
                startDate: selectedRange.value.startDate,
                endDate: selectedRange.value.endDate
            }
        )
    } catch (error) {
        if (isOwnerTwo.value) {
            settlementSummary.value = {
                totalPaidAmount: 0,
                totalUseCount: 0,
                trends: []
            }
        } else {
            settlementSummary.value = null
            settlementError.value = "정산 데이터를 불러오지 못했습니다."
        }
    } finally {
        settlementLoading.value = false
    }
}

async function loadRecentHistories() {
    historyLoading.value = true
    historyError.value = ""
    const historyRange = getDateRange(historyDays)

    try {
        if (isOwnerTwo.value) {
            recentHistories.value = filterOwnerTwoWashHistories({
                startDate: historyRange.startDate,
                endDate: historyRange.endDate
            }).slice(0, 5)
            return
        }

        const response = await washHistoryApi.getWashHistoryPage(
            "OWNER",
            {
                page: 1,
                size: 5,
                startDate: historyRange.startDate,
                endDate: historyRange.endDate
            }
        )
        recentHistories.value = response.content || []
    } catch (error) {
        recentHistories.value = []
        historyError.value = "최근 세차내역을 불러오지 못했습니다."
    } finally {
        historyLoading.value = false
    }
}

function getDateRange(days) {
    const end = new Date()
    const start = new Date(end.getFullYear(), end.getMonth(), end.getDate() - days + 1)

    return {
        startDate: toDateKey(start),
        endDate: toDateKey(end)
    }
}

function dateKeysBetween(startDate, endDate) {
    const dates = []
    const current = parseDateKey(startDate)
    const end = parseDateKey(endDate)

    while (current <= end) {
        dates.push(toDateKey(current))
        current.setDate(current.getDate() + 1)
    }

    return dates
}

function parseDateKey(value) {
    const [year, month, day] = String(value).split("-").map(Number)
    return new Date(year, month - 1, day)
}

function toDateKey(date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")
    return `${year}-${month}-${day}`
}

function dotPosition(value) {
    const amount = Number(value || 0)
    if (amount === 0) {
        return 4
    }

    return Math.max(4, Math.min(94, amount * 90 / maxPaidAmount.value))
}

function compactAmount(value) {
    const amount = Number(value || 0)
    if (amount >= 10000) {
        return `${Math.round(amount / 10000)}만`
    }

    return formatNumber(amount)
}

function formatAmount(value) {
    return `${formatNumber(value)}원`
}

function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(Number(value || 0))
}

function formatChartDate(value) {
    return String(value || "").slice(5).replace("-", ".")
}

function formatFullDate(value) {
    const parsed = parseDateKey(value)
    if (Number.isNaN(parsed.getTime())) {
        return "-"
    }

    return `${parsed.getFullYear()}. ${parsed.getMonth() + 1}. ${parsed.getDate()}.`
}

function formatDateTime(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(5, 16)
}

function displayCustomerType(value) {
    return value === "NON_MEMBER" ? "비회원" : "회원"
}

function displayWashStatus(value) {
    return {
        ENTERED: "입차",
        WAITING: "입차",
        WASHING: "세차중",
        WASH_DONE: "세차완료",
        EXITED: "출차"
    }[value] || value || "-"
}

function displayPaymentStatus(value) {
    return {
        WAITING: "결제대기",
        READY: "결제대기",
        SUCCESS: "결제완료",
        DONE: "결제완료",
        FAILED: "결제실패",
        CANCELED: "결제취소"
    }[value] || value || "-"
}

function displayReservationStatus(value) {
    return {
        CONFIRMED: "이용 예정",
        FULFILLED: "이용 완료",
        CANCELED: "취소",
        NO_SHOW: "노쇼"
    }[value] || value || "-"
}

function statusClass(value) {
    if (["SUCCESS", "DONE", "EXITED", "WASH_DONE", "FULFILLED"].includes(value)) {
        return "status-success"
    }

    if (["FAILED", "CANCELED", "NO_SHOW"].includes(value)) {
        return "status-error"
    }

    if (["WAITING", "READY"].includes(value)) {
        return "status-waiting"
    }

    return "status-active"
}
</script>

<style scoped>
.owner-dashboard {
    display: grid;
    gap: 18px;
    width: min(100%, var(--app-content-width, 1280px));
    min-height: calc(100vh - 70px);
    margin: 0 auto;
    padding: 26px 24px 48px;
    box-sizing: border-box;
    color: #172033;
}

.dashboard-heading,
.section-header,
.section-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.dashboard-heading h1,
.section-header h2,
.dashboard-heading p,
.eyebrow {
    margin: 0;
}

.dashboard-heading h1 {
    color: #0b1f3a;
    font-size: 28px;
    font-weight: 900;
}

.dashboard-heading > div > p:last-child {
    margin-top: 6px;
    color: #64748b;
    font-size: 14px;
}

.dashboard-heading > time {
    color: #52657d;
    font-size: 13px;
    font-weight: 800;
    white-space: nowrap;
}

.eyebrow {
    margin-bottom: 5px;
    color: #52749c;
    font-size: 11px;
    font-weight: 900;
    letter-spacing: 0;
    text-transform: uppercase;
}

.dashboard-section {
    min-width: 0;
    padding: 20px;
    border: 1px solid #dbe4ef;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.section-header {
    margin-bottom: 16px;
}

.section-header h2 {
    color: #0b1f3a;
    font-size: 19px;
    font-weight: 900;
}

.period-selector {
    display: inline-flex;
    overflow: hidden;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
}

.period-selector button {
    min-width: 52px;
    height: 34px;
    padding: 0 12px;
    border: 0;
    border-right: 1px solid #cbd5e1;
    background: #ffffff;
    color: #52657d;
    font-weight: 800;
    cursor: pointer;
}

.period-selector button:last-child {
    border-right: 0;
}

.period-selector button.selected {
    background: #0b1f3a;
    color: #ffffff;
}

.period-selector button:disabled {
    cursor: wait;
    opacity: 0.65;
}

.section-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 34px;
    padding: 0 13px;
    border: 1px solid #9eb1c9;
    border-radius: 6px;
    color: #17365d;
    font-size: 13px;
    font-weight: 900;
    text-decoration: none;
    white-space: nowrap;
}

.section-link:hover {
    border-color: #0b1f3a;
    background: #f3f7fb;
}

.section-state {
    margin: 0;
    padding: 24px;
    border: 1px dashed #cbd5e1;
    border-radius: 6px;
    color: #64748b;
    text-align: center;
}

.error-state {
    border-color: #fecdd3;
    background: #fff1f2;
    color: #be123c;
}

.chart-layout {
    display: grid;
    grid-template-columns: 210px minmax(0, 1fr);
    min-height: 230px;
    border-top: 1px solid #edf2f7;
}

.chart-summary {
    display: flex;
    align-items: flex-start;
    justify-content: center;
    flex-direction: column;
    gap: 7px;
    padding-right: 24px;
    border-right: 1px solid #edf2f7;
}

.chart-summary span,
.chart-summary small {
    color: #64748b;
    font-size: 13px;
}

.chart-summary strong {
    color: #0b1f3a;
    font-size: 27px;
    font-weight: 900;
}

.chart-scroll {
    min-width: 0;
    overflow-x: auto;
    padding: 16px 0 0 22px;
}

.revenue-chart {
    position: relative;
    display: grid;
    grid-template-columns: repeat(var(--point-count), minmax(34px, 1fr));
    gap: 7px;
    min-width: max(100%, calc(var(--point-count) * 44px));
    height: 205px;
}

.chart-lines {
    position: absolute;
    z-index: 1;
    top: 26px;
    right: 0;
    bottom: 26px;
    left: 0;
    width: 100%;
    height: calc(100% - 52px);
    overflow: visible;
    pointer-events: none;
}

.dot-connector {
    fill: none;
    stroke: #168579;
    stroke-width: 2.5;
    vector-effect: non-scaling-stroke;
}

.week-divider {
    stroke: #8ca0b6;
    stroke-width: 1;
    stroke-dasharray: 5 5;
    vector-effect: non-scaling-stroke;
}

.chart-point {
    position: relative;
    z-index: 2;
    display: grid;
    grid-template-rows: 22px 1fr 22px;
    gap: 4px;
    min-width: 0;
}

.point-value,
.chart-point time {
    overflow: hidden;
    color: #52657d;
    font-size: 11px;
    font-weight: 800;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.dot-track {
    position: relative;
    border-bottom: 1px solid #cbd5e1;
    background-image: linear-gradient(to bottom, #e8eef4 1px, transparent 1px);
    background-size: 100% 25%;
}

.dot-marker {
    position: absolute;
    left: 50%;
    display: block;
    width: 10px;
    height: 10px;
    border: 2px solid #ffffff;
    border-radius: 50%;
    background: #168579;
    box-shadow: 0 1px 4px rgb(22 133 121 / 35%);
    transform: translate(-50%, 50%);
    transition: bottom 180ms ease;
}

.reservation-summary-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 10px;
}

.reservation-summary-card {
    display: flex;
    align-items: baseline;
    gap: 5px;
    min-width: 0;
    padding: 16px;
    border: 1px solid #e4ebf5;
    border-radius: 6px;
    background: #f8fafc;
}

.reservation-summary-card span {
    margin-right: auto;
    color: #64748b;
    font-size: 13px;
    font-weight: 800;
}

.reservation-summary-card strong {
    color: #0b1f3a;
    font-size: 24px;
    font-weight: 900;
}

.reservation-summary-card small {
    color: #64748b;
    font-size: 12px;
}

.reservation-list-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin: 20px 0 10px;
}

.reservation-list-heading h3 {
    margin: 0;
    color: #27364b;
    font-size: 15px;
}

.reservation-list-heading span {
    color: #7b8ba1;
    font-size: 12px;
}

.compact-state {
    padding: 16px;
}

.reservation-table {
    min-width: 720px;
}

.clickable-row {
    cursor: pointer;
}

.clickable-row:hover,
.clickable-row:focus-visible {
    background: #f3f7fb;
    outline: none;
}

.history-table-wrap {
    max-width: 100%;
    overflow-x: auto;
    border: 1px solid #e4ebf5;
    border-radius: 6px;
}

table {
    width: 100%;
    min-width: 1060px;
    border-collapse: collapse;
}

th,
td {
    padding: 10px 11px;
    border-bottom: 1px solid #edf2f7;
    color: #344054;
    font-size: 13px;
    line-height: 1.35;
    text-align: left;
    white-space: nowrap;
}

th {
    background: #f6f8fb;
    color: #52657d;
    font-size: 12px;
    font-weight: 900;
}

tbody tr:last-child td {
    border-bottom: 0;
}

.detail-link {
    color: #174d82;
    font-weight: 900;
    text-decoration: none;
}

.detail-link:hover {
    text-decoration: underline;
}

.plate-column {
    color: #0b1f3a;
    font-weight: 900;
}

.amount-column {
    text-align: right;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 58px;
    min-height: 25px;
    padding: 2px 7px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 900;
}

.status-success {
    background: #e8f5f0;
    color: #047857;
}

.status-active {
    background: #eaf2ff;
    color: #1d4ed8;
}

.status-waiting {
    background: #fff7e6;
    color: #b45309;
}

.status-error {
    background: #fff1f2;
    color: #be123c;
}

@media (max-width: 760px) {
    .owner-dashboard {
        padding: 20px 14px 36px;
    }

    .reservation-summary-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .dashboard-heading,
    .section-header {
        align-items: flex-start;
        flex-direction: column;
    }

    .section-actions {
        width: 100%;
        flex-wrap: wrap;
    }

    .chart-layout {
        grid-template-columns: 1fr;
    }

    .chart-summary {
        padding: 18px 0;
        border-right: 0;
        border-bottom: 1px solid #edf2f7;
    }

    .chart-scroll {
        padding-left: 0;
    }
}
</style>
