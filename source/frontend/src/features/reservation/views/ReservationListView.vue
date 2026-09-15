<template>
  <main class="reservation-list-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">MY RESERVATIONS</p>
        <h1>내 예약</h1>
        <p>예약 일정과 이용 상태를 확인하고 예약을 취소할 수 있습니다.</p>
      </div>
      <RouterLink class="primary-link" :to="{ name: 'customer-reservation-create' }">
        새 예약
      </RouterLink>
    </header>

    <p v-if="route.query.created === 'true'" class="notice success">
      예약이 즉시 확정되었습니다. 예약 시간에 등록 차량으로 방문해 주세요.
    </p>
    <p v-if="errorMessage" class="notice error">{{ errorMessage }}</p>

    <section class="filter-card">
      <label>
        예약 상태
        <select v-model="statusFilter" @change="changeFilter">
          <option value="">전체</option>
          <option value="CONFIRMED">예약 확정</option>
          <option value="FULFILLED">이용 완료</option>
          <option value="CANCELED">예약 취소</option>
          <option value="NO_SHOW">노쇼</option>
        </select>
      </label>
      <button type="button" :disabled="loading" @click="loadReservations">새로고침</button>
    </section>

    <p v-if="loading" class="state-card">예약 내역을 불러오고 있습니다.</p>
    <section v-else-if="reservations.length" class="reservation-grid">
      <article
        v-for="reservation in reservations"
        :key="reservation.reservationId"
        class="reservation-card"
      >
        <header>
          <div class="reservation-date">
            <strong>{{ formatDate(reservation.startTime) }}</strong>
            <span>{{ formatTimeRange(reservation.startTime, reservation.endTime) }}</span>
          </div>
          <span class="status-badge" :data-status="reservation.status">
            {{ statusLabel(reservation.status) }}
          </span>
        </header>

        <div class="site-info">
          <h2>{{ reservation.siteName || "세차장" }}</h2>
          <p>{{ reservation.deviceName || `세차기 ${reservation.deviceId}` }}</p>
        </div>

        <dl>
          <div><dt>차량번호</dt><dd>{{ reservation.plateNumber || "-" }}</dd></div>
          <div><dt>세차 코스</dt><dd>{{ reservation.courseName || "-" }}</dd></div>
          <div><dt>현장 결제</dt><dd>{{ formatPrice(reservation.reservationPrice) }}</dd></div>
          <div><dt>예약번호</dt><dd>#{{ reservation.reservationId }}</dd></div>
        </dl>

        <p v-if="reservation.cancelReason" class="cancelled-reason">
          취소 사유: {{ reservation.cancelReason }}
        </p>

        <div v-if="canCancel(reservation)" class="cancel-area">
          <button
            v-if="cancelTargetId !== reservation.reservationId"
            type="button"
            class="cancel-open-button"
            @click="openCancel(reservation.reservationId)"
          >
            예약 취소
          </button>
          <template v-else>
            <label :for="`reason-${reservation.reservationId}`">취소 사유</label>
            <textarea
              :id="`reason-${reservation.reservationId}`"
              v-model="cancelReason"
              maxlength="500"
              rows="3"
              placeholder="취소 사유를 입력해 주세요."
            />
            <div class="cancel-actions">
              <button type="button" @click="closeCancel">닫기</button>
              <button
                type="button"
                class="danger"
                :disabled="!cancelReason.trim() || cancelling"
                @click="cancelReservation(reservation.reservationId)"
              >
                {{ cancelling ? "취소 처리 중" : "취소 확정" }}
              </button>
            </div>
          </template>
        </div>
        <p v-else-if="reservation.status === 'CONFIRMED'" class="cutoff-note">
          예약 시작 1시간 전부터는 취소할 수 없습니다.
        </p>
        <p v-else-if="reservation.status === 'FULFILLED'" class="cutoff-note">
          세차 이용이 완료된 예약은 취소할 수 없습니다.
        </p>
      </article>
    </section>
    <section v-else class="state-card empty">
      <strong>표시할 예약이 없습니다.</strong>
      <p>원하는 세차장과 시간을 선택해 첫 예약을 만들어 보세요.</p>
      <RouterLink class="primary-link" :to="{ name: 'customer-reservation-create' }">
        예약하러 가기
      </RouterLink>
    </section>

    <nav v-if="totalPages > 1" class="pagination" aria-label="예약 목록 페이지">
      <button type="button" :disabled="page <= 1" @click="movePage(page - 1)">이전</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button type="button" :disabled="page >= totalPages" @click="movePage(page + 1)">다음</button>
    </nav>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue"
