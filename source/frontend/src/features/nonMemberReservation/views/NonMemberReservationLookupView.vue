<template>
  <main class="lookup-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">GUEST RESERVATION</p>
        <h1>비회원 예약 조회</h1>
        <p>예약번호와 예약 당시 휴대전화 번호로 확인합니다.</p>
      </div>
      <div class="header-actions">
        <button class="back-button" type="button" @click="goBack">
          ← 뒤로가기
        </button>
        <RouterLink :to="{ name: 'non-member-reservation-create' }">새 예약</RouterLink>
      </div>
    </header>

    <section class="lookup-card">
      <form class="lookup-form" @submit.prevent="lookup">
        <label>
          <span>예약번호</span>
          <input v-model.trim="form.reservationId" inputmode="numeric" placeholder="예약번호">
        </label>
        <label>
          <span>휴대전화 번호</span>
          <input v-model.trim="form.phone" inputmode="tel" placeholder="01012345678">
        </label>
        <button type="submit" :disabled="loading || !canLookup">
          {{ loading ? "조회 중" : "예약 조회" }}
        </button>
      </form>
      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="reconciliationMessage" class="pending-message">{{ reconciliationMessage }}</p>
      <p v-if="successMessage" class="success-message">{{ successMessage }}</p>
    </section>

    <section v-if="reservation" class="detail-card">
      <div class="detail-heading">
        <div>
          <p class="eyebrow">RESERVATION #{{ reservation.reservationId }}</p>
          <h2>{{ reservation.siteName }}</h2>
        </div>
        <span class="status" :class="statusClass">{{ statusLabel }}</span>
      </div>

      <dl>
        <div><dt>예약일시</dt><dd>{{ formatDateTime(reservation.startTime) }}</dd></div>
        <div><dt>세차기</dt><dd>{{ reservation.deviceName || "-" }}</dd></div>
        <div><dt>코스</dt><dd>{{ reservation.courseName || "-" }}</dd></div>
        <div><dt>차량번호</dt><dd>{{ reservation.plateNumber || "-" }}</dd></div>
        <div><dt>연락처</dt><dd>{{ reservation.maskedPhone || "-" }}</dd></div>
        <div><dt>결제금액</dt><dd>{{ formatPrice(reservation.reservationPrice) }}</dd></div>
        <div><dt>결제상태</dt><dd>{{ paymentLabel }}</dd></div>
        <div v-if="reservation.paymentExpiresAt"><dt>결제기한</dt><dd>{{ formatDateTime(reservation.paymentExpiresAt) }}</dd></div>
        <div v-if="reservation.cancelReason"><dt>취소사유</dt><dd>{{ reservation.cancelReason }}</dd></div>
      </dl>

      <div v-if="canAct" class="actions">
        <button v-if="needsPayment" class="payment-button" type="button" :disabled="actionBusy" @click="startPayment">
          {{ paying ? "결제 준비 중" : "사전결제 진행" }}
        </button>
        <button class="cancel-button" type="button" :disabled="actionBusy" @click="cancelReservation">
          {{ cancelButtonLabel }}
        </button>
      </div>
      <p v-else-if="reservation.status === 'FULFILLED'" class="state-note">
        세차 이용이 완료된 예약은 취소할 수 없습니다.
      </p>
      <p v-else-if="reservation.status === 'CONFIRMED'" class="state-note">
        예약 시작 1시간 전부터는 취소할 수 없습니다.
      </p>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import * as api from "../api/nonMemberReservationApi"
import { getReservationAccess, removeReservationAccess, saveReservationAccess } from "../utils/nonMemberReservationAccess"

const route = useRoute()
const router = useRouter()
const form = reactive({ reservationId: "", phone: "" })
const reservation = ref(null)
const loading = ref(false)
const paying = ref(false)
const canceling = ref(false)
const errorMessage = ref("")
const reconciliationMessage = ref("")
const successMessage = ref("")

function goBack() {
  router.push({ name: "home" })
}

