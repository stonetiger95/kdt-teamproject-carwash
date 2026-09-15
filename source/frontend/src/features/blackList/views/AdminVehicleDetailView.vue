<template>
    <main class="vehicle-detail-page">
        <section class="page-hero">
            <div>
                <p class="eyebrow">관리자 차량 관리</p>
                <h1>차량 상세</h1>
                <p>차량 정보와 입출차·블랙리스트 이력을 확인합니다.</p>
            </div>

            <button
                class="ghost-button"
                type="button"
                @click="goList"
            >
                목록
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

        <p
            v-if="store.actionMessage"
            :class="['status-message', store.actionSuccess ? 'success' : 'error']"
        >
            {{ store.actionMessage }}
        </p>

        <template v-if="store.vehicleDetail">
            <section class="detail-card">
                <header class="section-title">
                    <h2>{{ store.vehicleDetail.plateNumber }}</h2>

                    <div class="header-badges">
                        <span :class="['badge', store.vehicleDetail.blacklisted ? 'danger' : 'success']">
                            {{ store.vehicleDetail.blacklisted ? "입차 차단" : "정상 차량" }}
                        </span>
                        <span
                            v-if="store.vehicleDetail.manualBlacklisted"
                            class="badge warning"
                        >
                            수동 등록
                        </span>
                        <span
                            v-if="store.vehicleDetail.suspendedBlacklisted"
                            class="badge muted"
                        >
                            회원 정지
                        </span>
                    </div>
                </header>

                <div class="summary-grid">
                    <div>
                        <span>차량 구분</span>
                        <strong>{{ displayCustomerType(store.vehicleDetail.customerType) }}</strong>
                    </div>
                    <div>
                        <span>회원명</span>
                        <strong>
                            <button
                                v-if="store.vehicleDetail.customerId"
                                class="member-link"
                                type="button"
                                @click="goMemberDetail"
                            >
                                {{ displayValue(store.vehicleDetail.customerName) }}
                            </button>
                            <template v-else>
                                {{ displayValue(store.vehicleDetail.customerName) }}
                            </template>
                        </strong>
                    </div>
                    <div>
                        <span>회원 상태</span>
                        <strong>{{ displayCustomerStatus(store.vehicleDetail.customerStatus) }}</strong>
                    </div>
                    <div>
                        <span>차량 상태</span>
                        <strong>{{ displayVehicleStatus(store.vehicleDetail.vehicleStatus) }}</strong>
                    </div>
                    <div>
                        <span>방문 횟수</span>
                        <strong>{{ store.vehicleDetail.visitCount || 0 }}회</strong>
                    </div>
                    <div>
                        <span>최초 입차</span>
                        <strong>{{ formatDateTime(store.vehicleDetail.firstEntryTime) }}</strong>
                    </div>
                    <div>
                        <span>최근 입차</span>
                        <strong>{{ formatDateTime(store.vehicleDetail.lastEntryTime) }}</strong>
                    </div>
                </div>

                <section class="action-panel">
                    <div>
                        <h3>블랙리스트 관리</h3>
                        <p v-if="store.vehicleDetail.customerType === 'MEMBER'">
                            회원 정지와 별개로 이 차량만 수동 등록하거나 해제할 수 있습니다.
                        </p>
                        <p v-else>
                            관리자가 사유를 입력해 이 차량을 수동 등록하거나 해제할 수 있습니다.
                        </p>
                    </div>

                    <div class="action-buttons">
                        <button
                            v-if="store.canRegisterManual"
                            class="danger-button"
                            type="button"
                            :disabled="store.actionLoading"
                            @click="openRegisterModal"
                        >
                            수동 블랙리스트 등록
                        </button>

                        <button
                            v-if="store.canReleaseManual"
                            class="ghost-button"
                            type="button"
                            :disabled="store.actionLoading"
                            @click="releaseBlackList"
                        >
                            수동 블랙리스트 해제
                        </button>
                    </div>
                </section>
            </section>

            <section class="table-card">
                <header class="section-title">
                    <h2>입출차 이력</h2>
                    <span>총 {{ entryExitHistory.length }}건</span>
                </header>

                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>ID</th>
                                <th>입차 시간</th>
                                <th>출차 시간</th>
                                <th>상태</th>
                                <th>입차 탐지 로그</th>
                                <th>출차 탐지 로그</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr
                                v-for="history in entryExitHistory"
                                :key="history.entryExitId"
                            >
                                <td>{{ history.entryExitId }}</td>
                                <td>{{ formatDateTime(history.entryTime) }}</td>
                                <td>{{ formatDateTime(history.exitTime) }}</td>
                                <td>
                                    <span :class="['badge', history.status === 'EXITED' ? 'success' : 'warning']">
                                        {{ displayEntryStatus(history.status) }}
                                    </span>
                                </td>
                                <td>{{ displayValue(history.entryDetectionLogId) }}</td>
                                <td>{{ displayValue(history.exitDetectionLogId) }}</td>
                            </tr>
                            <tr v-if="entryExitHistory.length === 0">
                                <td
                                    class="empty-cell"
                                    colspan="6"
                                >
                                    입출차 이력이 없습니다.
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="table-card">
                <header class="section-title">
                    <h2>블랙리스트 이력</h2>
                    <span>총 {{ blackListHistory.length }}건</span>
                </header>

                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>유형</th>
                                <th>사유</th>
                                <th>등록일</th>
                                <th>해제일</th>
                                <th>상태</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr
                                v-for="history in blackListHistory"
                                :key="history.vehicleBlacklistId"
                            >
                                <td>{{ displayBlackListType(history.blacklistType) }}</td>
                                <td class="reason-cell">{{ displayValue(history.reason) }}</td>
                                <td>{{ formatDateTime(history.createdAt) }}</td>
                                <td>{{ formatDateTime(history.releasedAt) }}</td>
                                <td>
                                    <span :class="['badge', history.active ? 'danger' : 'muted']">
                                        {{ history.active ? "활성" : "해제" }}
                                    </span>
                                </td>
                            </tr>
                            <tr v-if="blackListHistory.length === 0">
                                <td
                                    class="empty-cell"
                                    colspan="5"
                                >
                                    블랙리스트 이력이 없습니다.
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>
        </template>

        <div
            v-if="registerModalOpen"
            class="modal-backdrop"
            @click.self="closeRegisterModal"
        >
            <section class="modal-card" role="dialog" aria-modal="true">
                <header>
                    <h2>수동 블랙리스트 등록</h2>
                    <button
                        class="close-button"
                        type="button"
                        aria-label="닫기"
                        @click="closeRegisterModal"
                    >
                        ×
                    </button>
                </header>

                <label>
                    <span>등록 사유</span>
                    <textarea
                        v-model.trim="reason"
                        rows="5"
                        maxlength="500"
                        placeholder="블랙리스트 등록 사유를 입력하세요."
                    ></textarea>
                </label>

                <div class="modal-actions">
                    <button
                        class="ghost-button"
                        type="button"
                        :disabled="store.actionLoading"
                        @click="closeRegisterModal"
                    >
                        취소
                    </button>
                    <button
                        class="danger-button"
                        type="button"
                        :disabled="store.actionLoading"
                        @click="registerBlackList"
                    >
                        {{ store.actionLoading ? "등록 중..." : "등록" }}
                    </button>
                </div>
            </section>
        </div>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useBlackListStore } from "../stores/blackListStore"

