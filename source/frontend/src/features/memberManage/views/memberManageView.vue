<template>
    <main class="member-page">
        <section class="page-hero">
            <p class="eyebrow"></p>
            <h1>회원 관리</h1>
            <p>회원 상태와 차량 정보를 확인하고 관리합니다.</p>
        </section>

        <section class="filter-card">
            <input
                v-model.trim="search.keyword"
                type="text"
                placeholder="검색어"
                @keyup.enter="searchMembers"
            >

            <select v-model="search.status">
                <option
                    v-for="option in store.statusOptions"
                    :key="option.value"
                    :value="option.value"
                >
                    {{ option.label }}
                </option>
            </select>

            <select v-model="search.customerType">
                <option
                    v-for="option in store.customerTypeOptions"
                    :key="option.value"
                    :value="option.value"
                >
                    {{ option.label }}
                </option>
            </select>

            <button
                class="primary-button"
                type="button"
                @click="searchMembers"
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
                <h2>회원 목록</h2>
                <span>총 {{ store.pageInfo.totalElements || store.members.length }}명</span>
            </header>

            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>로그인 ID</th>
                            <th>이름</th>
                            <th>전화번호</th>
                            <th>유형</th>
                            <th>상태</th>
                            <th>활성 차량</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="member in store.members"
                            :key="member.customerId"
                            class="detail-row"
                            tabindex="0"
                            role="button"
                            :aria-label="`${displayValue(member.name)} 회원 상세보기`"
                            @click="selectMember(member.customerId)"
                            @keydown.enter="selectMember(member.customerId)"
                            @keydown.space.prevent="selectMember(member.customerId)"
                        >
                            <td>{{ member.customerId }}</td>
                            <td>{{ displayValue(member.loginId) }}</td>
                            <td class="strong-cell">{{ displayValue(member.name) }}</td>
                            <td>{{ displayValue(member.phone) }}</td>
                            <td>{{ displayCustomerType(member.customerType) }}</td>
                            <td>
                                <span :class="['badge', member.status === 'ACTIVE' ? 'success' : 'muted']">
                                    {{ displayStatus(member.status) }}
                                </span>
                            </td>
                            <td>{{ member.activeVehicleCount || 0 }}</td>
                        </tr>

                        <tr v-if="!store.loading && store.members.length === 0">
                            <td
                                class="empty-cell"
                                colspan="7"
                            >
                                조회된 회원이 없습니다.
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="pagination">
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
            v-if="store.memberDetail"
            ref="detailCard"
            class="detail-card"
        >
            <header class="section-title">
                <h2>회원 상세</h2>
                <button
                    class="ghost-button"
                    type="button"
                    @click="closeDetail"
                >
                    닫기
                </button>
            </header>

            <div class="summary-grid">
                <div>
                    <span>고객 ID</span>
                    <strong>{{ store.memberDetail.customerId }}</strong>
                </div>
                <div>
                    <span>로그인 ID</span>
                    <strong>{{ displayValue(store.memberDetail.loginId) }}</strong>
                </div>
                <div>
                    <span>상태</span>
                    <strong>{{ displayStatus(store.memberDetail.status) }}</strong>
                </div>
                <div>
                    <span>전체 차량</span>
                    <strong>{{ store.memberDetail.vehicleCount || 0 }}</strong>
                </div>
                <div>
                    <span>활성 차량</span>
                    <strong>{{ store.memberDetail.activeVehicleCount || 0 }}</strong>
                </div>
            </div>

            <form
                class="detail-form"
                @submit.prevent="saveMember"
            >
                <label>
                    <span>이름</span>
                    <input
                        v-model.trim="form.name"
                        type="text"
                        :disabled="!editing"
                    >
                </label>

                <label>
                    <span>생년월일</span>
                    <input
                        v-model="form.birthDate"
                        type="date"
                        :disabled="!editing"
                    >
                </label>

                <label>
                    <span>전화번호</span>
                    <input
                        v-model.trim="form.phone"
                        type="text"
                        :disabled="!editing"
                    >
                </label>

                <label>
                    <span>성별</span>
                    <input
                        v-model.trim="form.gender"
                        type="text"
                        :disabled="!editing"
                    >
                </label>

                <label>
                    <span>이메일</span>
                    <input
                        v-model.trim="form.emailAddress"
                        type="email"
                        :disabled="!editing"
                    >
                </label>

                <div class="form-actions"
                      v-if="store.memberDetail.status !== 'WITHDRAWN'"
                >
                    <button
                        v-if="!editing"
                        class="primary-button"
                        type="button"
                        @click="startEdit"
                    >
                        수정하기
                    </button>

                    <template v-else>
                        <button
                            class="primary-button"
                            type="submit"
                        >
                            저장
                        </button>
                        <button
                            class="ghost-button"
                            type="button"
                            @click="cancelEdit"
                        >
                            취소
                        </button>
                    </template>

                    <button
                        v-if="!editing"
                        class="ghost-button"
                        type="button"
                        @click="toggleStatus"
                    >
                        {{
                           store.memberDetail.status === "ACTIVE"
                                ? "정지"
                                : "활성"
                        }}
                    </button>
                </div>
                <p v-else>
                  탈퇴 회원은 조회만 가능합니다.
                </p>
            </form>
            
            <button
                class="ghost-button"
                type="button"
                @click="closeDetail"
            >
                닫기
            </button>
        

            <section class="vehicle-section">
                <h3>차량 목록</h3>

                <p
                    v-if="store.actionMessage"
                    :class="['status-message', store.actionSuccess ? 'success' : 'error']"
                >
                    {{ store.actionMessage }}
                </p>

                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>ID</th>
                                <th>번호판</th>
                                <th>차종</th>
                                <th>상태</th>
                                <th>블랙리스트</th>
                                <th>관리</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr
                                v-for="vehicle in store.memberDetail.vehicles || []"
                                :key="vehicle.vehicleId"
                            >
                                <td>{{ vehicle.vehicleId }}</td>
                                <td class="strong-cell">{{ displayValue(vehicle.plateNumber) }}</td>
                                <td>{{ displayValue(vehicle.vehicleType) }}</td>
                                <td>{{ displayValue(vehicle.vehicleStatus) }}</td>
                                <td>
                                    <span
                                        v-if="vehicle.manualBlacklisted"
                                        class="badge warning"
                                    >
                                        개별 블랙
                                    </span>
                                    <span
                                        v-if="vehicle.suspendedBlacklisted"
                                        class="badge muted"
                                    >
                                        회원 정지
                                    </span>
                                    <span
                                        v-if="!vehicle.manualBlacklisted && !vehicle.suspendedBlacklisted"
                                        class="badge success"
                                    >
                                        정상
                                    </span>
                                </td>
                                <td>
                                    <button
                                        v-if="vehicle.vehicleStatus === 'ACTIVE' && !vehicle.manualBlacklisted"
                                        class="small-button"
                                        type="button"
                                        :disabled="store.actionLoading"
                                        @click="registerVehicleBlackList(vehicle)"
                                    >
                                        개별 블랙 등록
                                    </button>
                                    <button
                                        v-if="vehicle.manualBlacklisted"
                                        class="ghost-button"
                                        type="button"
                                        :disabled="store.actionLoading"
                                        @click="releaseVehicleBlackList(vehicle)"
                                    >
                                        개별 블랙 해제
                                    </button>
                                </td>
                            </tr>
                            <tr v-if="(store.memberDetail.vehicles || []).length === 0">
                                <td
                                    class="empty-cell"
                                    colspan="6"
                                >
                                    등록된 차량이 없습니다.
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
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useMemberManageStore } from "../stores/memberManageStore"