import { useRoute } from "vue-router"
import * as reservationApi from "../api/reservationApi"

const route = useRoute()
const reservations = ref([])
const statusFilter = ref("")
const page = ref(1)
const totalPages = ref(0)
const loading = ref(false)
const cancelling = ref(false)
const errorMessage = ref("")
const cancelTargetId = ref(null)
const cancelReason = ref("")

onMounted(loadReservations)

async function loadReservations() {
  loading.value = true
  errorMessage.value = ""
  try {
    const response = await reservationApi.getCustomerReservations({
      page: page.value,
      size: 10,
      status: statusFilter.value || undefined
    })
    reservations.value = Array.isArray(response?.content) ? response.content : []
    totalPages.value = Number(response?.totalPages || 0)
  } catch (error) {
    reservations.value = []
    errorMessage.value = getErrorMessage(error, "예약 내역을 불러오지 못했습니다.")
  } finally {
    loading.value = false
  }
}

function changeFilter() {
  page.value = 1
  loadReservations()
}

function movePage(nextPage) {
  page.value = nextPage
  closeCancel()
  loadReservations()
}

function openCancel(reservationId) {
  cancelTargetId.value = reservationId
  cancelReason.value = ""
}

function closeCancel() {
  cancelTargetId.value = null
  cancelReason.value = ""
}

async function cancelReservation(reservationId) {
  if (!cancelReason.value.trim()) return
  cancelling.value = true
  errorMessage.value = ""
  try {
    await reservationApi.cancelCustomerReservation(
      reservationId,
      cancelReason.value.trim()
    )
    closeCancel()
    await loadReservations()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, "예약을 취소하지 못했습니다.")
  } finally {
    cancelling.value = false
  }
}

function canCancel(reservation) {
  if (reservation.status !== "CONFIRMED" || !reservation.startTime) return false
  return new Date(reservation.startTime).getTime() >= Date.now() + 60 * 60 * 1000
}

function formatDate(value) {
  if (!value) return "-"
  const date = new Date(value)
  const weekdays = ["일", "월", "화", "수", "목", "금", "토"]
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")} (${weekdays[date.getDay()]})`
}

function formatTimeRange(start, end) {
  return `${String(start || "").slice(11, 16)} ~ ${String(end || "").slice(11, 16)}`
}

function formatPrice(value) {
  return value == null ? "-" : `${Number(value).toLocaleString("ko-KR")}원`
}

function statusLabel(status) {
  return {
    CONFIRMED: "예약 확정",
    FULFILLED: "이용 완료",
    CANCELED: "예약 취소",
    NO_SHOW: "노쇼"
  }[status] || status || "-"
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.response?.data?.detail || fallback
}
</script>

