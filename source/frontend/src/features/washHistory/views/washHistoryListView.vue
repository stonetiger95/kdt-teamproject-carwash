<template>
    <main class="wash-history-page">
        <header class="page-header">
            <div>
                <p class="eyebrow"></p>
                <h1>{{ pageTitle }}</h1>
                <p>{{ pageDescription }}</p>
            </div>

            <button
                v-if="!isGuestMode"
                class="primary-button"
                type="button"
                @click="loadWashHistories(1)"
            >
                조회
            </button>
        </header>

        <section
            v-if="canSelectWashSite"
            class="filter-panel site-filter"
        >
            <label v-if="isAdmin">
                사업주
                <select
                    v-model="selectedOwnerId"
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
                <select v-model="selectedWashSiteId">
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
        </section>

        <section
            v-if="isGuestMode"
            class="filter-panel guest-panel"
        >
            <label>
                차량번호
                <input
                    v-model.trim="guestSearch.plateNumber"
                    type="text"
                    placeholder="예: 12가3456"
                >
            </label>

            <label>
                전화번호
                <input
                    v-model.trim="guestSearch.phone"
                    type="text"
                    placeholder="예: 01012345678"
                >
            </label>

            <button
                class="primary-button"
                type="button"
                @click="searchGuestWashHistory"
            >
                비회원 조회
            </button>

            <p
                v-if="guestSearchMessage"
                class="notice-text"
            >
                {{ guestSearchMessage }}
            </p>
        </section>

        <section
            v-if="showFilters"
            class="filter-panel detail-filter"
        >
            <label>
                차량번호
                <input
                    v-model.trim="filters.plateNumber"
                    type="text"
                    placeholder="차량번호 검색"
                >
            </label>

            <label>
                고객구분
                <select v-model="filters.customerType">
                    <option value="">전체</option>
                    <option value="MEMBER">회원</option>
                    <option value="NON_MEMBER">비회원</option>
                </select>
            </label>

            <label v-if="usesPeriodFilter">
                시작일
                <input
                    v-model="filters.startDate"
                    type="date"
                >
            </label>

            <label v-if="usesPeriodFilter">
                종료일
                <input
                    v-model="filters.endDate"
                    type="date"
                >
            </label>

            <label>
                세차상태
                <select v-model="filters.washStatus">
                    <option value="">전체</option>
                    <option value="ENTERED">입차</option>
                    <option value="WASHING">세차중</option>
                    <option value="WASH_DONE">세차완료</option>
                    <option value="EXITED">출차</option>
                </select>
            </label>

            <label>
                결제상태
                <select v-model="filters.paymentStatus">
                    <option value="">전체</option>
                    <option value="WAITING">결제대기</option>
                    <option value="SUCCESS">결제완료</option>
                    <option value="DONE">결제완료</option>
                    <option value="FAILED">결제실패</option>
                    <option value="CANCELED">결제취소</option>
                </select>
            </label>
        </section>

        <p
            v-if="store.loading || siteStore.loading"
            class="state-text"
        >
            세차 이용 내역을 불러오는 중입니다.
        </p>

        <p
            v-if="periodError || store.error || siteStore.error"
            class="error-text"
        >
            {{ periodError || store.error || siteStore.error }}
        </p>

        <section
            v-if="!isGuestMode && !searched"
            class="empty-state"
        >
            <strong>조회 버튼을 눌러 세차 이용 내역을 확인하세요.</strong>
            <span>사업주와 세차장을 선택하면 해당 범위의 내역만 볼 수 있습니다.</span>
        </section>

        <section
            v-if="!isGuestMode && searched && filteredWashHistories.length > 0"
            class="result-section"
        >
            <div class="result-header">
                <div>
                    <p class="eyebrow">Result</p>
                    <h2>조회 결과</h2>
                </div>
                <strong>{{ resultTotalCount }}건</strong>
            </div>

            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th class="col-id">번호</th>
                            <th v-if="canSelectWashSite" class="col-site">세차장</th>
                            <th class="col-plate">차량번호</th>
                            <th class="col-customer">고객구분</th>
                            <th class="col-course">세차코스</th>
                            <th class="col-amount">결제금액</th>
                            <th class="col-time">입차시간</th>
                            <th class="col-time">출차시간</th>
                            <th class="col-status">세차상태</th>
                            <th class="col-status">결제상태</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="(history, index) in filteredWashHistories"
                            :key="history.washHistoryId"
                            class="history-row"
                            tabindex="0"
                            :aria-label="`${index + 1}번 세차 내역 상세보기`"
                            @click="goToDetail(history.washHistoryId)"
                            @keydown.enter="goToDetail(history.washHistoryId)"
                            @keydown.space.prevent="goToDetail(history.washHistoryId)"
                        >
                            <td><strong>{{ index + 1 }}</strong></td>
                            <td v-if="canSelectWashSite">{{ displayHistorySite(history) }}</td>
                            <td>{{ displayValue(history.plateNumber) }}</td>
                            <td>{{ displayCustomerType(history.customerType) }}</td>
                            <td>{{ displayValue(history.courseName) }}</td>
                            <td>{{ formatAmount(history.amount) }}</td>
                            <td>{{ formatDateTime(history.entryTime) }}</td>
                            <td>{{ formatDateTime(history.exitTime) }}</td>
                            <td>
                                <span :class="['status-badge', getWashStatusClass(history.washStatus)]">
                                    {{ displayWashStatus(history.washStatus) }}
                                </span>
                            </td>
                            <td class="col-status" data-label="결제상태">
                                <span :class="['status-badge', getPaymentStatusClass(history.paymentStatus)]">
                                    {{ displayPaymentStatus(history.paymentStatus) }}
                                </span>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div
                v-if="isPagedMode"
                class="pagination"
            >
                <button
                    type="button"
                    :disabled="!store.pageInfo.hasPrevious"
                    @click="movePage(store.pageInfo.page - 1)"
                >
                    이전
                </button>

                <span>{{ store.pageInfo.page }} / {{ store.pageInfo.totalPages || 1 }}</span>

                <button
                    type="button"
                    :disabled="!store.pageInfo.hasNext"
                    @click="movePage(store.pageInfo.page + 1)"
                >
                    다음
                </button>
            </div>
        </section>

        <section
            v-else-if="!isGuestMode && searched && !store.loading"
            class="empty-state"
        >
            <strong>표시할 세차 이용 내역이 없습니다.</strong>
            <span>조회 조건이나 세차장 선택 조건을 확인해 주세요.</span>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { useWashHistoryStore } from "../stores/washHistoryStore"
