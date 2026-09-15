<template>
  <main class="reservation-page">
    <header class="page-header">
      <div class="header-copy">
        <p class="eyebrow">GUEST RESERVATION</p>
        <h1>비회원 세차 예약</h1>
        <p>날짜와 시간을 고른 뒤 사전결제를 완료하면 예약이 확정됩니다.</p>
      </div>
      <div class="header-actions">
        <button class="back-button" type="button" @click="goBack">
          ← 뒤로가기
        </button>
        <RouterLink :to="{ name: 'non-member-reservation-lookup' }">
          예약 조회
        </RouterLink>
      </div>
    </header>

    <section v-if="createdReservation" class="result-card">
      <p class="eyebrow">RESERVATION CREATED</p>
      <h2>예약이 임시로 생성되었습니다.</h2>
      <p>예약번호와 예약 당시 휴대전화 번호로 조회할 수 있습니다.</p>
      <dl>
        <div><dt>예약번호</dt><dd>{{ createdReservation.reservationId }}</dd></div>
        <div><dt>결제금액</dt><dd>{{ formatPrice(createdReservation.reservationPrice) }}</dd></div>
        <div><dt>결제기한</dt><dd>{{ formatDateTime(createdReservation.paymentExpiresAt) }}</dd></div>
      </dl>
      <p class="notice warning">결제 완료 전까지 예약은 확정되지 않습니다.</p>
      <button class="primary-button" type="button" :disabled="paying" @click="startPayment">
        {{ paying ? "결제 준비 중" : "사전결제 진행" }}
      </button>
      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    </section>

    <div v-else class="reservation-layout">
      <section class="schedule-card">
        <label class="field">
          <span>세차장</span>
          <select v-model="selectedSiteId">
            <option value="">세차장을 선택해 주세요</option>
            <option v-for="site in washSites" :key="site.washSiteId" :value="String(site.washSiteId)">
              {{ site.siteName }} · {{ site.courseName }}
            </option>
          </select>
        </label>

        <div v-if="selectedSite" class="site-summary">
          <strong>{{ selectedSite.siteName }}</strong>
          <span>{{ selectedSite.address }}</span>
          <span>{{ selectedSite.courseName }} · {{ formatPrice(selectedSite.washPrice) }}</span>
        </div>

        <div class="calendar">
          <div class="calendar-title">
            <button type="button" aria-label="이전 달" @click="moveMonth(-1)">‹</button>
            <strong>{{ calendarYear }}년 {{ calendarMonth + 1 }}월</strong>
            <button type="button" aria-label="다음 달" @click="moveMonth(1)">›</button>
          </div>
          <div class="week-row">
            <span v-for="day in weekLabels" :key="day">{{ day }}</span>
          </div>
          <div class="date-grid">
            <button
              v-for="day in calendarDays"
              :key="day.key"
              type="button"
              :disabled="!day.selectable"
              :class="{ selected: day.key === selectedDate, muted: !day.currentMonth, today: day.key === todayKey }"
              @click="selectedDate = day.key"
            >
              <span>{{ day.day }}</span>
              <small v-if="day.key === todayKey">오늘</small>
            </button>
          </div>
        </div>

        <div class="time-section">
          <div class="section-title">
            <strong>예약 시간</strong>
            <span>30분 단위</span>
          </div>
          <p v-if="slotLoading" class="empty-state">예약 가능 시간을 확인하고 있습니다.</p>
          <p v-else-if="slotGroups.length === 0" class="empty-state">선택한 날짜에 예약 가능한 시간이 없습니다.</p>
          <div v-else class="time-grid">
            <button
              v-for="slot in slotGroups"
              :key="slot.key"
              type="button"
              :class="{ selected: selectedSlotKey === slot.key }"
              @click="selectSlot(slot)"
            >
              <strong>{{ formatTime(slot.startTime) }}</strong>
              <small>{{ slot.devices.length }}대 가능</small>
            </button>
          </div>
        </div>
      </section>

      <aside class="form-card">
        <div>
          <p class="eyebrow">CUSTOMER DETAILS</p>
          <h2>예약자 정보</h2>
        </div>
        <label class="field">
          <span>차량번호</span>
          <input v-model.trim="plateNumber" maxlength="20" placeholder="예: 01고8819">
        </label>
        <label class="field">
          <span>휴대전화 번호</span>
          <input v-model.trim="phone" inputmode="tel" maxlength="20" placeholder="예: 01012345678">
        </label>
        <dl class="summary">
          <div><dt>세차장</dt><dd>{{ selectedSite?.siteName || "-" }}</dd></div>
          <div><dt>예약시간</dt><dd>{{ selectedSlot ? formatDateTime(selectedSlot.startTime) : "-" }}</dd></div>
          <div><dt>결제금액</dt><dd>{{ formatPrice(selectedSite?.washPrice) }}</dd></div>
        </dl>
        <p class="notice">비회원 예약은 사전결제를 완료해야 확정됩니다.</p>
        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        <button class="primary-button" type="button" :disabled="!canSubmit || submitting" @click="submitReservation">
          {{ submitting ? "예약 생성 중" : "예약하고 결제하기" }}
        </button>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue"