const route = useRoute()
const router = useRouter()
const store = useBlackListStore()

const registerModalOpen = ref(false)
const reason = ref("")

const plateNumber = computed(() => String(route.params.plateNumber || ""))
const entryExitHistory = computed(() =>
    store.vehicleDetail?.entryExitHistory || []
)
const blackListHistory = computed(() =>
    store.vehicleDetail?.blackListHistory || []
)

onMounted(() => {
    store.fetchVehicleDetail(plateNumber.value)
})

onBeforeUnmount(() => {
    store.clearVehicleDetail()
})

function openRegisterModal() {
    reason.value = ""
    store.clearActionMessage()
    registerModalOpen.value = true
}

function closeRegisterModal() {
    if (store.actionLoading) {
        return
    }

    registerModalOpen.value = false
    reason.value = ""
}

async function registerBlackList() {
    const success = await store.registerBlackList(
        plateNumber.value,
        reason.value
    )

    if (success) {
        registerModalOpen.value = false
        reason.value = ""
    }
}

async function releaseBlackList() {
    if (!confirm("수동 블랙리스트 등록을 해제하시겠습니까?")) {
        return
    }

    await store.releaseBlackList(plateNumber.value)
}

function goList() {
    router.push({ name: "admin-vehicles" })
}

function goMemberDetail() {
    const customerId = store.vehicleDetail?.customerId

    if (!customerId) {
        return
    }

    router.push({
        name: "admin-member-manage",
        query: { customerId }
    })
}

