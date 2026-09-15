<template>
  <main class="reservation-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">WASH RESERVATION</p>
        <h1>세차 예약</h1>
        <p>세차장과 날짜를 선택한 뒤 가능한 시간을 예약하세요.</p>
      </div>
      <RouterLink class="history-link" :to="{ name: 'customer-reservations' }">
        내 예약 보기
      </RouterLink>
    </header>

    <div class="reservation-layout">
      <section class="booking-card">
        <div class="field-block">
          <label for="wash-site">세차장</label>
          <select id="wash-site" v-model="selectedSiteId">
            <option value="">세차장을 선택하세요</option>
            <option
              v-for="site in washSites"
              :key="site.washSiteId"
              :value="String(site.washSiteId)"
            >
              {{ site.siteName }}
            </option>
          </select>
          <div v-if="selectedSite" class="site-summary">
            <strong>{{ selectedSite.siteName }}</strong>
            <span>{{ selectedSite.address }}</span>
            <span>{{ selectedSite.courseName }} · {{ formatPrice(selectedSite.washPrice) }}</span>
          </div>
        </div>

        <section class="calendar" aria-label="예약 날짜 선택">
          <header class="calendar-header">
            <button
              type="button"
              aria-label="이전 달"
              :disabled="!canMovePreviousMonth"
              @click="moveMonth(-1)"
            >
              ‹
            </button>
            <strong>{{ calendarTitle }}</strong>
            <button
              type="button"
              aria-label="다음 달"
              :disabled="!canMoveNextMonth"
              @click="moveMonth(1)"
            >
              ›
            </button>
          </header>

          <div class="weekday-row" aria-hidden="true">
            <span class="sunday">일</span>
            <span>월</span><span>화</span><span>수</span><span>목</span><span>금</span>
            <span>토</span>
          </div>

          <div class="calendar-grid">
            <button
              v-for="day in calendarDays"
              :key="day.key"
              type="button"
              :class="{
                outside: !day.currentMonth,
                sunday: day.dayOfWeek === 0,
                today: day.key === todayKey,
                selected: day.key === selectedDate,
                unavailable: !day.selectable
              }"
              :disabled="!day.selectable"
              :aria-label="`${day.key}${day.key === todayKey ? ' 오늘' : ''}`"
              @click="selectDate(day.key)"
            >
              <span>{{ day.date.getDate() }}</span>
              <small v-if="day.key === todayKey">오늘</small>
            </button>
          </div>
        </section>

        <section class="slot-section" aria-live="polite">
          <div class="section-title">
            <div>
              <p class="eyebrow">AVAILABLE TIME</p>
              <h2>{{ selectedDateLabel }}</h2>
            </div>
            <span class="slot-guide">30분 단위</span>
          </div>

          <p v-if="slotLoading" class="state-message">예약 가능한 시간을 확인하고 있습니다.</p>
          <p v-else-if="!selectedSiteId" class="state-message">세차장을 먼저 선택해 주세요.</p>
          <p v-else-if="slotGroups.length === 0" class="state-message">
            선택한 날짜에 예약 가능한 시간이 없습니다.
          </p>

          <template v-else>
            <div v-if="morningSlots.length" class="time-period">
              <h3>오전</h3>
              <div class="time-grid">
                <button
                  v-for="slot in morningSlots"
                  :key="slot.key"
                  type="button"
                  :class="{ selected: selectedSlotKey === slot.key }"
                  @click="selectSlot(slot)"
                >
                  <strong>{{ formatSlotTime(slot.startTime) }}</strong>
                  <small>{{ slot.devices.length }}대 가능</small>
                </button>
              </div>
            </div>

            <div v-if="afternoonSlots.length" class="time-period">
              <h3>오후</h3>
              <div class="time-grid">
                <button
                  v-for="slot in afternoonSlots"
                  :key="slot.key"
                  type="button"
                  :class="{ selected: selectedSlotKey === slot.key }"
                  @click="selectSlot(slot)"
                >
                  <strong>{{ formatSlotTime(slot.startTime) }}</strong>
                  <small>{{ slot.devices.length }}대 가능</small>
                </button>
              </div>
            </div>
          </template>
        </section>
      </section>

      <aside class="summary-card">
        <div>
          <p class="eyebrow">BOOKING DETAILS</p>
          <h2>예약 정보</h2>
        </div>

        <div class="field-block">
          <label for="vehicle">예약 차량</label>
          <select id="vehicle" v-model="selectedVehicleId">
            <option value="">차량을 선택하세요</option>
            <option
              v-for="vehicle in vehicles"
              :key="vehicle.vehicleId"
              :value="String(vehicle.vehicleId)"
            >
              {{ vehicle.plateNumber }} · {{ displayVehicleType(vehicle.vehicleType) }}
            </option>
          </select>
          <RouterLink
            v-if="!loading && vehicles.length === 0"
            class="inline-link"
            :to="{ name: 'customer-vehicles-registration' }"
          >
            예약 전에 차량을 등록해 주세요.
          </RouterLink>
        </div>

        <dl class="booking-summary">
          <div><dt>세차장</dt><dd>{{ selectedSite?.siteName || "-" }}</dd></div>
          <div><dt>예약일</dt><dd>{{ selectedDateLabel }}</dd></div>
          <div><dt>시간</dt><dd>{{ selectedTimeRange }}</dd></div>
          <div><dt>코스</dt><dd>{{ selectedSite?.courseName || "-" }}</dd></div>
          <div class="price-row"><dt>현장 결제 금액</dt><dd>{{ formatPrice(selectedSite?.washPrice) }}</dd></div>
        </dl>

        <p class="payment-notice">
          결제는 예약 시점이 아니라 세차 카메라가 차량을 인식한 후 진행됩니다.
        </p>
        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

        <button
          class="submit-button"
          type="button"
          :disabled="!canSubmit || submitting"
          @click="submitReservation"
        >
          {{ submitting ? "예약 처리 중" : "예약 확정" }}
        </button>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue"