const route = useRoute()
const router = useRouter()
const store = useMemberManageStore()
const editing = ref(false)
const detailCard = ref(null)

const search = reactive({
    keyword: "",
    status: "",
    customerType: ""
})

const form = reactive({
    name: "",
    birthDate: "",
    phone: "",
    gender: "",
    emailAddress: ""
})

onMounted(async () => {
    if (!route.query.customerId) {
        clearDetailState()
    }

    await store.fetchMembers()
    await openMemberFromRoute(route.query.customerId)
})

watch(
    () => route.query.customerId,
    customerId => {
        if (!customerId) {
            clearDetailState()
            return
        }

        openMemberFromRoute(customerId)
    }
)

onBeforeUnmount(clearDetailState)

function buildSearchParams(page = 1) {
    return {
        page,
        keyword: search.keyword,
        status: search.status,
        customerType: search.customerType
    }
}

async function searchMembers() {
    await closeDetail()
    await store.fetchMembers(buildSearchParams(1))
}

async function movePage(page) {
    await closeDetail()
    await store.fetchMembers(buildSearchParams(page))
}

async function selectMember(customerId) {
    editing.value = false

    const success = await store.fetchMemberDetail(customerId)

    if (success) {
        fillForm()
        await router.replace({
            name: "admin-member-manage",
            query: { customerId }
        })
        await nextTick()
        detailCard.value?.scrollIntoView({
            behavior: "smooth",
            block: "start"
        })
    }
}

