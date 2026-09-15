<template>
    <section class="calendar-section">
        <div class="section-title">
            <div>
                <p>Calendar</p>
                <h2>{{ calendarTitle }}</h2>
            </div>
            <span>금액은 결제 완료 건 기준</span>
        </div>

        <div class="calendar-wrap">
            <div class="calendar-grid calendar-header">
                <span
                    v-for="day in weekdays"
                    :key="day"
                >
                    {{ day }}
                </span>
                <strong>주간 합계</strong>
            </div>

            <div
                v-for="week in calendarWeeks"
                :key="week.key"
                class="calendar-grid calendar-week"
            >
                <template
                    v-for="(day, index) in week.days"
                    :key="day?.date || `${week.key}-${index}`"
                >
                    <button
                        v-if="day"
                        class="day-cell"
                        type="button"
                        @click="$emit('select-date', day.date)"
                    >
                        <span class="day-number">{{ day.dayNumber }}</span>
                        <strong>{{ formatCurrency(day.paidAmount) }}</strong>
                        <small>{{ formatNumber(day.useCount) }}건</small>
                    </button>
                    <span
                        v-else
                        class="day-cell empty-cell"
                    />
                </template>

                <button
                    class="week-total"
                    type="button"
                    :disabled="!week.startDate"
                    @click="$emit('select-week', week)"
                >
                    <span>{{ week.label }}</span>
                    <strong>{{ formatCurrency(week.paidAmount) }}</strong>
                    <small>{{ formatNumber(week.useCount) }}건</small>
                </button>
            </div>
        </div>
    </section>
</template>

<script setup>
import { computed } from "vue"

const props = defineProps({
    histories: {
        type: Array,
        default: () => []
    },
    startDate: {
        type: String,
        required: true
    }
})

defineEmits(["select-date", "select-week"])

const weekdays = ["월", "화", "수", "목", "금", "토", "일"]

const monthDate = computed(() => parseDate(props.startDate) || new Date())

const calendarTitle = computed(() => {
    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "long"
    }).format(monthDate.value)
})

const dailyTotals = computed(() => {
    const totals = new Map()

    props.histories.forEach(history => {
        const date = String(history.entryTime || "").slice(0, 10)
        if (!date) {
            return
        }

        const current = totals.get(date) || {
            useCount: 0,
            paidAmount: 0
        }

        current.useCount += 1
        if (["SUCCESS", "DONE"].includes(history.paymentStatus)) {
            current.paidAmount += Number(history.amount || 0)
        }

        totals.set(date, current)
    })

    return totals
})

const calendarWeeks = computed(() => {
    const year = monthDate.value.getFullYear()
    const month = monthDate.value.getMonth()
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7
    const cells = Array(firstWeekday).fill(null)

    for (let day = 1; day <= daysInMonth; day += 1) {
        const date = toDateKey(new Date(year, month, day))
        const totals = dailyTotals.value.get(date) || {
            useCount: 0,
            paidAmount: 0
        }

        cells.push({
            date,
            dayNumber: day,
            ...totals
        })
    }

    while (cells.length % 7 !== 0) {
        cells.push(null)
    }

    const weeks = []
    for (let index = 0; index < cells.length; index += 7) {
        const days = cells.slice(index, index + 7)
        const actualDays = days.filter(Boolean)
        weeks.push({
            key: `week-${index / 7}`,
            label: `${index / 7 + 1}주차`,
            days,
            startDate: actualDays[0]?.date || "",
            endDate: actualDays.at(-1)?.date || "",
            useCount: actualDays.reduce((sum, day) => sum + day.useCount, 0),
            paidAmount: actualDays.reduce((sum, day) => sum + day.paidAmount, 0)
        })
    }

    return weeks
})

function parseDate(value) {
    if (!value) {
        return null
    }

    const [year, month, day] = value.split("-").map(Number)
    if (!year || !month || !day) {
        return null
    }

    return new Date(year, month - 1, day)
}

function toDateKey(date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")
    return `${year}-${month}-${day}`
}

function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(value || 0)
}

function formatCurrency(value) {
    return `${formatNumber(value)}원`
}
</script>

<style scoped>
.calendar-section {
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
    font-size: 20px;
}

.section-title > span {
    color: #64748b;
    font-size: 13px;
}

.calendar-wrap {
    overflow-x: auto;
    border: 1px solid #dfe7f2;
    border-radius: 8px;
    background: #ffffff;
}

.calendar-grid {
    display: grid;
    grid-template-columns: repeat(7, minmax(105px, 1fr)) minmax(130px, 1.1fr);
    min-width: 940px;
}

.calendar-header {
    border-bottom: 1px solid #dfe7f2;
    background: #f5f8fb;
}

.calendar-header span,
.calendar-header strong {
    padding: 11px;
    color: #475467;
    font-size: 13px;
    text-align: center;
}

.calendar-header span:nth-child(6) {
    color: #2563a7;
}

.calendar-header span:nth-child(7) {
    color: #b42318;
}

.calendar-week + .calendar-week {
    border-top: 1px solid #edf2f7;
}

.day-cell,
.week-total {
    display: grid;
    align-content: space-between;
    gap: 8px;
    min-height: 112px;
    padding: 11px;
    border: 0;
    border-right: 1px solid #edf2f7;
    background: #ffffff;
    color: #172033;
    text-align: left;
}

button.day-cell,
.week-total {
    cursor: pointer;
}

button.day-cell:hover,
button.day-cell:focus-visible,
.week-total:hover:not(:disabled),
.week-total:focus-visible:not(:disabled) {
    background: #eef5fb;
    outline: none;
}

.empty-cell {
    background: #fafbfc;
}

.day-number {
    color: #64748b;
    font-size: 13px;
    font-weight: 900;
}

.day-cell strong,
.week-total strong {
    font-size: 15px;
}

.day-cell small,
.week-total small {
    color: #64748b;
    font-weight: 700;
}

.week-total {
    border-right: 0;
    background: #f7fafc;
}

.week-total > span {
    color: #47627f;
    font-size: 12px;
    font-weight: 900;
}

.week-total:disabled {
    cursor: default;
}

@media (max-width: 700px) {
    .section-title {
        align-items: flex-start;
        flex-direction: column;
    }
}
</style>