import { useRouter } from "vue-router"
import * as vehicleApi from "@/features/vehicle/api/vehicleApi"
import * as reservationApi from "../api/reservationApi"

const router = useRouter()
const today = startOfDay(new Date())
const maxDate = addDays(today, 7)

const washSites = ref([])
const vehicles = ref([])
const availableSlots = ref([])
const selectedSiteId = ref("")
const selectedVehicleId = ref("")
const selectedDate = ref(toDateKey(today))
const selectedSlotKey = ref("")
const calendarMonth = ref(new Date(today.getFullYear(), today.getMonth(), 1))
const loading = ref(true)
const slotLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref("")

const selectedSite = computed(() =>
  washSites.value.find(site => String(site.washSiteId) === selectedSiteId.value) || null
)

const slotGroups = computed(() => {
  const groups = new Map()

  for (const slot of availableSlots.value) {
    const key = String(slot.startTime || "").slice(0, 16)
    if (!key) continue
    if (!groups.has(key)) {
      groups.set(key, { key, startTime: slot.startTime, endTime: slot.endTime, devices: [] })
    }
    groups.get(key).devices.push({ deviceId: slot.deviceId, deviceName: slot.deviceName })
  }

  return [...groups.values()].sort((a, b) => a.key.localeCompare(b.key))
})