import { useRouter } from "vue-router"
import * as api from "../api/nonMemberReservationApi"
import { saveReservationAccess } from "../utils/nonMemberReservationAccess"

const router = useRouter()

const now = new Date()
const todayKey = toDateKey(now)
const maxDateKey = toDateKey(addDays(now, 7))
const weekLabels = ["일", "월", "화", "수", "목", "금", "토"]
const washSites = ref([])
const availableSlots = ref([])
const selectedSiteId = ref("")
const selectedDate = ref(todayKey)
const selectedSlotKey = ref("")
const plateNumber = ref("")
const phone = ref("")
const slotLoading = ref(false)
const submitting = ref(false)
const paying = ref(false)
const errorMessage = ref("")
const createdReservation = ref(null)
const calendarCursor = ref(new Date(now.getFullYear(), now.getMonth(), 1))

function goBack() {
  router.push({ name: "home" })
}

const calendarYear = computed(() => calendarCursor.value.getFullYear())
const calendarMonth = computed(() => calendarCursor.value.getMonth())
const selectedSite = computed(() => washSites.value.find(site => String(site.washSiteId) === selectedSiteId.value) || null)
const calendarDays = computed(() => {
  const first = new Date(calendarYear.value, calendarMonth.value, 1)
  const start = new Date(first)
  start.setDate(first.getDate() - first.getDay())
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(start, index)
    const key = toDateKey(date)
    return {
      key,
      day: date.getDate(),
      currentMonth: date.getMonth() === calendarMonth.value,
      selectable: key >= todayKey && key <= maxDateKey
    }
  })
})
const slotGroups = computed(() => {
  const groups = new Map()
  const oneHourLater = Date.now() + 60 * 60 * 1000
  for (const slot of availableSlots.value) {
    if (new Date(slot.startTime).getTime() < oneHourLater) continue
    const key = String(slot.startTime).slice(0, 16)
    if (!groups.has(key)) groups.set(key, { key, startTime: slot.startTime, endTime: slot.endTime, devices: [] })
    groups.get(key).devices.push({ deviceId: slot.deviceId, deviceName: slot.deviceName })
  }
  return [...groups.values()].sort((a, b) => a.key.localeCompare(b.key))
})
const selectedSlot = computed(() => slotGroups.value.find(slot => slot.key === selectedSlotKey.value) || null)
const canSubmit = computed(() => Boolean(selectedSite.value?.courseId && selectedSlot.value && plateNumber.value && phone.value))

watch([selectedSiteId, selectedDate], loadSlots)

onMounted(async () => {
  try {
    const response = await api.getWashSites()
    washSites.value = Array.isArray(response) ? response : []
    if (washSites.value.length) selectedSiteId.value = String(washSites.value[0].washSiteId)
  } catch (error) {
    errorMessage.value = getError(error, "세차장 정보를 불러오지 못했습니다.")
  }
})