<style scoped>
.reservation-list-page { display: grid; gap: 18px; color: #172033; text-align: left; }
.page-heading, .filter-card, .reservation-card, .state-card { border: 1px solid #dce5f1; border-radius: 12px; background: #fff; box-shadow: 0 12px 30px rgba(7,27,58,.05); }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 28px 32px; }
.eyebrow { margin: 0 0 5px; color: #177245; font-size: 11px; font-weight: 900; letter-spacing: .08em; }
.page-heading h1 { margin: 0; color: #071b3a; font-size: 32px; font-weight: 900; }
.page-heading p:last-child { margin-top: 8px; color: #64748b; font-size: 14px; }
.primary-link { display: inline-flex; align-items: center; justify-content: center; min-height: 43px; padding: 0 18px; border-radius: 8px; color: #fff; background: #08a45c; font-size: 14px; font-weight: 900; text-decoration: none; }
.notice { padding: 13px 16px; border-radius: 8px; font-size: 13px; font-weight: 800; }
.notice.success { color: #087944; background: #eaf8f1; }
.notice.error { color: #b42318; background: #fff1f1; }
.filter-card { display: flex; align-items: end; justify-content: space-between; gap: 15px; padding: 16px 20px; }
.filter-card label { display: grid; gap: 6px; color: #52637d; font-size: 12px; font-weight: 900; }
.filter-card select { min-width: 180px; min-height: 40px; padding: 0 11px; border: 1px solid #d4deea; border-radius: 8px; background: #fff; }
.filter-card button, .pagination button { min-height: 38px; padding: 0 14px; border: 1px solid #d4deea; border-radius: 8px; color: #334155; background: #fff; font-weight: 800; cursor: pointer; }
.reservation-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 16px; }
.reservation-card { display: grid; gap: 17px; padding: 22px; }
.reservation-card > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.reservation-date { display: grid; gap: 3px; }
.reservation-date strong { color: #071b3a; font-size: 18px; }
.reservation-date span { color: #177245; font-size: 14px; font-weight: 900; }
.status-badge { padding: 6px 10px; border-radius: 999px; color: #087944; background: #eaf8f1; font-size: 11px; font-weight: 900; }
.status-badge[data-status="CANCELED"], .status-badge[data-status="NO_SHOW"] { color: #b42318; background: #fff1f1; }
.status-badge[data-status="FULFILLED"] { color: #31577f; background: #edf3fa; }
.site-info h2 { margin: 0; color: #071b3a; font-size: 20px; font-weight: 900; }
.site-info p { margin-top: 4px; color: #64748b; font-size: 13px; }
dl { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 9px; margin: 0; }
dl div { padding: 11px 12px; border-radius: 8px; background: #f6f8fb; }
dt, dd { margin: 0; }
dt { color: #718096; font-size: 11px; }
dd { margin-top: 3px; color: #172033; font-size: 13px; font-weight: 900; }
.cancelled-reason, .cutoff-note { padding: 10px 12px; border-radius: 8px; color: #7c5b20; background: #fff8e8; font-size: 12px; }
.cancel-area { display: grid; gap: 8px; }
.cancel-area label { color: #52637d; font-size: 12px; font-weight: 900; }
.cancel-area textarea { padding: 10px; resize: vertical; border: 1px solid #d7e0eb; border-radius: 8px; font: inherit; font-size: 13px; }
.cancel-open-button { justify-self: end; min-height: 37px; padding: 0 13px; border: 1px solid #efc4c4; border-radius: 8px; color: #b42318; background: #fff; font-weight: 900; cursor: pointer; }
.cancel-actions { display: flex; justify-content: flex-end; gap: 8px; }
.cancel-actions button { min-height: 37px; padding: 0 13px; border: 1px solid #d8e0ea; border-radius: 8px; background: #fff; font-weight: 800; cursor: pointer; }
.cancel-actions .danger { border-color: #b42318; color: #fff; background: #b42318; }
.cancel-actions button:disabled { opacity: .5; cursor: not-allowed; }
.state-card { display: grid; place-items: center; gap: 10px; min-height: 220px; padding: 30px; color: #64748b; text-align: center; }
.state-card strong { color: #071b3a; font-size: 20px; }
.pagination { display: flex; align-items: center; justify-content: center; gap: 14px; }
.pagination span { color: #475569; font-size: 13px; font-weight: 900; }
.pagination button:disabled { opacity: .4; cursor: default; }
@media (max-width: 800px) { .reservation-grid { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .page-heading { align-items: stretch; flex-direction: column; padding: 22px 18px; } .filter-card { align-items: stretch; flex-direction: column; } .filter-card select { width: 100%; } dl { grid-template-columns: 1fr; } }
</style>
