<template>
    <section class="detail-section">
        <div class="section-title">
            <div>
                <p>Detail</p>
                <h2>{{ filterLabel }}</h2>
            </div>
            <span>{{ histories.length }}건</span>
        </div>

        <p
            v-if="loading"
            class="state-text"
        >
            세차 상세내역을 불러오는 중입니다.
        </p>

        <div
            v-else-if="histories.length"
            class="table-wrap"
        >
            <table>
                <thead>
                    <tr>
                        <th>이용번호</th>
                        <th>세차장</th>
                        <th>입차시간</th>
                        <th>차량번호</th>
                        <th>고객</th>
                        <th>코스</th>
                        <th>금액</th>
                        <th>결제상태</th>
                        <th>세차상태</th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="history in histories"
                        :key="history.washHistoryId"
                        tabindex="0"
                        @click="$emit('select-history', history.washHistoryId)"
                        @keydown.enter="$emit('select-history', history.washHistoryId)"
                    >
                        <td><strong>#{{ history.washHistoryId }}</strong></td>
                        <td>{{ history.siteName || `세차장 #${history.washSiteId}` }}</td>
                        <td>{{ formatDateTime(history.entryTime) }}</td>
                        <td>{{ history.plateNumber || "-" }}</td>
                        <td>{{ displayCustomer(history) }}</td>
                        <td>{{ history.courseName || `코스 #${history.courseId}` }}</td>
                        <td>{{ formatCurrency(history.amount) }}</td>
                        <td>
                            <span :class="['status-badge', paymentStatusClass(history.paymentStatus)]">
                                {{ displayPaymentStatus(history.paymentStatus) }}
                            </span>
                        </td>
                        <td>{{ displayWashStatus(history.washStatus) }}</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <p
            v-else
            class="empty-state"
        >
            선택한 조건에 해당하는 세차 상세내역이 없습니다.
        </p>
    </section>
</template>

<script setup>
defineProps({
    histories: {
        type: Array,
        default: () => []
    },
    filterLabel: {
        type: String,
        default: "조회 기간 전체"
    },
    loading: {
        type: Boolean,
        default: false
    }
})

defineEmits(["select-history"])

function displayCustomer(history) {
    if (history.customerType === "MEMBER") {
        return history.customerId ? `회원 #${history.customerId}` : "회원"
    }

    return "비회원"
}

function displayPaymentStatus(value) {
    const labels = {
        WAITING: "결제대기",
        SUCCESS: "결제완료",
        DONE: "결제완료",
        FAILED: "결제실패",
        CANCELED: "결제취소"
    }

    return labels[value] || value || "-"
}

function displayWashStatus(value) {
    const labels = {
        ENTERED: "입차",
        WASHING: "세차중",
        WASH_DONE: "세차완료",
        EXITED: "출차"
    }

    return labels[value] || value || "-"
}

function paymentStatusClass(value) {
    if (["SUCCESS", "DONE"].includes(value)) {
        return "status-success"
    }

    if (["FAILED", "CANCELED"].includes(value)) {
        return "status-error"
    }

    return "status-waiting"
}

function formatDateTime(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(0, 16)
}

function formatCurrency(value) {
    return `${new Intl.NumberFormat("ko-KR").format(value || 0)}원`
}
</script>

<style scoped>
.detail-section {
    display: grid;
    gap: 14px;
}

.section-title {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 12px;
}

.section-title p {
    margin: 0 0 6px;
    color: #5b728f;
    font-size: 12px;
    font-weight: 800;
    text-transform: uppercase;
}

h2 {
    margin: 0;
    color: #0f172a;
    font-size: 19px;
}

.section-title > span {
    color: #64748b;
    font-size: 13px;
}

.table-wrap {
    overflow-x: auto;
    border: 1px solid #e4ebf5;
    border-radius: 8px;
}

table {
    width: 100%;
    min-width: 1120px;
    border-collapse: collapse;
}

th,
td {
    padding: 12px 13px;
    border-bottom: 1px solid #edf2f7;
    text-align: left;
    white-space: nowrap;
}

th {
    background: #f6f8fb;
    color: #344054;
    font-size: 13px;
}

td {
    color: #26364a;
    font-size: 13px;
}

tbody tr {
    cursor: pointer;
}

tbody tr:hover,
tbody tr:focus-visible {
    background: #f2f7fb;
    outline: none;
}

tbody tr:last-child td {
    border-bottom: 0;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 800;
}

.status-success {
    background: #e8f5ef;
    color: #166534;
}

.status-waiting {
    background: #fff5df;
    color: #9a5a08;
}

.status-error {
    background: #fdecec;
    color: #b42318;
}

.state-text,
.empty-state {
    margin: 0;
    padding: 28px;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    color: #64748b;
    text-align: center;
}

@media (max-width: 700px) {
    .section-title {
        align-items: flex-start;
        flex-direction: column;
    }
}
</style>
