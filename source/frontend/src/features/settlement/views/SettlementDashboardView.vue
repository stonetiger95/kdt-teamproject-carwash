<template>
    <main class="settlement-page">
        <header class="page-header">
            <div>
                <p class="eyebrow">{{ roleLabel }} 정산</p>
                <h1>정산 대시보드</h1>
                <p>세차장과 기간을 선택해 달력, 요약, 추이와 상세내역을 확인합니다.</p>
            </div>
        </header>

        <section class="query-panel">
            <div class="filter-grid">
                <label v-if="isAdmin">
                    사업주
                    <select
                        v-model="filters.ownerId"
                        @change="handleOwnerChange"
                    >
                        <option value="">전체 사업주</option>
                        <option
                            v-for="owner in siteStore.owners"
                            :key="owner.ownerId"
                            :value="String(owner.ownerId)"
                        >
                            {{ displayOwnerOption(owner) }}
                        </option>
                    </select>
                </label>

                <label>
                    세차장
                    <select v-model="filters.washSiteId">
                        <option value="">{{ siteSelectAllLabel }}</option>
                        <option
                            v-for="site in siteStore.washSites"
                            :key="site.washSiteId"
                            :value="String(site.washSiteId)"
                        >
                            {{ displaySiteOption(site) }}
                        </option>
                    </select>
                </label>

                <label>
                    집계 단위
                    <select v-model="filters.periodType">
                        <option value="DAILY">일간</option>
                        <option value="WEEKLY">주간</option>
                        <option value="MONTHLY">월간</option>
                    </select>
                </label>

                <label>
                    시작일
                    <input
                        v-model="filters.startDate"
                        type="date"
                    >
                </label>

                <label>
                    종료일
                    <input
                        v-model="filters.endDate"
                        type="date"
                    >
                </label>
            </div>

            <div class="query-actions">
                <div
                    class="view-selector"
                    role="group"
                    aria-label="정산 보기 방식"
                >
                    <template
                        v-for="view in viewOptions"
                        :key="view.value"
                    >
                        <button
                            v-if="!view.adminOnly || isAdmin"
                            type="button"
                            :disabled="isLoading"
                            :class="{ selected: filters.viewMode === view.value }"
                            :aria-pressed="filters.viewMode === view.value"
                            @click="selectView(view.value)"
                        >
                            {{ view.label }}
                        </button>
                    </template>
                </div>
            </div>
        </section>

        <p
            v-if="isLoading"
            class="state-text"
        >
            정산 데이터를 불러오는 중입니다.
        </p>

        <p
            v-if="combinedError"
            class="error-text"
        >
            {{ combinedError }}
        </p>

        <section
            v-if="dashboard && appliedFilters"
            class="result-area"
        >
            <header class="result-header">
                <div class="result-title">
                    <strong>{{ activeViewLabel }}</strong>
                    <label class="result-site-filter">
                        <span>세차장</span>
                        <select
                            :value="appliedFilters.washSiteId"
                            :disabled="isLoading"
                            @change="handleResultSiteChange"
                        >
                            <option value="">{{ appliedFilters.siteAllLabel }}</option>
                            <option
                                v-for="site in appliedFilters.siteOptions"
                                :key="site.washSiteId"
                                :value="String(site.washSiteId)"
                            >
                                {{ displaySiteOption(site) }}
                            </option>
                        </select>
                    </label>
                </div>
                <time>{{ dashboard.startDate }} ~ {{ dashboard.endDate }}</time>
            </header>

            <SettlementCalendar
                v-if="activeView === 'CALENDAR'"
                :histories="filteredHistories"
                :start-date="appliedFilters.startDate"
                @select-date="handleCalendarDate"
                @select-week="handleCalendarWeek"
            />

            <SettlementSummaryBlocks
                v-else-if="activeView === 'SUMMARY'"
                :dashboard="dashboard"
                @drilldown="handleDrilldown"
            />

            <SettlementAggregateList
                v-else-if="activeView === 'LIST'"
                :trends="dashboard.trends || []"
                @select-period="handleTrendPeriod"
            />

            <SettlementTrendChart
                v-else-if="activeView === 'CHART'"
                :trends="dashboard.trends || []"
                @select-period="handleTrendPeriod"
            />

            <SettlementSiteComparison
                v-else-if="activeView === 'SITE_COMPARE'"
                :comparisons="store.siteComparisons"
                :loading="store.siteComparisonLoading"
            />

            <SettlementDetailTable
                v-else
                :histories="detailHistories"
                :filter-label="drilldown.label"
                :loading="historyLoading"
                @select-history="goWashHistoryDetail"
            />
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { useSettlementStore } from "../stores/settlementStore"
import { useWashSiteStore } from "@/features/washSite/stores/washSiteStore"
import { useAuthStore } from "@/features/auth/stores/authStore"
import { filterOwnerTwoWashHistories } from "@/features/washHistory/utils/ownerTwoDummyData"
import * as washHistoryApi from "@/features/washHistory/api/washHistoryApi"
import SettlementAggregateList from "../components/SettlementAggregateList.vue"
import SettlementCalendar from "../components/SettlementCalendar.vue"
import SettlementDetailTable from "../components/SettlementDetailTable.vue"
import SettlementSummaryBlocks from "../components/SettlementSummaryBlocks.vue"
import SettlementTrendChart from "../components/SettlementTrendChart.vue"
import SettlementSiteComparison from "../components/SettlementSiteComparison.vue"