import { useWashSiteStore } from "@/features/washSite/stores/washSiteStore"
import { useAuthStore } from "@/features/auth/stores/authStore"

const props = defineProps({
    viewerRole: {
        type: String,
        required: true
    },
    detailRouteName: {
        type: String,
        required: true
    }
})

const store = useWashHistoryStore()
const siteStore = useWashSiteStore()
const authStore = useAuthStore()
const router = useRouter()
const searched = ref(false)
const guestSearchMessage = ref("")
const selectedOwnerId = ref("")
const selectedWashSiteId = ref("")
const periodError = ref("")
const isOwnerTwo = props.viewerRole === "OWNER" && String(authStore.ownerId) === "2"
const defaultPeriod = isOwnerTwo
    ? { startDate: "2026-06-01", endDate: "2026-07-24" }
    : getCurrentMonthRange()

const filters = reactive({
    plateNumber: "",
    customerType: "",
    washStatus: "",
    paymentStatus: "",
    startDate: defaultPeriod.startDate,
    endDate: defaultPeriod.endDate
})

const guestSearch = reactive({
    plateNumber: "",
    phone: ""
})

const isGuestMode = computed(() => props.viewerRole === "GUEST")
const isAdmin = computed(() => props.viewerRole === "ADMIN")
const isOwner = computed(() => props.viewerRole === "OWNER")
const isCustomer = computed(() => props.viewerRole === "CUSTOMER")
const isPagedMode = computed(() => isAdmin.value || isOwner.value)
const canSelectWashSite = computed(() => isAdmin.value || isOwner.value)
const usesPeriodFilter = computed(() => isCustomer.value || isOwner.value)

