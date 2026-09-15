<template>
    <main class="wash-detail-page">
        <header class="page-header">
            <div>
                <p class="eyebrow">{{ roleLabel }} 상세 조회</p>
                <h2>세차 상세내역</h2>
            </div>

            <div class="header-actions">
                <button
                    v-if="canRetryPayment"
                    class="retry-button"
                    type="button"
                    :disabled="store.paymentRetrying"
                    @click="retryPayment"
                >
                    {{ store.paymentRetrying ? "결제 확인 중..." : "결제 재시도" }}
                </button>
                <button
                    class="primary-button"
                    type="button"
                    :disabled="!canPrintReceipt"
                    @click="printReceipt"
                >
                    영수증 인쇄
                </button>
                <button
                    class="secondary-button"
                    type="button"
                    @click="goList"
                >
                    목록
                </button>
            </div>
        </header>

        <p
            v-if="store.loading"
            class="state-text"
        >
            불러오는 중...
        </p>

        <p
            v-if="store.error"
            class="error-text"
        >
            {{ store.error }}
        </p>

        <p
            v-if="store.paymentActionMessage"
            :class="store.paymentActionSuccess ? 'success-text' : 'error-text'"
        >
            {{ store.paymentActionMessage }}
        </p>

        <section
            v-if="store.washHistory"
            class="summary-band"
        >
            <div>
                <span>세차이용번호</span>
                <strong>{{ store.washHistory.washHistoryId }}</strong>
            </div>
            <div>
                <span>차량번호</span>
                <strong>{{ displayValue(store.washHistory.plateNumber) }}</strong>
            </div>
            <div>
                <span>세차상태</span>
                <strong>{{ displayWashStatus(store.washHistory.washStatus) }}</strong>
            </div>
            <div>
                <span>결제상태</span>
                <strong>{{ displayPaymentStatus(store.washHistory.paymentStatus) }}</strong>
            </div>
            <div>
                <span>결제금액</span>
                <strong>{{ formatAmount(store.washHistory.amount) }}</strong>
            </div>
        </section>

        <div
            v-if="store.washHistory"
            class="detail-grid"
        >
            <section class="detail-section">
                <h3>기본 정보</h3>

                <dl>
                    <dt>세차이용번호</dt>
                    <dd>{{ store.washHistory.washHistoryId }}</dd>

                    <dt>고객번호</dt>
                    <dd>{{ displayValue(store.washHistory.customerId) }}</dd>

                    <dt>고객구분</dt>
                    <dd>{{ displayCustomerType(store.washHistory.customerType) }}</dd>

                    <dt>차량ID</dt>
                    <dd>{{ displayValue(store.washHistory.vehicleId) }}</dd>

                    <dt>차량번호</dt>
                    <dd>{{ displayValue(store.washHistory.plateNumber) }}</dd>
                </dl>
            </section>

            <section class="detail-section">
                <h3>세차 정보</h3>

                <dl>
                    <dt>세차장번호</dt>
                    <dd>{{ displayValue(store.washHistory.washSiteId) }}</dd>

                    <dt>세차장</dt>
                    <dd>{{ displayValue(store.washHistory.siteName) }}</dd>

                    <dt>세차장 유선전화</dt>
                    <dd>{{ displaySitePhone(store.washHistory.sitePhone) }}</dd>

                    <dt>코스번호</dt>
                    <dd>{{ displayValue(store.washHistory.courseId) }}</dd>

                    <dt>세차코스</dt>
                    <dd>{{ displayValue(store.washHistory.courseName) }}</dd>

                    <dt>결제금액</dt>
                    <dd>{{ formatAmount(store.washHistory.amount) }}</dd>

                    <dt>세차상태</dt>
                    <dd>
                        <span :class="['status-badge', getWashStatusClass(store.washHistory.washStatus)]">
                            {{ displayWashStatus(store.washHistory.washStatus) }}
                        </span>
                    </dd>

                    <dt>결제상태</dt>
                    <dd>
                        <span :class="['status-badge', getPaymentStatusClass(store.washHistory.paymentStatus)]">
                            {{ displayPaymentStatus(store.washHistory.paymentStatus) }}
                        </span>
                    </dd>
                </dl>
            </section>

            <section class="detail-section full-row">
                <h3>시간 정보</h3>

                <dl class="time-list">
                    <dt>입차시간</dt>
                    <dd>{{ formatDateTime(store.washHistory.entryTime) }}</dd>

                    <dt>세차시작시간</dt>
                    <dd>{{ formatDateTime(store.washHistory.washStartTime) }}</dd>

                    <dt>세차종료시간</dt>
                    <dd>{{ formatDateTime(store.washHistory.washEndTime) }}</dd>

                    <dt>출차시간</dt>
                    <dd>{{ formatDateTime(store.washHistory.exitTime) }}</dd>
                </dl>
            </section>

            <section class="detail-section full-row photo-section">
                <h3>입출차 촬영 사진</h3>

                <p
                    v-if="store.imageError"
                    class="error-text"
                >
                    {{ store.imageError }}
                </p>

                <div class="photo-grid">
                    <figure>
                        <div class="photo-frame">
                            <img
                                v-if="store.entryImageUrl"
                                :src="store.entryImageUrl"
                                :alt="`${displayValue(store.washHistory.plateNumber)} 입차 사진`"
                            >
                            <span v-else>
                                {{ store.imageLoading ? "사진 확인 중..." : "입차 사진 없음" }}
                            </span>
                        </div>
                        <figcaption>
                            <strong>입차</strong>
                            <span>{{ formatDateTime(store.washHistory.entryTime) }}</span>
                        </figcaption>
                    </figure>

                    <figure>
                        <div class="photo-frame">
                            <img
                                v-if="store.exitImageUrl"
                                :src="store.exitImageUrl"
                                :alt="`${displayValue(store.washHistory.plateNumber)} 출차 사진`"
                            >
                            <span v-else>
                                {{ store.imageLoading ? "사진 확인 중..." : "출차 사진 없음" }}
                            </span>
                        </div>
                        <figcaption>
                            <strong>출차</strong>
                            <span>{{ formatDateTime(store.washHistory.exitTime) }}</span>
                        </figcaption>
                    </figure>
                </div>
            </section>

            <section class="detail-section full-row receipt-section">
                <h3>세차 영수증</h3>
                <dl>
                    <dt>이용번호</dt>
                    <dd>{{ store.washHistory.washHistoryId }}</dd>

                    <dt>세차장</dt>
                    <dd>{{ displayValue(store.washHistory.siteName) }}</dd>

                    <dt>세차코스</dt>
                    <dd>{{ displayValue(store.washHistory.courseName) }}</dd>

                    <dt>차량번호</dt>
                    <dd>{{ displayValue(store.washHistory.plateNumber) }}</dd>

                    <dt>이용금액</dt>
                    <dd>{{ formatAmount(store.washHistory.amount) }}</dd>

                    <dt>결제상태</dt>
                    <dd>{{ displayPaymentStatus(store.washHistory.paymentStatus) }}</dd>

                    <dt>세차완료시간</dt>
                    <dd>{{ formatDateTime(store.washHistory.washEndTime) }}</dd>
                </dl>
            </section>
        </div>

        <section
            v-else-if="!store.loading"
            class="empty-state"
        >
            <strong>세차 상세내역을 찾을 수 없습니다.</strong>
            <span>목록에서 다시 세차 이용 내역을 선택해 주세요.</span>
        </section>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useAuthStore } from "@/features/auth/stores/authStore"