const props = defineProps({
    viewerRole: {
        type: String,
        required: true
    }
})

const router = useRouter()
const store = useSettlementStore()
const siteStore = useWashSiteStore()
const authStore = useAuthStore()
const isOwnerTwo = props.viewerRole === "OWNER" && String(authStore.ownerId) === "2"

const viewOptions = [
    { value: "CALENDAR", label: "달력" },
    { value: "SUMMARY", label: "블럭" },
    { value: "LIST", label: "집계목록" },
    { value: "CHART", label: "그래프" },
    { value: "DETAIL", label: "상세내역" },
    { value: "SITE_COMPARE", label: "세차장 비교", adminOnly: true }
]

const monthRange = isOwnerTwo
    ? { startDate: "2026-06-01", endDate: "2026-07-24" }
    : getCurrentMonthRange()
const filters = reactive({
    ownerId: "",
    washSiteId: "",
    periodType: "DAILY",
    startDate: monthRange.startDate,
    endDate: monthRange.endDate,
    viewMode: "CALENDAR"
})

const activeView = ref("CALENDAR")
const appliedFilters = ref(null)
const washHistories = ref([])
const historyLoading = ref(false)
const historyError = ref("")
const drilldown = ref(defaultDrilldown())

const dashboard = computed(() => store.dashboard)
const isAdmin = computed(() => props.viewerRole === "ADMIN")
const isOwner = computed(() => props.viewerRole === "OWNER")
const isLoading = computed(() => store.loading || store.siteComparisonLoading || siteStore.loading || historyLoading.value)
const combinedError = computed(() => store.error || store.siteComparisonError || siteStore.error || historyError.value)

const roleLabel = computed(() => isAdmin.value ? "관리자" : "사업주")

const activeViewLabel = computed(() => {
    return viewOptions.find(view => view.value === activeView.value)?.label || "정산"
})

const siteSelectAllLabel = computed(() => {
    if (isAdmin.value && filters.ownerId) {
        return "선택 사업주의 전체 세차장"
    }

    if (isAdmin.value) {
        return "전체 세차장"
    }

    return "내 전체 세차장"
})

const filteredHistories = computed(() => {
    if (!appliedFilters.value) {
        return []
    }

    const applied = appliedFilters.value
    const ownerSiteIds = new Set(applied.ownerSiteIds)

    return washHistories.value.filter(history => {
        const entryDate = String(history.entryTime || "").slice(0, 10)
        if (!entryDate || entryDate < applied.startDate || entryDate > applied.endDate) {
            return false
        }

        if (applied.washSiteId && String(history.washSiteId) !== applied.washSiteId) {
            return false
        }

        if (isAdmin.value
                && applied.ownerId
                && !applied.washSiteId
                && !ownerSiteIds.has(Number(history.washSiteId))) {
            return false
        }

        return true
    })
})

const detailHistories = computed(() => {
    const selected = drilldown.value
    let histories = [...filteredHistories.value]

    if (selected.startDate) {
        histories = histories.filter(history => {
            const entryDate = String(history.entryTime || "").slice(0, 10)
            return entryDate >= selected.startDate && entryDate <= selected.endDate
        })
    }

    if (selected.type === "MEMBER") {
        histories = histories.filter(history => history.customerType === "MEMBER")
    }

    if (selected.type === "NON_MEMBER") {
        histories = histories.filter(history => history.customerType === "NON_MEMBER")
    }

    if (selected.type === "PAID") {
        histories = histories.filter(history => ["SUCCESS", "DONE"].includes(history.paymentStatus))
    }

    if (selected.type === "WAITING") {
        histories = histories.filter(history => history.paymentStatus === "WAITING")
    }

    return histories.sort((left, right) => {
        return String(right.entryTime || "").localeCompare(String(left.entryTime || ""))
    })
})

onMounted(async () => {
    if (isAdmin.value) {
        await siteStore.fetchOwners()
        await siteStore.fetchWashSites("ADMIN")
    }

    if (isOwner.value) {
        await siteStore.fetchWashSites("OWNER")
    }

    await loadDashboard()
})