const canLookup = computed(() => Boolean(form.reservationId && form.phone))
const statusLabel = computed(() => ({
  PENDING_PAYMENT: "결제 대기",
  CONFIRMED: "예약 확정",
  CANCELED: "예약 취소",
  FULFILLED: "이용 완료",
  NO_SHOW: "노쇼"
}[reservation.value?.status] || reservation.value?.status || "-"))
const statusClass = computed(() => String(reservation.value?.status || "").toLowerCase())
const paymentLabel = computed(() => ({
  WAITING: "결제 대기",
  PAID: "결제 완료",
  SUCCESS: "결제 완료",
  FAILED: "결제 실패",
  CANCELED: "결제 취소",
  REFUNDED: "환불 완료"
}[reservation.value?.paymentStatus] || reservation.value?.paymentStatus || "-") )
const needsPayment = computed(() => reservation.value?.status === "PENDING_PAYMENT" && !["PAID", "SUCCESS"].includes(reservation.value?.paymentStatus))
const canCancel = computed(() => {
  if (reservation.value?.status === "PENDING_PAYMENT") return true
  if (reservation.value?.status !== "CONFIRMED" || !reservation.value?.startTime) return false
  return new Date(reservation.value.startTime).getTime() >= Date.now() + 60 * 60 * 1000
})
const canAct = computed(() => needsPayment.value || canCancel.value)
const isPaidReservation = computed(() => reservation.value?.status === "CONFIRMED" && reservation.value?.paymentStatus === "PAID")
const actionBusy = computed(() => loading.value || paying.value || canceling.value)
const cancelButtonLabel = computed(() => {
  if (canceling.value) return isPaidReservation.value ? "전액 환불 처리 중" : "취소 중"
  return isPaidReservation.value ? "예약 취소 및 전액 환불" : "예약 취소"
})

onMounted(async () => {
  const routeId = String(route.params.reservationId || "")
  if (!routeId) return
  form.reservationId = routeId
  const access = getReservationAccess(routeId)
  if (!access) return
  form.phone = access.phone || ""
  await lookup()
})

async function lookup() {
  loading.value = true
  errorMessage.value = ""
  reconciliationMessage.value = ""
  successMessage.value = ""
  try {
    reservation.value = await api.lookupReservation(accessPayload())
    saveReservationAccess(form.reservationId, form.phone)
  } catch (error) {
    reservation.value = null
    errorMessage.value = getError(error, "예약 정보를 확인하지 못했습니다.")
  } finally {
    loading.value = false
  }
}

async function startPayment() {
  if (actionBusy.value) return
  paying.value = true
  errorMessage.value = ""
  reconciliationMessage.value = ""
  successMessage.value = ""
  try {
    const response = await api.requestPayment({ ...accessPayload(), customerName: "비회원" })
    if (!response?.payUrl) throw new Error("결제 URL이 없습니다.")
    window.location.assign(response.payUrl)
  } catch (error) {
    errorMessage.value = getError(error, "결제를 시작하지 못했습니다.")
  } finally {
    paying.value = false
  }
}

async function cancelReservation() {
  if (actionBusy.value) return
  const refundRequired = isPaidReservation.value
  const reason = window.prompt("예약 취소 사유를 입력해 주세요.", "고객 요청")
  if (!reason?.trim()) return
  const confirmation = refundRequired
    ? `결제금액 ${formatPrice(reservation.value?.reservationPrice)}을 전액 환불하고 예약을 취소합니다.\n예약 시작 1시간 전까지만 취소할 수 있습니다.\n계속하시겠습니까?`
    : "예약을 취소하시겠습니까?"
  if (!window.confirm(confirmation)) return
  canceling.value = true
  errorMessage.value = ""
  reconciliationMessage.value = ""
  successMessage.value = ""
  try {
    const result = await api.cancelReservation(Number(form.reservationId), {
      phone: form.phone,
      cancelReason: reason.trim()
    })
    if (refundRequired) {
      if (result?.refundStatus !== "SUCCESS") {
        throw new Error("환불 완료 응답을 확인하지 못했습니다.")
      }
      reservation.value = {
        ...reservation.value,
        status: result.reservationStatus,
        paymentStatus: result.paymentStatus,
        cancelReason: reason.trim(),
        paymentExpiresAt: null
      }
      successMessage.value = `${formatPrice(result.refundAmount)} 전액 환불이 완료되었습니다.`
      removeReservationAccess(form.reservationId)
      return
    }

    await lookup()
    removeReservationAccess(form.reservationId)
    successMessage.value = "예약 취소가 완료되었습니다."
  } catch (error) {
    if (refundRequired && isReconciliationPending(error)) {
      reconciliationMessage.value = "결제사 취소는 승인되었지만 내부 반영을 확인 중입니다. 잠시 후 다시 조회해 주세요."
    } else {
      errorMessage.value = getError(
        error,
        refundRequired
          ? "전액 환불을 완료하지 못했습니다. 결제 상태를 확인한 뒤 다시 시도해 주세요."
          : "예약을 취소하지 못했습니다."
      )
    }
  } finally {
    canceling.value = false
  }
}

