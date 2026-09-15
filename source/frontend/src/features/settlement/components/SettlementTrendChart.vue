<template>
    <section class="chart-section">
        <div class="section-title">
            <div>
                <p>Chart</p>
                <h2>매출 및 회원 구분 추이</h2>
            </div>
            <span>막대를 선택하면 상세내역으로 이동합니다.</span>
        </div>

        <div
            v-if="points.length"
            class="chart-grid"
        >
            <article class="chart-panel">
                <h3>결제 완료 금액</h3>
                <div class="chart-scroll">
                    <div class="bar-chart">
                        <button
                            v-for="point in points"
                            :key="`paid-${point.periodLabel}`"
                            type="button"
                            :title="`${point.periodLabel} ${formatCurrency(point.totalPaidAmount)}`"
                            @click="$emit('select-period', point)"
                        >
                            <span class="bar-value">{{ compactAmount(point.totalPaidAmount) }}</span>
                            <span class="bar-track">
                                <span
                                    class="bar paid-bar"
                                    :style="{ height: `${paidHeight(point)}%` }"
                                />
                            </span>
                            <small>{{ shortLabel(point.periodLabel) }}</small>
                        </button>
                    </div>
                </div>
            </article>

            <article class="chart-panel">
                <div class="chart-panel-title">
                    <h3>회원 / 비회원 이용</h3>
                    <div class="legend">
                        <span><i class="member-color" />회원</span>
                        <span><i class="non-member-color" />비회원</span>
                    </div>
                </div>
                <div class="chart-scroll">
                    <div class="bar-chart">
                        <button
                            v-for="point in points"
                            :key="`use-${point.periodLabel}`"
                            type="button"
                            :title="`${point.periodLabel} 회원 ${point.memberUseCount || 0}건, 비회원 ${point.nonMemberUseCount || 0}건`"
                            @click="$emit('select-period', point)"
                        >
                            <span class="bar-value">{{ formatNumber(point.totalUseCount) }}건</span>
                            <span class="bar-track stacked-track">
                                <span
                                    class="bar member-bar"
                                    :style="{ height: `${memberHeight(point)}%` }"
                                />
                                <span
                                    class="bar non-member-bar"
                                    :style="{ height: `${nonMemberHeight(point)}%` }"
                                />
                            </span>
                            <small>{{ shortLabel(point.periodLabel) }}</small>
                        </button>
                    </div>
                </div>
            </article>
        </div>

        <p
            v-else
            class="empty-state"
        >
            선택한 기간에 표시할 추이 데이터가 없습니다.
        </p>
    </section>
</template>

<script setup>
import { computed } from "vue"

const props = defineProps({
    trends: {
        type: Array,
        default: () => []
    }
})

defineEmits(["select-period"])

const points = computed(() => props.trends || [])

const maxPaidAmount = computed(() => {
    return Math.max(1, ...points.value.map(point => Number(point.totalPaidAmount || 0)))
})

const maxUseCount = computed(() => {
    return Math.max(1, ...points.value.map(point => Number(point.totalUseCount || 0)))
})

function paidHeight(point) {
    return percentage(point.totalPaidAmount, maxPaidAmount.value)
}

function memberHeight(point) {
    return percentage(point.memberUseCount, maxUseCount.value)
}

function nonMemberHeight(point) {
    return percentage(point.nonMemberUseCount, maxUseCount.value)
}

function percentage(value, maxValue) {
    const numericValue = Number(value || 0)
    if (numericValue === 0) {
        return 0
    }

    return Math.max(4, numericValue * 100 / maxValue)
}

function shortLabel(value) {
    if (/^\d{4}-\d{2}-\d{2}$/.test(value || "")) {
        return value.slice(5).replace("-", ".")
    }

    return value || "-"
}

function compactAmount(value) {
    const amount = Number(value || 0)
    if (amount >= 10000) {
        return `${Math.round(amount / 10000)}만`
    }

    return formatNumber(amount)
}

function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(value || 0)
}

function formatCurrency(value) {
    return `${formatNumber(value)}원`
}
</script>

<style scoped>
.chart-section {
    display: grid;
    gap: 14px;
}

.section-title,
.chart-panel-title {
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

h2,
h3 {
    margin: 0;
    color: #0f172a;
}

h2 {
    font-size: 19px;
}

h3 {
    font-size: 15px;
}

.section-title > span {
    color: #64748b;
    font-size: 13px;
}

.chart-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
}

.chart-panel {
    display: grid;
    gap: 16px;
    min-width: 0;
    padding: 18px;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    background: #ffffff;
}

.chart-scroll {
    overflow-x: auto;
}

.bar-chart {
    display: grid;
    grid-auto-columns: minmax(52px, 1fr);
    grid-auto-flow: column;
    gap: 8px;
    min-width: max(100%, 560px);
    height: 250px;
}

.bar-chart button {
    display: grid;
    grid-template-rows: 24px 1fr 22px;
    gap: 5px;
    min-width: 0;
    padding: 0;
    border: 0;
    background: transparent;
    color: #344054;
    cursor: pointer;
}

.bar-chart button:hover .bar-track,
.bar-chart button:focus-visible .bar-track {
    background: #e8eef5;
}

.bar-chart button:focus-visible {
    outline: 2px solid #52749c;
    outline-offset: 2px;
}

.bar-value {
    overflow: hidden;
    font-size: 11px;
    font-weight: 800;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.bar-track {
    display: flex;
    align-items: flex-end;
    justify-content: center;
    overflow: hidden;
    border-radius: 4px 4px 0 0;
    background: #f1f5f9;
}

.stacked-track {
    flex-direction: column-reverse;
    justify-content: flex-start;
}

.bar {
    display: block;
    width: 72%;
    transition: height 180ms ease;
}

.paid-bar {
    background: #245f52;
}

.member-bar,
.non-member-bar {
    width: 72%;
}

.member-bar {
    background: #315f91;
}

.non-member-bar {
    background: #d58a36;
}

.bar-chart small {
    overflow: hidden;
    color: #64748b;
    font-size: 11px;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.legend {
    display: flex;
    gap: 10px;
    color: #64748b;
    font-size: 12px;
}

.legend span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
}

.legend i {
    width: 9px;
    height: 9px;
    border-radius: 2px;
}

.member-color {
    background: #315f91;
}

.non-member-color {
    background: #d58a36;
}

.empty-state {
    margin: 0;
    padding: 28px;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    color: #64748b;
    text-align: center;
}

@media (max-width: 980px) {
    .chart-grid {
        grid-template-columns: 1fr;
    }
}

@media (max-width: 700px) {
    .section-title,
    .chart-panel-title {
        align-items: flex-start;
        flex-direction: column;
    }
}
</style>