async function handleOwnerChange() {
    filters.washSiteId = ""
    await siteStore.fetchWashSites("ADMIN", filters.ownerId)
}

async function selectView(viewMode) {
    filters.viewMode = viewMode
    await loadDashboard()
}

async function handleResultSiteChange(event) {
    const applied = appliedFilters.value
    if (!applied) {
        return
    }

    filters.ownerId = applied.ownerId
    filters.washSiteId = event.target.value
    filters.viewMode = activeView.value

    if (isAdmin.value) {
        await siteStore.fetchWashSites("ADMIN", filters.ownerId)
    }

    await loadDashboard()
}

async function loadDashboard() {
    if (!filters.startDate || !filters.endDate) {
        historyError.value = "조회 시작일과 종료일을 선택해 주세요."
        return
    }

    if (filters.startDate > filters.endDate) {
        historyError.value = "종료일은 시작일보다 빠를 수 없습니다."
        return
    }

    historyLoading.value = true
    historyError.value = ""
    appliedFilters.value = null

    const params = {
        periodType: filters.periodType,
        startDate: filters.startDate,
        endDate: filters.endDate
    }

    if (isAdmin.value && filters.ownerId) {
        params.ownerId = filters.ownerId
    }

    if (filters.washSiteId) {
        params.washSiteId = filters.washSiteId
    }

    try {
        const historyRequest = isOwnerTwo
            ? Promise.resolve(filterOwnerTwoWashHistories(params))
            : washHistoryApi.getWashHistoryList(props.viewerRole)
        const requests = [
            store.fetchDashboard(props.viewerRole, params),
            historyRequest
        ]

        if (isAdmin.value) {
            requests.push(store.fetchSiteComparisons(params))
        }

        const [, histories] = await Promise.all(requests)

        if (!store.dashboard) {
            return
        }

        washHistories.value = Array.isArray(histories) ? histories : []
        appliedFilters.value = {
            ownerId: String(filters.ownerId || ""),
            washSiteId: String(filters.washSiteId || ""),
            periodType: filters.periodType,
            startDate: filters.startDate,
            endDate: filters.endDate,
            ownerSiteIds: siteStore.washSites.map(site => Number(site.washSiteId)),
            siteOptions: siteStore.washSites.map(site => ({ ...site })),
            siteAllLabel: siteSelectAllLabel.value
        }
        activeView.value = filters.viewMode
        drilldown.value = defaultDrilldown()
    } catch (error) {
        historyError.value = "세차 상세내역을 불러오지 못했습니다."
    } finally {
        historyLoading.value = false
    }
}

function handleDrilldown(payload) {
    openDetail({
        type: payload.type,
        label: payload.label
    })
}

function handleCalendarDate(date) {
    openDetail({
        type: "DATE",
        label: `${date} 이용내역`,
        startDate: date,
        endDate: date
    })
}

function handleCalendarWeek(week) {
    openDetail({
        type: "WEEK",
        label: `${week.startDate} ~ ${week.endDate} 이용내역`,
        startDate: week.startDate,
        endDate: week.endDate
    })
}

function handleTrendPeriod(trend) {
    const range = trendPeriodRange(trend.periodLabel)
    openDetail({
        type: "PERIOD",
        label: `${trend.periodLabel} 이용내역`,
        ...range
    })
}

function openDetail(selection) {
    drilldown.value = {
        ...defaultDrilldown(),
        ...selection
    }
    filters.viewMode = "DETAIL"
    activeView.value = "DETAIL"
}

function goWashHistoryDetail(washHistoryId) {
    const routeName = isAdmin.value
        ? "admin-wash-history-detail"
        : "owner-wash-history-detail"

    router.push({
        name: routeName,
        params: { washHistoryId }
    })
}

function trendPeriodRange(periodLabel) {
    const applied = appliedFilters.value
    if (!applied) {
        return { startDate: "", endDate: "" }
    }

    let startDate = periodLabel
    let endDate = periodLabel

    if (/^\d{4}-\d{2}$/.test(periodLabel || "")) {
        const [year, month] = periodLabel.split("-").map(Number)
        startDate = toDateKey(new Date(year, month - 1, 1))
        endDate = toDateKey(new Date(year, month, 0))
    } else if (applied.periodType === "WEEKLY") {
        endDate = addDays(startDate, 6)
    }

    return {
        startDate: startDate < applied.startDate ? applied.startDate : startDate,
        endDate: endDate > applied.endDate ? applied.endDate : endDate
    }
}

function defaultDrilldown() {
    return {
        type: "ALL",
        label: "조회 기간 전체 이용내역",
        startDate: "",
        endDate: ""
    }
}