async function openMemberFromRoute(customerId) {
    const normalizedCustomerId = Number(customerId)

    if (!Number.isInteger(normalizedCustomerId)
            || normalizedCustomerId < 1
            || store.memberDetail?.customerId === normalizedCustomerId) {
        return
    }

    await selectMember(normalizedCustomerId)
}

function fillForm() {
    const detail = store.memberDetail

    form.name = detail?.name || ""
    form.birthDate = detail?.birthDate || ""
    form.phone = detail?.phone || ""
    form.gender = detail?.gender || ""
    form.emailAddress = detail?.emailAddress || ""
}

function startEdit() {
    fillForm()
    editing.value = true
}

function cancelEdit() {
    fillForm()
    editing.value = false
}

async function saveMember() {
    if (!store.memberDetail) {
        return
    }

    const success = await store.saveMember(store.memberDetail.customerId, {
        name: form.name,
        birthDate: form.birthDate,
        phone: form.phone,
        gender: form.gender,
        emailAddress: form.emailAddress
    })

    if (success) {
        editing.value = false
        fillForm()
        await store.fetchMembers(buildSearchParams(store.pageInfo.page))
    }
}

async function toggleStatus() {
    const detail = store.memberDetail

    if (!detail || detail.status === "WITHDRAWN") {
        return
    }

    const status =
        detail.status === "ACTIVE"
            ? "SUSPENDED"
            : "ACTIVE"

    const message =
        status === "SUSPENDED"
            ? "회원을 정지하시겠습니까?"
            : "회원 정지를 해제하시겠습니까?"

    if (!confirm(message)) {
        return
    }

    const success = await store.saveMember(
        detail.customerId,
        { status }
    )

    if (success) {
        fillForm()
        await store.fetchMembers(
            buildSearchParams(store.pageInfo.page)
        )
    }
}

async function registerVehicleBlackList(vehicle) {
    const reason = window.prompt(
        `${displayValue(vehicle.plateNumber)} 차량의 개별 블랙리스트 등록 사유를 입력해주세요.`
    )

    if (reason === null) {
        return
    }

    await store.registerVehicleBlackList(
        store.memberDetail.customerId,
        vehicle.plateNumber,
        reason
    )
}

async function releaseVehicleBlackList(vehicle) {
    if (!window.confirm(
        `${displayValue(vehicle.plateNumber)} 차량의 개별 블랙리스트를 해제할까요?`
    )) {
        return
    }

    await store.releaseVehicleBlackList(
        store.memberDetail.customerId,
        vehicle.plateNumber
    )
}