import { useWashHistoryStore } from "../stores/washHistoryStore"

const props = defineProps({
    viewerRole: {
        type: String,
        required: true
    },
    listRouteName: {
        type: String,
        required: true
    }
})

const route = useRoute()
const router = useRouter()
const store = useWashHistoryStore()
const authStore = useAuthStore()

const canPrintReceipt = computed(() => {
    return ["SUCCESS", "DONE"].includes(store.washHistory?.paymentStatus)
})

const canRetryPayment = computed(() => {
    return props.viewerRole === "CUSTOMER"
        && store.washHistory?.customerType === "MEMBER"
        && !canPrintReceipt.value
        && authStore.canUseFeature("payment")
})

const roleLabel = computed(() => {
    if (props.viewerRole === "ADMIN") {
        return "관리자"
    }

    if (props.viewerRole === "OWNER") {
        return "업주"
    }

    return "고객"
})

onMounted(async () => {
    await store.fetchWashHistory(
        props.viewerRole,
        route.params.washHistoryId
    )

    if (store.washHistory) {
        await store.fetchWashHistoryImages(
            props.viewerRole,
            route.params.washHistoryId
        )
    }
})

onBeforeUnmount(() => {
    store.clearWashHistoryDetail()
})

function goList() {
    router.push({
        name: props.listRouteName
    })
}

