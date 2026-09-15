<template>
    <section class="summary-grid">
        <button
            v-for="item in summaryItems"
            :key="item.type"
            class="metric-card"
            type="button"
            @click="emitDrilldown(item.type, item.label)"
        >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>내역 보기</small>
        </button>

        <article class="metric-card member-card">
            <span>회원 / 비회원</span>
            <div class="member-actions">
                <button
                    type="button"
                    @click="emitDrilldown('MEMBER', '회원 이용')"
                >
                    <small>회원</small>
                    <strong>{{ formatNumber(dashboard.memberUseCount) }}</strong>
                </button>
                <button
                    type="button"
                    @click="emitDrilldown('NON_MEMBER', '비회원 이용')"
                >
                    <small>비회원</small>
                    <strong>{{ formatNumber(dashboard.nonMemberUseCount) }}</strong>
                </button>
            </div>
        </article>

        <button
            class="metric-card waiting-card"
            type="button"
            @click="emitDrilldown('WAITING', '결제 대기')"
        >
            <span>결제 대기</span>
            <strong>{{ formatNumber(dashboard.waitingPaymentCount) }}건</strong>
            <small>대기 차량 보기</small>
        </button>
    </section>
</template>

<script setup>
import { computed } from "vue"

const props = defineProps({
    dashboard: {
        type: Object,
        required: true
    }
})

const emit = defineEmits(["drilldown"])

const summaryItems = computed(() => [
    {
        type: "ALL",
        label: "총 이용 건수",
        value: `${formatNumber(props.dashboard.totalUseCount)}건`
    },
    {
        type: "CUSTOMERS",
        label: "이용 고객 수",
        value: `${formatNumber(props.dashboard.uniqueCustomerCount)}명`
    },
    {
        type: "USE_AMOUNT",
        label: "총 이용 금액",
        value: formatCurrency(props.dashboard.totalUseAmount)
    },
    {
        type: "PAID",
        label: "총 결제 금액",
        value: formatCurrency(props.dashboard.totalPaidAmount)
    }
])

function emitDrilldown(type, label) {
    emit("drilldown", { type, label })
}

function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(value || 0)
}

function formatCurrency(value) {
    return `${formatNumber(value)}원`
}
</script>

<style scoped>
.summary-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
}

.metric-card {
    display: grid;
    gap: 10px;
    min-height: 128px;
    padding: 18px;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    background: #ffffff;
    color: #172033;
    text-align: left;
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

button.metric-card {
    cursor: pointer;
}

button.metric-card:hover,
button.metric-card:focus-visible {
    border-color: #52749c;
    background: #f8fbff;
    outline: none;
}

.metric-card > span {
    color: #64748b;
    font-size: 13px;
    font-weight: 800;
}

.metric-card > strong {
    align-self: center;
    font-size: 24px;
    font-weight: 900;
}

.metric-card > small {
    align-self: end;
    color: #5b728f;
    font-size: 12px;
    font-weight: 700;
}

.member-card {
    align-content: start;
}

.member-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
}

.member-actions button {
    display: grid;
    gap: 6px;
    min-height: 70px;
    padding: 11px;
    border: 1px solid #dfe7f2;
    border-radius: 6px;
    background: #f8fafc;
    color: #172033;
    text-align: left;
    cursor: pointer;
}

.member-actions button:hover,
.member-actions button:focus-visible {
    border-color: #52749c;
    background: #eef5fb;
    outline: none;
}

.member-actions small {
    color: #64748b;
    font-weight: 800;
}

.member-actions strong {
    font-size: 20px;
}

.waiting-card strong {
    color: #9a5a08;
}

@media (max-width: 980px) {
    .summary-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}

@media (max-width: 620px) {
    .summary-grid {
        grid-template-columns: 1fr;
    }
}
</style>