const morningSlots = computed(() => slotGroups.value.filter(slot => getHour(slot.startTime) < 12))
const afternoonSlots = computed(() => slotGroups.value.filter(slot => getHour(slot.startTime) >= 12))
const selectedSlot = computed(() =>
  slotGroups.value.find(slot => slot.key === selectedSlotKey.value) || null
)
const canSubmit = computed(() => Boolean(
  selectedSite.value?.courseId
  && selectedVehicleId.value
  && selectedSlot.value
))
const selectedTimeRange = computed(() => {
  if (!selectedSlot.value) return "-"
  return `${format24Hour(selectedSlot.value.startTime)} ~ ${format24Hour(selectedSlot.value.endTime)}`
})
const selectedDateLabel = computed(() => {
  const date = fromDateKey(selectedDate.value)
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`
})
const todayKey = toDateKey(today)
const maxDateKey = toDateKey(maxDate)
const calendarTitle = computed(() =>
  `${calendarMonth.value.getFullYear()}년 ${calendarMonth.value.getMonth() + 1}월`
)
const calendarDays = computed(() => buildCalendarDays(calendarMonth.value))
const canMovePreviousMonth = computed(() => monthKey(calendarMonth.value) > monthKey(today))
const canMoveNextMonth = computed(() => monthKey(calendarMonth.value) < monthKey(maxDate))

watch([selectedSiteId, selectedDate], loadAvailableSlots)

onMounted(async () => {
  loading.value = true
  errorMessage.value = ""
  try {
    const [siteResponse, vehicleResponse] = await Promise.all([
      reservationApi.getCustomerWashSites(),
      vehicleApi.vehicleList()
    ])
    washSites.value = Array.isArray(siteResponse) ? siteResponse : []
    vehicles.value = Array.isArray(vehicleResponse) ? vehicleResponse : []
    if (washSites.value.length) selectedSiteId.value = String(washSites.value[0].washSiteId)
    if (vehicles.value.length) selectedVehicleId.value = String(vehicles.value[0].vehicleId)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, "예약 정보를 불러오지 못했습니다.")
  } finally {
    loading.value = false
  }
})

async function loadAvailableSlots() {
  availableSlots.value = []
  selectedSlotKey.value = ""
  errorMessage.value = ""
  if (!selectedSiteId.value || !selectedDate.value) return

  slotLoading.value = true
  try {
    const response = await reservationApi.getAvailableSlots(
      Number(selectedSiteId.value),
      selectedDate.value
    )
    availableSlots.value = Array.isArray(response) ? response : []
  } catch (error) {
    errorMessage.value = getErrorMessage(error, "예약 가능한 시간을 불러오지 못했습니다.")
  } finally {
    slotLoading.value = false
  }
}

function selectDate(dateKey) {
  selectedDate.value = dateKey
}

function selectSlot(slot) {
  selectedSlotKey.value = slot.key
}

async function submitReservation() {
  if (!canSubmit.value) return
  submitting.value = true
  errorMessage.value = ""
  try {
    await reservationApi.createReservation({
      vehicleId: Number(selectedVehicleId.value),
      washSiteId: Number(selectedSiteId.value),
      courseId: Number(selectedSite.value.courseId),
      startTime: String(selectedSlot.value.startTime).slice(0, 19)
    })
    await router.push({ name: "customer-reservations", query: { created: "true" } })
  } catch (error) {
    const message = getErrorMessage(error, "예약을 완료하지 못했습니다.")
    await loadAvailableSlots()
    errorMessage.value = message
  } finally {
    submitting.value = false
  }
}

function moveMonth(offset) {
  calendarMonth.value = new Date(
    calendarMonth.value.getFullYear(),
    calendarMonth.value.getMonth() + offset,
    1
  )
}

function buildCalendarDays(month) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const gridStart = addDays(first, -first.getDay())
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(gridStart, index)
    const key = toDateKey(date)
    return {
      key,
      date,
      dayOfWeek: date.getDay(),
      currentMonth: date.getMonth() === month.getMonth(),
      selectable: key >= todayKey && key <= maxDateKey
    }
  })
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}
function addDays(date, days) {
  const copy = new Date(date)
  copy.setDate(copy.getDate() + days)
  return copy
}
function toDateKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}
function fromDateKey(key) {
  const [year, month, day] = key.split("-").map(Number)
  return new Date(year, month - 1, day)
}
function monthKey(date) {
  return date.getFullYear() * 12 + date.getMonth()
}
function getHour(value) {
  return Number(String(value || "").slice(11, 13))
}
function formatSlotTime(value) {
  const hour = getHour(value)
  const minute = String(value || "").slice(14, 16)
  return `${hour % 12 || 12}:${minute}`
}
function format24Hour(value) {
  return String(value || "").slice(11, 16) || "-"
}
function formatPrice(value) {
  if (value == null) return "-"
  return `${Number(value).toLocaleString("ko-KR")}원`
}
function displayVehicleType(value) {
  return { SEDAN: "세단", SUV: "SUV", TRUCK: "트럭", HATCHBACK: "해치백", VAN: "승합차" }[value] || value || "차량"
}
function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.response?.data?.detail || fallback
}
</script>

<style scoped>
.reservation-page { display: grid; gap: 20px; color: #172033; text-align: left; }
.page-heading, .booking-card, .summary-card { border: 1px solid #dce5f1; border-radius: 14px; background: #fff; box-shadow: 0 12px 32px rgba(7,27,58,.06); }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 28px 32px; }
.eyebrow { margin: 0 0 5px; color: #177245; font-size: 11px; font-weight: 900; letter-spacing: .08em; }
.page-heading h1, .summary-card h2, .section-title h2 { margin: 0; color: #071b3a; font-weight: 900; }
.page-heading h1 { font-size: 32px; }
.page-heading > div > p:last-child { margin-top: 8px; color: #64748b; font-size: 14px; }
.history-link, .submit-button { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; padding: 0 18px; border: 0; border-radius: 9px; color: #fff; background: #071b3a; font-weight: 900; text-decoration: none; cursor: pointer; }
.reservation-layout { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(300px, .75fr); gap: 20px; align-items: start; }
.booking-card, .summary-card { padding: 26px; }
.summary-card { position: sticky; top: 92px; display: grid; gap: 22px; }
.field-block { display: grid; gap: 9px; }
.field-block label, .field-label { color: #263b58; font-size: 13px; font-weight: 900; }
select { width: 100%; min-height: 46px; padding: 0 13px; border: 1px solid #ccd7e5; border-radius: 9px; color: #172033; background: #fff; font: inherit; font-size: 14px; }
.site-summary { display: grid; gap: 2px; padding: 13px 15px; border-radius: 9px; color: #64748b; background: #f5f8fc; font-size: 12px; }
.site-summary strong { color: #071b3a; font-size: 14px; }
.calendar { margin-top: 24px; padding-top: 20px; border-top: 1px solid #edf1f6; }
.calendar-header { display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; margin-bottom: 15px; text-align: center; }
.calendar-header strong { color: #071b3a; font-size: 18px; }
.calendar-header button { width: 36px; height: 36px; border: 1px solid #dbe3ed; border-radius: 50%; color: #334155; background: #fff; font-size: 24px; cursor: pointer; }
.calendar-header button:disabled { opacity: .25; cursor: default; }
.weekday-row, .calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0,1fr)); text-align: center; }
.weekday-row { margin-bottom: 5px; color: #8794a6; font-size: 12px; font-weight: 800; }
.weekday-row span { padding: 5px 0; }
.calendar-grid { gap: 4px 2px; }
.calendar-grid button { display: grid; place-content: center; gap: 1px; min-height: 46px; border: 0; border-radius: 9px; color: #172033; background: transparent; cursor: pointer; }
.calendar-grid button:hover:not(:disabled) { background: #edf8f2; }
.calendar-grid button.selected { color: #fff; background: #08a45c; font-weight: 900; }
.calendar-grid button.today:not(.selected) { color: #07884c; border: 1px solid #08a45c; font-weight: 900; }
.calendar-grid button.sunday:not(.selected) { color: #ef4444; }
.calendar-grid button.outside, .calendar-grid button.unavailable { color: #c4cbd4; }
.calendar-grid small { font-size: 9px; font-weight: 900; }
.slot-section { margin-top: 26px; padding-top: 24px; border-top: 1px solid #e8edf3; }
.section-title { display: flex; align-items: end; justify-content: space-between; gap: 15px; margin-bottom: 18px; }
.section-title h2 { font-size: 18px; }
.slot-guide { color: #64748b; font-size: 12px; }
.state-message { padding: 34px 15px; border-radius: 9px; color: #64748b; background: #f7f9fc; font-size: 13px; text-align: center; }
.time-period + .time-period { margin-top: 20px; }
.time-period h3 { margin: 0 0 10px; color: #475569; font-size: 14px; }
.time-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 9px; }
.time-grid button { display: grid; place-content: center; gap: 2px; min-height: 51px; border: 1px solid #dce2e9; border-radius: 9px; color: #1f2937; background: #fff; cursor: pointer; }
.time-grid button:hover { border-color: #08a45c; }
.time-grid button.selected { border-color: #08a45c; color: #fff; background: #08a45c; box-shadow: 0 8px 18px rgba(8,164,92,.2); }
.time-grid strong { font-size: 14px; font-weight: 700; }
.time-grid small { font-size: 10px; opacity: .8; }
.booking-summary { display: grid; gap: 0; margin: 0; border-top: 1px solid #e6ebf2; }
.booking-summary div { display: grid; grid-template-columns: 105px 1fr; gap: 12px; padding: 12px 0; border-bottom: 1px solid #edf1f5; }
.booking-summary dt, .booking-summary dd { margin: 0; }
.booking-summary dt { color: #64748b; font-size: 12px; }
.booking-summary dd { color: #172033; font-size: 13px; font-weight: 800; text-align: right; }
.booking-summary .price-row dd { color: #07884c; font-size: 17px; }
.payment-notice { padding: 12px; border-radius: 8px; color: #53657b; background: #f4f7fb; font-size: 12px; line-height: 1.55; }
.inline-link { color: #176e4a; font-size: 12px; font-weight: 800; }
.error-message { padding: 11px 12px; border-radius: 8px; color: #b42318; background: #fff1f1; font-size: 12px; font-weight: 800; }
.submit-button { width: 100%; background: #08a45c; }
.submit-button:disabled { background: #cbd5df; cursor: not-allowed; }
@media (max-width: 900px) { .reservation-layout { grid-template-columns: 1fr; } .summary-card { position: static; } }
@media (max-width: 560px) { .page-heading { align-items: stretch; flex-direction: column; padding: 22px 18px; } .booking-card, .summary-card { padding: 18px 14px; } .time-grid { grid-template-columns: repeat(4, minmax(0,1fr)); gap: 7px; } .time-grid button { min-height: 48px; } .calendar-grid button { min-height: 42px; } }
</style>