function getCurrentMonthRange() {
    const now = new Date()
    return {
        startDate: toDateKey(new Date(now.getFullYear(), now.getMonth(), 1)),
        endDate: toDateKey(new Date(now.getFullYear(), now.getMonth() + 1, 0))
    }
}

function addDays(value, days) {
    const [year, month, day] = value.split("-").map(Number)
    const date = new Date(year, month - 1, day)
    date.setDate(date.getDate() + days)
    return toDateKey(date)
}

function toDateKey(date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")
    return `${year}-${month}-${day}`
}

function displayOwnerOption(owner) {
    const count = Number(owner.washSiteCount || 0)
    return `${owner.ownerName || "이름 없음"} / ${owner.businessNumber || "-"} / 세차장 ${count}곳`
}

function displaySiteOption(site) {
    const siteName = site.siteName || `세차장 #${site.washSiteId}`
    if (isAdmin.value && site.ownerName) {
        return `${siteName} / ${site.ownerName}`
    }

    return siteName
}
</script>

<style scoped>
.settlement-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header {
    padding-bottom: 4px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 7px;
    color: #5b728f;
    font-size: 12px;
    font-weight: 800;
    text-transform: uppercase;
}

h1 {
    margin: 0;
    color: #0f172a;
    font-size: 30px;
    font-weight: 900;
}

.page-header p:last-child {
    margin: 8px 0 0;
    color: #64748b;
    font-size: 14px;
}

.query-panel {
    display: grid;
    gap: 16px;
    padding: 18px;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.filter-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(140px, 1fr));
    gap: 12px;
}

label {
    display: grid;
    gap: 7px;
    color: #344054;
    font-size: 13px;
    font-weight: 800;
}

input,
select {
    width: 100%;
    height: 40px;
    box-sizing: border-box;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    padding: 0 11px;
    background: #ffffff;
    color: #172033;
    font-size: 14px;
}

input:focus,
select:focus {
    border-color: #52749c;
    outline: none;
    box-shadow: 0 0 0 3px rgba(82, 116, 156, 0.14);
}

.query-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding-top: 14px;
    border-top: 1px solid #edf2f7;
}

.view-selector {
    display: inline-grid;
    grid-template-columns: repeat(6, minmax(80px, 1fr));
    overflow: hidden;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
}

.view-selector button {
    min-height: 38px;
    padding: 0 14px;
    border: 0;
    border-right: 1px solid #dfe7f2;
    background: #ffffff;
    color: #475467;
    font-size: 13px;
    font-weight: 800;
    cursor: pointer;
}

.view-selector button:last-child {
    border-right: 0;
}

.view-selector button:hover,
.view-selector button:focus-visible {
    background: #eef5fb;
    outline: none;
}

.view-selector button.selected {
    background: #183b5c;
    color: #ffffff;
}

.primary-button {
    min-width: 104px;
    height: 40px;
    border: 0;
    border-radius: 6px;
    background: #0b1f3a;
    color: #ffffff;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.primary-button:hover:not(:disabled) {
    background: #163b68;
}

.primary-button:disabled {
    opacity: 0.6;
    cursor: wait;
}

.state-text,
.error-text {
    margin: 0;
    padding: 14px 16px;
    border-radius: 8px;
    background: #f8fafc;
    color: #475467;
    font-size: 14px;
}

.error-text {
    background: #fff1f2;
    color: #be123c;
}

.result-area {
    display: grid;
    gap: 18px;
}

.result-header {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 14px;
    padding-bottom: 12px;
    border-bottom: 1px solid #dfe7f2;
}

.result-title {
    display: grid;
    gap: 8px;
}

.result-header strong {
    color: #0f172a;
    font-size: 18px;
}

.result-header time {
    color: #64748b;
    font-size: 13px;
}

.result-site-filter {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #64748b;
    font-size: 13px;
}

.result-site-filter select {
    width: auto;
    min-width: 220px;
    height: 34px;
    padding-right: 32px;
    font-size: 13px;
}

@media (max-width: 1160px) {
    .filter-grid {
        grid-template-columns: repeat(3, minmax(150px, 1fr));
    }
}

@media (max-width: 760px) {
    .filter-grid {
        grid-template-columns: 1fr;
    }

    .query-actions,
    .result-header {
        align-items: stretch;
        flex-direction: column;
    }

    .result-site-filter {
        align-items: stretch;
        flex-direction: column;
    }

    .result-site-filter select {
        width: 100%;
    }

    .view-selector {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        width: 100%;
    }

    .view-selector button {
        border-bottom: 1px solid #dfe7f2;
    }

    .primary-button {
        width: 100%;
    }
}
</style>