function displayValue(value) {
    return value || "-"
}

function displaySitePhone(value) {
    return value || "미등록"
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

function printReceipt() {
    if (canPrintReceipt.value) {
        window.print()
    }
}

async function retryPayment() {
    await store.retryWashPayment(route.params.washHistoryId)
}

function displayCustomerType(value) {
    if (value === "MEMBER") {
        return "회원"
    }

    if (value === "NON_MEMBER") {
        return "비회원"
    }

    return displayValue(value)
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
.wash-detail-page {
    padding: 24px;
    color: #172033;
}

.page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 20px;
}

.header-actions {
    display: flex;
    gap: 8px;
}

.eyebrow {
    margin: 0 0 6px;
    color: #667085;
    font-size: 13px;
}

h2,
h3 {
    margin: 0;
}

h2 {
    font-size: 24px;
}

h3 {
    margin-bottom: 16px;
    font-size: 17px;
}

.primary-button,
.secondary-button,
.retry-button {
    min-width: 76px;
    height: 38px;
    border-radius: 6px;
    font-weight: 700;
    cursor: pointer;
}

.primary-button {
    border: 0;
    background: #2454d6;
    color: #ffffff;
}

.retry-button {
    border: 1px solid #1d4ed8;
    background: #ffffff;
    color: #1d4ed8;
}

.retry-button:hover:not(:disabled) {
    background: #eff6ff;
}

.retry-button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
}

.primary-button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.secondary-button {
    border: 1px solid #cbd5e1;
    background: #ffffff;
    color: #172033;
}

.secondary-button:hover {
    background: #f8fafc;
}

.state-text,
.error-text {
    margin: 16px 0;
}

.error-text {
    color: #c03434;
}

.success-text {
    color: #166534;
}

.summary-band {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 1px;
    overflow: hidden;
    margin-bottom: 18px;
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #d9e2f1;
}

.summary-band div {
    display: grid;
    gap: 6px;
    padding: 16px;
    background: #ffffff;
}

.summary-band span {
    color: #667085;
    font-size: 13px;
}

.summary-band strong {
    color: #172033;
    font-size: 18px;
}

.detail-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

.detail-section {
    padding: 18px;
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #ffffff;
}

.full-row {
    grid-column: 1 / -1;
}

.receipt-section {
    border-color: #b9c8e4;
}

.photo-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

.photo-grid figure {
    min-width: 0;
    margin: 0;
}

.photo-frame {
    display: grid;
    place-items: center;
    width: 100%;
    aspect-ratio: 16 / 9;
    overflow: hidden;
    border: 1px solid #d9e2f1;
    border-radius: 6px;
    background: #f8fafc;
    color: #667085;
}

.photo-frame img {
    width: 100%;
    height: 100%;
    object-fit: contain;
}

.photo-grid figcaption {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-top: 8px;
    color: #667085;
    font-size: 13px;
}

.photo-grid figcaption strong {
    color: #172033;
}

dl {
    display: grid;
    grid-template-columns: 140px minmax(0, 1fr);
    gap: 12px 16px;
    margin: 0;
}

.time-list {
    grid-template-columns: 150px minmax(0, 1fr);
}

dt {
    color: #667085;
    font-weight: 700;
}

dd {
    margin: 0;
    color: #172033;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 3px 9px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 700;
}

.status-done {
    background: #e9f7ef;
    color: #166534;
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
    background: #fdecec;
    color: #b42318;
}

.empty-state {
    display: grid;
    gap: 8px;
    padding: 28px;
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #f9fbff;
    color: #475467;
}

.empty-state strong {
    color: #172033;
}

@media (max-width: 760px) {
    .page-header {
        align-items: flex-start;
        flex-direction: column;
    }

    .summary-band,
    .detail-grid,
    .photo-grid {
        grid-template-columns: 1fr;
    }

    dl,
    .time-list {
        grid-template-columns: 1fr;
    }
}

@media print {
    .wash-detail-page {
        padding: 0;
    }

    .page-header,
    .summary-band,
    .detail-section:not(.receipt-section),
    .state-text,
    .error-text,
    .empty-state {
        display: none;
    }

    .detail-grid {
        display: block;
    }

    .receipt-section {
        border: 0;
        padding: 0;
    }
}
</style>