function displayValue(value) {
    return value === null || value === undefined || value === ""
        ? "-"
        : value
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

function displayEntryStatus(value) {
    const labels = {
        ENTERED: "입차 중",
        EXITED: "출차 완료"
    }

    return labels[value] || displayValue(value)
}

function displayBlackListType(value) {
    const labels = {
        MANUAL: "수동 등록",
        CUSTOMER_SUSPENDED: "회원 정지"
    }

    return labels[value] || displayValue(value)
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
.vehicle-detail-page {
    display: grid;
    gap: 20px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.page-hero,
.detail-card,
.table-card,
.modal-card {
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
}

.page-hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    padding: 30px 34px;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

.page-hero h1 {
    margin: 0;
    font-size: 34px;
    font-weight: 900;
}

.page-hero p {
    margin: 10px 0 0;
    color: #52637d;
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

.status-message.success {
    color: #1c6b4f;
    background: #e7f7ef;
}

.status-message.error {
    color: #b42318;
    background: #fff1f1;
}

.detail-card,
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

.section-title > span {
    color: #52637d;
    font-size: 14px;
    font-weight: 800;
}

.header-badges,
.action-buttons,
.modal-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.summary-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    padding: 20px 24px;
}

.summary-grid div {
    padding: 14px;
    border-radius: 8px;
    background: #f5f8fc;
}

.summary-grid span,
.modal-card label span {
    display: block;
    color: #52637d;
    font-size: 13px;
    font-weight: 800;
}

.summary-grid strong {
    display: block;
    margin-top: 5px;
    font-size: 16px;
    font-weight: 900;
}

.member-link {
    padding: 0;
    border: 0;
    color: #1c3d70;
    background: transparent;
    font: inherit;
    font-weight: 900;
    text-decoration: underline;
    text-underline-offset: 3px;
    cursor: pointer;
}

.member-link:hover {
    color: #0a1931;
}

.action-panel {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    margin: 0 24px 24px;
    padding: 18px;
    border: 1px solid #e3eaf4;
    border-radius: 8px;
    background: #f8fafc;
}

.action-panel h3 {
    margin: 0;
    font-size: 18px;
}

.action-panel p {
    margin: 7px 0 0;
    color: #52637d;
    font-size: 14px;
}

.ghost-button,
.danger-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.danger-button {
    color: #ffffff;
    border-color: #b42318;
    background: #b42318;
}

button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
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

.badge.warning {
    color: #9a6700;
    background: #fff7d6;
}

.badge.muted {
    color: #52637d;
    background: #edf2f8;
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    min-width: 760px;
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

.reason-cell {
    max-width: 360px;
    white-space: normal;
}

.empty-cell {
    padding: 34px 12px;
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

.modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: 1200;
    display: grid;
    padding: 20px;
    background: rgba(7, 27, 58, 0.45);
    place-items: center;
}

.modal-card {
    width: min(100%, 520px);
    padding: 24px;
}

.modal-card header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20px;
}

.modal-card h2 {
    margin: 0;
    font-size: 22px;
}

.close-button {
    border: 0;
    color: #52637d;
    background: transparent;
    font-size: 28px;
    cursor: pointer;
}

.modal-card label {
    display: grid;
    gap: 8px;
}

textarea {
    width: 100%;
    padding: 12px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    font: inherit;
    resize: vertical;
    box-sizing: border-box;
}

textarea:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
    outline: none;
}

.modal-actions {
    justify-content: flex-end;
    margin-top: 20px;
}

@media (max-width: 800px) {
    .page-hero,
    .action-panel {
        align-items: stretch;
        flex-direction: column;
    }

    .summary-grid {
        grid-template-columns: 1fr;
    }
}
</style>