const pageTitle = computed(() => {
    if (isGuestMode.value) {
        return "비회원 세차 이용 내역"
    }

    return "세차 이용 내역"
})

const pageDescription = computed(() => {
    if (isAdmin.value) {
        return "사업주와 세차장 기준으로 세차 이용 내역과 결제 상태를 확인합니다."
    }

    if (isOwner.value) {
        return "내 세차장 이용 내역과 결제 상태를 확인합니다."
    }

    if (isGuestMode.value) {
        return "차량번호와 전화번호로 비회원 세차 이용 내역을 조회합니다."
    }

    return "내 세차 이용 내역을 확인합니다."
})

const siteSelectAllLabel = computed(() => {
    if (isAdmin.value && selectedOwnerId.value) {
        return "선택 사업주의 전체 세차장"
    }

    if (isAdmin.value) {
        return "전체 세차장"
    }

    return "내 전체 세차장"
})

const showFilters = computed(() => {
    return !isGuestMode.value
})

const filteredWashHistories = computed(() => {
    if (isPagedMode.value) {
        return store.washHistories
    }

    return store.washHistories.filter(history => {
        const plateNumber = history.plateNumber || ""

        if (filters.plateNumber && !plateNumber.includes(filters.plateNumber)) {
            return false
        }

        if (filters.customerType && history.customerType !== filters.customerType) {
            return false
        }

        if (filters.washStatus && history.washStatus !== filters.washStatus) {
            return false
        }

        if (filters.paymentStatus && history.paymentStatus !== filters.paymentStatus) {
            return false
        }

        return true
    })
})

const resultTotalCount = computed(() => {
    if (isPagedMode.value) {
        return store.pageInfo.totalElements
    }

    return filteredWashHistories.value.length
})

onMounted(async () => {
    if (isAdmin.value) {
        await siteStore.fetchOwners()
        await siteStore.fetchWashSites("ADMIN")
    }

    if (isOwner.value) {
        await siteStore.fetchWashSites("OWNER")

        if (isOwnerTwo) {
            await loadWashHistories()
        }
    }

    if (props.viewerRole === "CUSTOMER") {
        await loadWashHistories()
    }
})

async function handleOwnerChange() {
    selectedWashSiteId.value = ""
    await siteStore.fetchWashSites("ADMIN", selectedOwnerId.value)
}

function buildSearchParams(page = 1) {
    return {
        page,
        size: store.pageInfo.size,
        ownerId: isAdmin.value && selectedOwnerId.value
            ? Number(selectedOwnerId.value)
            : undefined,
        washSiteId: selectedWashSiteId.value
            ? Number(selectedWashSiteId.value)
            : undefined,
        plateNumber: filters.plateNumber || undefined,
        customerType: filters.customerType || undefined,
        washStatus: filters.washStatus || undefined,
        paymentStatus: filters.paymentStatus || undefined,
        startDate: usesPeriodFilter.value ? filters.startDate : undefined,
        endDate: usesPeriodFilter.value ? filters.endDate : undefined
    }
}

async function loadWashHistories(page = 1) {
    if (!validatePeriod()) {
        return
    }

    searched.value = true
    guestSearchMessage.value = ""

    if (isPagedMode.value) {
        await store.fetchWashHistoryList(
            props.viewerRole,
            buildSearchParams(page)
        )
        return
    }

    await store.fetchWashHistoryList(
        props.viewerRole,
        buildSearchParams(page)
    )
}

function validatePeriod() {
    periodError.value = ""

    if (!usesPeriodFilter.value) {
        return true
    }

    if (!filters.startDate || !filters.endDate) {
        periodError.value = "조회 시작일과 종료일을 선택해 주세요."
        return false
    }

    if (filters.startDate > filters.endDate) {
        periodError.value = "종료일은 시작일보다 빠를 수 없습니다."
        return false
    }

    return true
}

function getCurrentMonthRange() {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), 1)
    const end = new Date(now.getFullYear(), now.getMonth() + 1, 0)

    return {
        startDate: toLocalDateKey(start),
        endDate: toLocalDateKey(end)
    }
}

