<template>
    <section class="data-section">
        <div class="section-title">
            <div>
                <p>COMPARE</p>
                <h2>세차장별 비교</h2>
            </div>
            <span>{{ comparisons.length }}개 세차장</span>
        </div>

        <p
            v-if="loading"
            class="state-text"
        >
            세차장별 비교 데이터를 불러오는 중입니다.
        </p>

        <div
            v-else-if="comparisons.length"
            class="table-wrap"
        >
            <table>
                <thead>
                    <tr>
                        <th>순위</th>
                        <th>세차장</th>
                        <th>사업주</th>
                        <th>총 이용</th>
                        <th>이용 고객</th>
                        <th>회원</th>
                        <th>비회원</th>
                        <th>이용 금액</th>
                        <th>결제 금액</th>
                        <th>결제 대기</th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="(site, index) in comparisons"
                        :key="site.washSiteId"
                        :class="{ leader: index === 0 && site.totalUseCount > 0 }"
                    >
                        <td>{{ index + 1 }}</td>
                        <td><strong>{{ site.siteName || `세차장 #${site.washSiteId}` }}</strong></td>
                        <td>{{ site.ownerName || "-" }}</td>
                        <td>{{ formatNumber(site.totalUseCount) }}건</td>
                        <td>{{ formatNumber(site.uniqueCustomerCount) }}명</td>
                        <td>{{ formatNumber(site.memberUseCount) }}건</td>
                        <td>{{ formatNumber(site.nonMemberUseCount) }}건</td>
                        <td>{{ formatCurrency(site.totalUseAmount) }}</td>
                        <td>{{ formatCurrency(site.totalPaidAmount) }}</td>
                        <td>{{ formatNumber(site.waitingPaymentCount) }}건</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <p
            v-else
            class="empty-state"
        >
            선택한 조건에 해당하는 세차장이 없습니다.
        </p>
    </section>
</template>

<script setup>
defineProps({
    comparisons: {
        type: Array,
        default: () => []
    },
    loading: {
        type: Boolean,
        default: false
    }
})

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
    min-width: 980px;
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

tbody tr.leader {
    background: #f4fbf8;
}

tbody tr:last-child td {
    border-bottom: 0;
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
</style>