async function loadSlots() {
  availableSlots.value = []
  selectedSlotKey.value = ""
  if (!selectedSiteId.value || !selectedDate.value) return
  slotLoading.value = true
  errorMessage.value = ""
  try {
    const response = await api.getAvailableSlots(Number(selectedSiteId.value), selectedDate.value)
    availableSlots.value = Array.isArray(response) ? response : []
  } catch (error) {
    errorMessage.value = getError(error, "예약 가능 시간을 불러오지 못했습니다.")
  } finally {
    slotLoading.value = false
  }
}

function moveMonth(amount) {
  const next = new Date(calendarYear.value, calendarMonth.value + amount, 1)
  const minMonth = new Date(now.getFullYear(), now.getMonth(), 1)
  const maxDate = addDays(now, 7)
  const maxMonth = new Date(maxDate.getFullYear(), maxDate.getMonth(), 1)
  if (next >= minMonth && next <= maxMonth) calendarCursor.value = next
}
function selectSlot(slot) {
  selectedSlotKey.value = slot.key
}
async function submitReservation() {
  submitting.value = true
  errorMessage.value = ""
  try {
    const response = await api.createReservation({
      plateNumber: plateNumber.value,
      phone: phone.value,
      washSiteId: Number(selectedSiteId.value),
      courseId: Number(selectedSite.value.courseId),
      startTime: String(selectedSlot.value.startTime).slice(0, 19)
    })
    createdReservation.value = response
    saveReservationAccess(response.reservationId, phone.value)
  } catch (error) {
    const message = getError(error, "예약을 생성하지 못했습니다.")
    await loadSlots()
    errorMessage.value = message
  } finally {
    submitting.value = false
  }
}
async function startPayment() {
  paying.value = true
  errorMessage.value = ""
  try {
    const response = await api.requestPayment({
      reservationId: createdReservation.value.reservationId,
      phone: phone.value,
      customerName: "비회원"
    })
    if (!response?.payUrl) throw new Error("결제 URL이 없습니다.")
    window.location.assign(response.payUrl)
  } catch (error) {
    errorMessage.value = getError(error, "결제를 시작하지 못했습니다.")
  } finally {
    paying.value = false
  }
}
function addDays(date, days) { const result = new Date(date); result.setDate(result.getDate() + days); return result }
function toDateKey(date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}` }
function formatTime(value) { return String(value || "").slice(11, 16) || "-" }
function formatDateTime(value) { return value ? new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-" }
function formatPrice(value) { return value == null ? "-" : `${Number(value).toLocaleString("ko-KR")}원` }
function getError(error, fallback) { return error?.response?.data?.message || error?.response?.data?.detail || fallback }
</script>

<style scoped>
.reservation-page { display: grid; gap: 24px; width: 100%; max-width: 1120px; margin: 0 auto; padding: 32px 24px 72px; box-sizing: border-box; color: #172033; text-align: left; }
.page-header, .schedule-card, .form-card, .result-card { border: 1px solid #dce5f1; border-radius: 14px; background: #fff; box-shadow: 0 12px 32px rgba(7, 27, 58, .06); }
.page-header { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 32px; align-items: center; padding: 30px 32px; }
.header-copy { min-width: 0; }
.page-header h1, h2 { margin: 0; color: #071b3a; }
.page-header h1 { font-size: clamp(36px, 4vw, 50px); line-height: 1.08; letter-spacing: -.04em; }
.page-header p:not(.eyebrow) { max-width: 680px; margin: 10px 0 0; color: #64748b; line-height: 1.55; word-break: keep-all; }
.header-actions { display: flex; flex-wrap: nowrap; gap: 10px; align-items: center; }
.page-header a, .back-button { min-height: 42px; padding: 0 16px; border-radius: 9px; font: inherit; font-weight: 800; }
.page-header a { display: inline-flex; align-items: center; color: #fff; background: #071b3a; text-decoration: none; }
.back-button { border: 1px solid #b8c5d6; color: #334155; background: #fff; cursor: pointer; }
.back-button:hover { background: #f2f6fa; }
.eyebrow { margin: 0 0 8px !important; color: #07884c !important; font-size: 11px; line-height: 1.2; font-weight: 900; letter-spacing: .1em; }
.reservation-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, .68fr); gap: 24px; align-items: start; }
.schedule-card, .form-card, .result-card { min-width: 0; padding: 28px; }
.form-card { position: sticky; top: 90px; display: grid; gap: 20px; }
.form-card > div:first-child { text-align: center; }
.field { display: grid; gap: 8px; color: #334155; font-size: 13px; font-weight: 800; }
input, select { width: 100%; min-width: 0; min-height: 46px; padding: 0 13px; box-sizing: border-box; border: 1px solid #ccd7e5; border-radius: 9px; background: #fff; font: inherit; }
.site-summary { display: grid; gap: 3px; margin: 14px 0 22px; padding: 14px; border-radius: 9px; background: #f5f8fc; color: #64748b; font-size: 12px; }
.site-summary strong { color: #071b3a; font-size: 14px; }
.calendar { padding: 18px 0 22px; border-top: 1px solid #e8edf3; }
.calendar-title { display: grid; grid-template-columns: 38px 1fr 38px; align-items: center; margin-bottom: 14px; text-align: center; }
.calendar-title button { border: 0; background: transparent; font-size: 25px; cursor: pointer; }
.week-row, .date-grid { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; }
.week-row { margin-bottom: 6px; color: #718096; font-size: 12px; }
.date-grid button { display: grid; place-content: center; min-height: 50px; border: 0; border-radius: 9px; background: transparent; cursor: pointer; }
.date-grid button:first-child, .date-grid button:nth-child(7n + 1) { color: #ef4444; }
.date-grid button.muted { color: #c5ccd5; }
.date-grid button.today { font-weight: 900; }
.date-grid button.selected { color: #fff; background: #08a45c; }
.date-grid small { font-size: 9px; }
.date-grid button:disabled { cursor: not-allowed; opacity: .45; }
.time-section { padding-top: 20px; border-top: 1px solid #e8edf3; }
.section-title { display: flex; justify-content: space-between; margin-bottom: 14px; color: #64748b; font-size: 12px; }
.section-title strong { color: #071b3a; font-size: 15px; }
.time-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; }
.time-grid button { display: grid; place-content: center; gap: 3px; min-height: 48px; border: 1px solid #dce2e9; border-radius: 9px; background: #fff; cursor: pointer; }
.time-grid button.selected { border-color: #08a45c; color: #fff; background: #08a45c; }
.time-grid button strong { font-size: 14px; }
.time-grid button small { font-size: 10px; opacity: .8; }
.empty-state { padding: 30px; border-radius: 9px; color: #64748b; background: #f7f9fc; text-align: center; }
.summary, .result-card dl { display: grid; margin: 0; }
.summary div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 16px; align-items: start; padding: 12px 0; border-bottom: 1px solid #edf1f5; }
.result-card dl div { display: flex; justify-content: space-between; gap: 16px; padding: 11px 0; border-bottom: 1px solid #edf1f5; }
dt, dd { margin: 0; }
.summary dt { white-space: nowrap; }
dd { min-width: 0; font-weight: 800; text-align: right; word-break: keep-all; overflow-wrap: break-word; }
.notice { padding: 12px; border-radius: 8px; color: #53657b; background: #f4f7fb; font-size: 12px; }
.warning { color: #9a5a00; background: #fff7df; }
.error-message { padding: 11px; border-radius: 8px; color: #b42318; background: #fff1f1; }
.primary-button { min-height: 48px; border: 0; border-radius: 9px; color: #fff; background: #08a45c; font-weight: 900; cursor: pointer; }
.primary-button:disabled { opacity: .55; cursor: not-allowed; }
.result-card { display: grid; gap: 16px; width: min(620px, 100%); margin: 0 auto; box-sizing: border-box; }
@media (max-width: 960px) { .reservation-layout { grid-template-columns: 1fr; } .form-card { position: static; } }
@media (max-width: 720px) { .reservation-page { padding: 20px 16px 52px; } .page-header { grid-template-columns: 1fr; gap: 20px; padding: 24px; } .header-actions { justify-content: flex-start; } }
@media (max-width: 560px) { .header-actions { display: grid; grid-template-columns: 1fr 1fr; } .page-header a, .back-button { justify-content: center; } .time-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .schedule-card, .form-card, .result-card { padding: 20px; } .summary div { grid-template-columns: 76px minmax(0, 1fr); gap: 12px; } }
</style>
