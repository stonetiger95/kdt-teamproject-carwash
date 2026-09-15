<template>
    <main class="vehicle-page">
        <section class="page-hero">
            <p class="eyebrow"></p>
            <h1>차량 관리</h1>
            <p>입출차 차량과 블랙리스트 상태를 확인합니다.</p>
        </section>

        <section class="filter-card">
            <input
                v-model.trim="search.keyword"
                type="text"
                placeholder="차량번호 검색"
                @keyup.enter="searchVehicles"
            >

            <button
                class="primary-button"
                type="button"
                @click="searchVehicles"
            >
                조회
            </button>
        </section>

        <p
            v-if="store.loading"
            class="status-message"
        >
            불러오는 중입니다.
        </p>

        <p
            v-if="store.errorMessage"
            class="status-message error"
        >
            {{ store.errorMessage }}
        </p>

        <section class="table-card">
            <header class="section-title">
                <h2>차량 목록</h2>
                <span>총 {{ store.vehiclePageInfo.totalElements || store.vehicles.length }}대</span>
            </header>

            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>차량번호</th>
                            <th>구분</th>
                            <th>회원명</th>
                            <th>회원 상태</th>
                            <th>차량 상태</th>
                            <th>방문 횟수</th>
                            <th>최근 입차</th>
                            <th>차단 상태</th>
                            <th>작업</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="vehicle in store.vehicles"
                            :key="vehicle.plateNumber"
                            class="detail-row"
                            tabindex="0"
                            role="button"
                            :aria-label="`${displayValue(vehicle.plateNumber)} 차량 상세보기`"
                            @click="selectVehicle(vehicle.plateNumber)"
                            @keydown.enter="selectVehicle(vehicle.plateNumber)"
                            @keydown.space.prevent="selectVehicle(vehicle.plateNumber)"
                        >
                            <td class="strong-cell">{{ displayValue(vehicle.plateNumber) }}</td>
                            <td>{{ displayCustomerType(vehicle.customerType) }}</td>
                            <td>{{ displayValue(vehicle.customerName) }}</td>
                            <td>{{ displayCustomerStatus(vehicle.customerStatus) }}</td>
                            <td>{{ displayVehicleStatus(vehicle.vehicleStatus) }}</td>
                            <td>{{ vehicle.visitCount || 0 }}</td>
                            <td>{{ formatDateTime(vehicle.lastEntryTime) }}</td>
                            <td>
                                <span :class="['badge', vehicle.blacklisted ? 'danger' : 'success']">
                                    {{ vehicle.blacklisted ? "차단" : "정상" }}
                                </span>
                            </td>
                            <td>
                                <button
                                    class="small-button"
                                    type="button"
                                    @click.stop="selectVehicle(vehicle.plateNumber)"
                                >
                                    상세
                                </button>
                            </td>
                        </tr>

                        <tr v-if="!store.loading && store.vehicles.length === 0">
                            <td
                                class="empty-cell"
                                colspan="9"
                            >
                                조회된 차량이 없습니다.
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="pagination">
                <button
                    type="button"
                    :disabled="!store.vehiclePageInfo.hasPrevious"
                    @click="movePage(store.vehiclePageInfo.page - 1)"
                >
                    이전
                </button>

                <span>
                    {{ store.vehiclePageInfo.page }} / {{ store.vehiclePageInfo.totalPages || 1 }}
                </span>

                <button
                    type="button"
                    :disabled="!store.vehiclePageInfo.hasNext"
                    @click="movePage(store.vehiclePageInfo.page + 1)"
                >
                    다음
                </button>
            </div>
        </section>
    </main>
</template>

<script setup>
import { onMounted, reactive } from "vue"
import { useRouter } from "vue-router"
import { useBlackListStore } from "../stores/blackListStore"

const router = useRouter()
const store = useBlackListStore()

const search = reactive({
    keyword: ""
})

onMounted(() => {
    store.fetchVehicles()
})