async function closeDetail() {
    clearDetailState()

    if (!route.query.customerId) {
        return
    }

    const query = { ...route.query }
    delete query.customerId
    await router.replace({
        name: "admin-member-manage",
        query
    })
}

function clearDetailState() {
    editing.value = false
    store.clearMemberDetail()
}

function displayValue(value) {
    return value || "-"
}

function displayStatus(value) {
    const labels = {
        ACTIVE: "활성",
        SUSPENDED: "정지",
        WITHDRAWN: "탈퇴"
    }

    return labels[value] || displayValue(value)
}

function displayCustomerType(value) {
    const labels = {
        MEMBER: "회원",
        NON_MEMBER: "비회원"
    }

    return labels[value] || displayValue(value)
}
</script>

<style scoped>
.member-page {
    display: grid;
    gap: 20px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.page-hero,
.filter-card,
.table-card,
.detail-card {
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
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

.page-hero h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
    letter-spacing: 0;
}

.page-hero p {
    margin: 10px 0 0;
    color: #52637d;
    font-size: 15px;
}

.filter-card {
    display: grid;
    grid-template-columns: minmax(220px, 1fr) 160px 160px auto;
    gap: 10px;
    padding: 18px 22px;
}

input,
select {
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

input:focus,
select:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.primary-button,
.small-button,
.ghost-button,
.pagination button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 8px;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
    transition: transform 0.18s ease, background 0.18s ease;
}

.primary-button,
.small-button,
.pagination button {
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 8px 16px rgba(7, 27, 58, 0.14);
}

.primary-button:hover,
.small-button:hover,
.pagination button:hover:not(:disabled) {
    background: #0f2f5f;
    transform: translateY(-1px);
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

.ghost-button {
    color: #071b3a;
    background: #ffffff;
    border-color: #cbd6e6;
}

.ghost-button:hover {
    background: #f5f8fc;
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

.status-message.success {
    color: #1c6b4f;
    background: #e7f7ef;
}

.table-card,
.detail-card {
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
    color: #071b3a;
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
    min-width: 900px;
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
    justify-content: center;
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

.badge.muted {
    color: #52637d;
    background: #edf2f8;
}

.badge.warning {
    color: #9a6700;
    background: #fff4d6;
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
    color: #071b3a;
    font-weight: 900;
    text-align: center;
}

.pagination button:disabled {
    color: #94a3b8;
    background: #edf2f8;
    box-shadow: none;
    cursor: not-allowed;
}

.summary-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
    padding: 20px 24px;
}

.summary-grid div {
    padding: 14px;
    border-radius: 8px;
    background: #f5f8fc;
}

.summary-grid span,
label span {
    display: block;
    color: #52637d;
    font-size: 13px;
    font-weight: 800;
}

.summary-grid strong {
    display: block;
    margin-top: 5px;
    color: #071b3a;
    font-size: 17px;
    font-weight: 900;
}

.detail-form {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
    padding: 0 24px 24px;
}

label {
    display: grid;
    gap: 7px;
}

input:disabled {
    color: #52637d;
    background: #f5f8fc;
}

.form-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    grid-column: 1 / -1;
    justify-content: end;
}

.vehicle-section {
    margin: 0 24px 24px;
    border: 1px solid #e3eaf4;
    border-radius: 8px;
    overflow: hidden;
}

.vehicle-section h3 {
    margin: 0;
    padding: 16px 18px;
    border-bottom: 1px solid #e3eaf4;
    color: #071b3a;
    font-size: 18px;
    font-weight: 900;
}

.vehicle-section table {
    min-width: 520px;
}

@media (max-width: 900px) {
    .filter-card,
    .detail-form,
    .summary-grid {
        grid-template-columns: 1fr;
    }

    .section-title {
        align-items: stretch;
        flex-direction: column;
    }

    .form-actions {
        justify-content: stretch;
    }

    .form-actions button {
        flex: 1;
    }
}
</style>