function toLocalDateKey(date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")
    return `${year}-${month}-${day}`
}

async function movePage(page) {
    await loadWashHistories(page)
}

function searchGuestWashHistory() {
    if (!guestSearch.plateNumber || !guestSearch.phone) {
        guestSearchMessage.value = "비회원 조회는 차량번호와 전화번호를 모두 입력해야 합니다."
        return
    }

    guestSearchMessage.value = "비회원 조회 API가 연결되면 입력한 정보로 세차 이용 내역을 조회합니다."
}

function displayOwnerOption(owner) {
    return `${owner.ownerName || "이름 없음"} / ${owner.businessNumber || "-"}`
}

function displaySiteOption(site) {
    const siteName = site.siteName || `세차장 #${site.washSiteId}`
    if (isAdmin.value && site.ownerName) {
        return `${siteName} / ${site.ownerName}`
    }

    return siteName
}

function displayHistorySite(history) {
    const site = siteStore.washSites.find(item => Number(item.washSiteId) === Number(history.washSiteId))
    if (site) {
        return site.siteName || `#${history.washSiteId}`
    }

    return history.washSiteId ? `#${history.washSiteId}` : "-"
}

function displayValue(value) {
    return value || "-"
}

function goToDetail(washHistoryId) {
    router.push({
        name: props.detailRouteName,
        params: { washHistoryId }
    })
}

function formatDateTime(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(0, 19)
}

function formatAmount(value) {
    if (value === null || value === undefined) {
        return "-"
    }

    return `${Number(value).toLocaleString()}원`
}

function displayCustomerType(value) {
    const labels = {
        MEMBER: "회원",
        NON_MEMBER: "비회원"
    }

    return labels[value] || displayValue(value)
}

function displayWashStatus(value) {
    const labels = {
        ENTERED: "입차",
        WASHING: "세차중",
        WASH_DONE: "세차완료",
        EXITED: "출차"
    }

    return labels[value] || displayValue(value)
}

function displayPaymentStatus(value) {
    const labels = {
        WAITING: "결제대기",
        SUCCESS: "결제완료",
        DONE: "결제완료",
        FAILED: "결제실패",
        CANCELED: "결제취소"
    }

    return labels[value] || displayValue(value)
}

function getWashStatusClass(value) {
    if (value === "EXITED" || value === "WASH_DONE") {
        return "status-done"
    }

    if (value === "WASHING") {
        return "status-active"
    }

    return "status-waiting"
}

function getPaymentStatusClass(value) {
    if (value === "SUCCESS" || value === "DONE") {
        return "status-done"
    }

    if (value === "FAILED" || value === "CANCELED") {
        return "status-error"
    }

    return "status-waiting"
}
</script>

<style scoped>
.wash-history-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: end;
    gap: 18px;
    width: 100%;
}

.page-header > div {
    grid-column: 2;
    text-align: center;
}

.page-header .primary-button {
    grid-column: 3;
    justify-self: end;
}

.eyebrow {
    margin: 0 0 7px;
    color: #5b728f;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0;
    text-transform: uppercase;
}

h1,
h2 {
    margin: 0;
    color: #0f172a;
    font-weight: 900;
    letter-spacing: 0;
}

h1 {
    font-size: 30px;
}

h2 {
    font-size: 19px;
}

.page-header p:not(.eyebrow) {
    margin-top: 8px;
    color: #64748b;
    font-size: 14px;
}