function buildSearchParams(page = 1) {
    return {
        page,
        keyword: search.keyword
    }
}

async function searchVehicles() {
    await store.fetchVehicles(buildSearchParams(1))
}

async function movePage(page) {
    await store.fetchVehicles(buildSearchParams(page))
}

function selectVehicle(plateNumber) {
    router.push({
        name: "admin-vehicle-detail",
        params: { plateNumber }
    })
}

function displayValue(value) {
    return value || "-"
}

function displayCustomerType(value) {
    return value === "MEMBER" ? "회원" : "비회원"
}

function displayCustomerStatus(value) {
    const labels = {
        ACTIVE: "활성",
        SUSPENDED: "정지",
        WITHDRAWN: "탈퇴"
    }

    return labels[value] || "-"
}

function displayVehicleStatus(value) {
    const labels = {
        ACTIVE: "활성",
        DEACTIVATED: "비활성"
    }

    return labels[value] || "미등록"
}

function formatDateTime(value) {
    if (!value) {
        return "-"
    }

    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value))
}
</script>

<style scoped>
.vehicle-page {
    display: grid;
    gap: 20px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.page-hero,
.filter-card,
.table-card {
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
}

.page-hero {
    padding: 30px 34px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
}

.page-hero h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
}

.page-hero p {
    margin: 10px 0 0;
    color: #52637d;
    font-size: 15px;
}

.filter-card {
    display: grid;
    grid-template-columns: minmax(220px, 1fr) auto;
    gap: 10px;
    padding: 18px 22px;
}

input {
    width: 100%;
    min-height: 40px;
    padding: 0 12px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
    outline: none;
}

input:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.primary-button,
.small-button,
.pagination button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 8px;
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 8px 16px rgba(7, 27, 58, 0.14);
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.small-button {
    min-height: 34px;
    padding: 0 12px;
    box-shadow: none;
}

tbody tr.detail-row {
    cursor: pointer;
    transition: background-color 120ms ease, box-shadow 120ms ease;
}

tbody tr.detail-row:hover,
tbody tr.detail-row:focus-visible {
    background: #eaf2ff;
    outline: none;
    box-shadow: inset 3px 0 0 #2563eb;
}

tbody tr.detail-row:focus-visible {
    box-shadow: inset 3px 0 0 #0b1f3a, inset 0 0 0 1px #0b1f3a;
}

.status-message {
    margin: 0;
    padding: 14px 16px;
    border-radius: 8px;
    color: #1c3d70;
    background: #e8eef8;
    font-weight: 800;
    text-align: center;
}

.status-message.error {
    color: #b42318;
    background: #fff1f1;
}

.table-card {
    overflow: hidden;
}

.section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 20px 24px;
    border-bottom: 1px solid #e3eaf4;
}

.section-title h2 {
    margin: 0;
    font-size: 22px;
    font-weight: 900;
}

.section-title span {
    color: #52637d;
    font-size: 14px;
    font-weight: 800;
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    min-width: 950px;
    border-collapse: collapse;
}

th,
td {
    padding: 14px 12px;
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

.strong-cell {
    color: #071b3a;
    font-weight: 900;
}

.badge {
    display: inline-flex;
    align-items: center;
    min-height: 26px;
    padding: 0 10px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 900;
}

.badge.success {
    color: #1c6b4f;
    background: #e7f7ef;
}

.badge.danger {
    color: #b42318;
    background: #fff1f1;
}

.empty-cell {
    padding: 34px 12px;
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

.pagination {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 16px 20px 20px;
}

.pagination span {
    min-width: 72px;
    font-weight: 900;
    text-align: center;
}

.pagination button:disabled {
    color: #94a3b8;
    background: #edf2f8;
    box-shadow: none;
    cursor: not-allowed;
}

@media (max-width: 700px) {
    .filter-card {
        grid-template-columns: 1fr;
    }
}
</style>
