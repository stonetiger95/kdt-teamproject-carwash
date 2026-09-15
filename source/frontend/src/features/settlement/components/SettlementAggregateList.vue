<template>
    <section class="data-section">
        <div class="section-title">
            <div>
                <p>Aggregate</p>
                <h2>기간별 집계 목록</h2>
            </div>
            <span>{{ trends.length }}개 구간</span>
        </div>

        <div
            v-if="trends.length"
            class="table-wrap"
        >
            <table>
                <thead>
                    <tr>
                        <th>기간</th>
                        <th>총 이용</th>
                        <th>회원</th>
                        <th>비회원</th>
                        <th>이용 금액</th>
                        <th>결제 금액</th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="trend in trends"
                        :key="trend.periodLabel"
                        tabindex="0"
                        @click="$emit('select-period', trend)"
                        @keydown.enter="$emit('select-period', trend)"
                    >
                        <td><strong>{{ trend.periodLabel }}</strong></td>
                        <td>{{ formatNumber(trend.totalUseCount) }}건</td>
                        <td>{{ formatNumber(trend.memberUseCount) }}건</td>
                        <td>{{ formatNumber(trend.nonMemberUseCount) }}건</td>
                        <td>{{ formatCurrency(trend.totalUseAmount) }}</td>
                        <td>{{ formatCurrency(trend.totalPaidAmount) }}</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <p
            v-else
            class="empty-state"
        >
            선택한 기간에 집계할 이용내역이 없습니다.
        </p>
    </section>
</template>

<script setup>
defineProps({
    trends: {
        type: Array,
        default: () => []
    }
})

defineEmits(["select-period"])

function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(value || 0)
}

function formatCurrency(value) {
    return `${formatNumber(value)}원`
}
</script>

<style scoped>
.data-section {
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
    min-width: 760px;
    border-collapse: collapse;
}

th,
td {
    padding: 13px 14px;
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
    font-size: 14px;
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

.empty-state {
    margin: 0;
    padding: 28px;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    color: #64748b;
    text-align: center;
}
</style>