.filter-panel,
.result-section,
.empty-state {
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.filter-panel {
    display: grid;
    gap: 12px;
    padding: 18px;
}

.site-filter {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
}

.detail-filter,
.guest-panel {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    align-items: end;
}

.guest-panel .notice-text {
    grid-column: 1 / -1;
}

label {
    display: grid;
    gap: 7px;
    color: #344054;
    font-size: 13px;
    font-weight: 800;
    text-align: left;
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
    outline: none;
    border-color: #0b1f3a;
    box-shadow: 0 0 0 3px rgba(11, 31, 58, 0.12);
}

.primary-button {
    min-width: 104px;
    height: 42px;
    border: 0;
    border-radius: 6px;
    background: #0b1f3a;
    color: #ffffff;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.primary-button:hover {
    background: #163b68;
}

.state-text,
.error-text,
.notice-text {
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

.empty-state {
    display: grid;
    gap: 8px;
    padding: 32px;
    color: #64748b;
    text-align: center;
}

.empty-state strong {
    color: #0f172a;
    font-size: 16px;
}

.result-section {
    display: grid;
    gap: 12px;
    min-width: 0;
    padding: 20px;
}

.result-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-width: 0;
}

.result-header > div {
    min-width: 0;
}

.result-header strong {
    color: #0b1f3a;
    font-size: 18px;
}

.table-wrap {
    min-width: 0;
    max-width: 100%;
    overflow-x: auto;
    overscroll-behavior-inline: contain;
    border: 1px solid #e4ebf5;
    border-radius: 8px;
}

.pagination {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding-top: 8px;
}

.pagination span {
    min-width: 72px;
    color: #0b1f3a;
    font-weight: 900;
    text-align: center;
}

.pagination button {
    min-width: 64px;
    min-height: 36px;
    border: 1px solid #cdd8e8;
    border-radius: 6px;
    background: #ffffff;
    color: #26364a;
    font-weight: 800;
    cursor: pointer;
}

.pagination button:hover:not(:disabled) {
    border-color: #0b1f3a;
    color: #0b1f3a;
}

.pagination button:disabled {
    color: #94a3b8;
    background: #edf2f8;
    cursor: not-allowed;
}

table {
    width: max-content;
    min-width: 100%;
    border-collapse: collapse;
    background: #ffffff;
    table-layout: auto;
}

th,
td {
    padding: 12px 8px;
    border-bottom: 1px solid #edf2f7;
    line-height: 1.4;
    text-align: left;
    white-space: nowrap;
    overflow-wrap: normal;
    word-break: keep-all;
}

.col-id {
    min-width: 54px;
}

.col-site {
    min-width: 160px;
}

.col-plate {
    min-width: 104px;
}

.col-customer,
.col-status {
    min-width: 82px;
}

.col-course {
    min-width: 144px;
}

.col-amount {
    min-width: 94px;
}

.col-time {
    min-width: 160px;
}

th {
    background: #f6f8fb;
    color: #344054;
    font-size: 13px;
    font-weight: 900;
}

td {
    color: #26364a;
    font-size: 14px;
}

tbody tr {
    cursor: pointer;
    transition: background-color 120ms ease, box-shadow 120ms ease;
}

tbody tr:hover,
tbody tr:focus-visible {
    background: #eaf2ff;
    outline: none;
    box-shadow: inset 3px 0 0 #2563eb;
}

tbody tr:focus-visible {
    box-shadow: inset 3px 0 0 #0b1f3a, inset 0 0 0 1px #0b1f3a;
}

th:nth-last-child(-n + 2),
td:nth-last-child(-n + 2) {
    width: 76px;
    white-space: nowrap;
    overflow-wrap: normal;
}

th:nth-last-child(4),
td:nth-last-child(4),
th:nth-last-child(3),
td:nth-last-child(3) {
    width: 132px;
    white-space: nowrap;
    overflow-wrap: normal;
}

th:nth-last-child(5),
td:nth-last-child(5) {
    width: 82px;
    white-space: nowrap;
    overflow-wrap: normal;
}

tbody tr:last-child td {
    border-bottom: 0;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
    min-height: 26px;
    max-width: 100%;
    padding: 3px 7px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 900;
    white-space: nowrap;
}

.status-done {
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

@media (max-width: 900px) {
    .page-header {
        display: flex;
        align-items: flex-start;
        flex-direction: column;
    }

    .page-header > div {
        text-align: left;
    }

    .page-header .primary-button {
        width: auto;
    }

    .site-filter,
    .detail-filter,
    .guest-panel {
        grid-template-columns: 1fr;
    }

    .result-section {
        padding: 14px;
    }
}
</style>