function accessPayload() {
  return { reservationId: Number(form.reservationId), phone: form.phone }
}
function formatDateTime(value) { return value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-" }
function formatPrice(value) { return value == null ? "-" : `${Number(value).toLocaleString("ko-KR")}원` }
function getError(error, fallback) { return error?.response?.data?.message || error?.response?.data?.detail || fallback }
function isReconciliationPending(error) {
  const status = error?.response?.status
  const message = getError(error, "")
  return status === 409 && /재조정|결제 취소는 승인|내부 반영/.test(message)
}
</script>

<style scoped>
.lookup-page { display: grid; gap: 20px; max-width: 920px; margin: 0 auto; padding: 28px 20px 64px; color: #172033; }
.page-header, .lookup-card, .detail-card { padding: 24px 28px; border: 1px solid #dce5f1; border-radius: 14px; background: #fff; box-shadow: 0 12px 32px rgba(7, 27, 58, .06); }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 24px; }
.page-header > div:first-child { min-width: 0; }
.page-header h1 { margin: 0; color: #071b3a; font-size: clamp(2rem, 4.2vw, 2.5rem); line-height: 1.18; letter-spacing: -.04em; }
.detail-heading h2 { margin: 0; color: #071b3a; }
.page-header p:not(.eyebrow) { margin: 8px 0 0; color: #52627a; font-size: 1rem; line-height: 1.55; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.page-header a, .back-button { min-height: 42px; padding: 0 16px; border-radius: 9px; font: inherit; font-weight: 800; }
.page-header a { display: inline-flex; align-items: center; color: #fff; background: #071b3a; text-decoration: none; }
.back-button { border: 1px solid #b8c5d6; color: #334155; background: #fff; cursor: pointer; }
.back-button:hover { background: #f2f6fa; }
.eyebrow { margin: 0 0 6px !important; color: #087a49 !important; font-size: 12px; font-weight: 900; letter-spacing: .1em; }
.lookup-form { display: grid; grid-template-columns: .7fr 1fr auto; gap: 12px; align-items: end; }
label { display: grid; gap: 8px; color: #24324a; font-size: 14px; font-weight: 800; }
input { min-width: 0; min-height: 48px; padding: 0 14px; border: 1px solid #aebdd0; border-radius: 9px; color: #172033; background: #fff; font: inherit; font-size: 15px; }
input::placeholder { color: #718096; opacity: 1; }
input:focus { border-color: #087a49; outline: 3px solid rgba(8, 122, 73, .16); outline-offset: 1px; }
.lookup-form button, .actions button { min-height: 48px; padding: 0 20px; border-radius: 9px; font-size: 15px; font-weight: 900; cursor: pointer; }
.lookup-form button, .payment-button { border: 1px solid #087a49; color: #fff; background: #087a49; }
button:disabled { cursor: not-allowed; opacity: .55; }
.lookup-form button:disabled { border-color: #64748b; color: #fff; background: #64748b; opacity: 1; }
.error-message { padding: 11px; border-radius: 8px; color: #b42318; background: #fff1f1; }
.pending-message { padding: 11px; border-radius: 8px; color: #8a4b08; background: #fff7e6; }
.success-message { padding: 11px; border-radius: 8px; color: #075e36; background: #e7f8ef; }
.detail-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.status { padding: 7px 11px; border-radius: 999px; color: #075e36; background: #e7f8ef; font-size: 12px; font-weight: 900; }
.status.canceled, .status.no_show { color: #a1261d; background: #fff0ee; }
.detail-card dl { display: grid; margin: 22px 0 0; }
.detail-card dl div { display: flex; justify-content: space-between; gap: 20px; padding: 12px 0; border-bottom: 1px solid #edf1f5; }
dt, dd { margin: 0; } dt { color: #64748b; } dd { font-weight: 800; text-align: right; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 22px; }
.cancel-button { border: 1px solid #bdc9d8; color: #334155; background: #fff; }
.state-note { margin: 16px 0 0; padding: 12px 14px; border-radius: 8px; color: #53657b; background: #f4f7fb; font-size: 13px; }
@media (max-width: 760px) { .lookup-form { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .page-header { align-items: stretch; flex-direction: column; gap: 16px; } .header-actions { display: grid; grid-template-columns: 1fr 1fr; } .page-header a, .back-button { justify-content: center; } .page-header, .lookup-card, .detail-card { padding: 20px; } }
</style>
