<template>
  <main class="owner-reservation-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">RESERVATION MANAGEMENT</p>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDescription }}</p>
      </div>
      <button type="button" :disabled="loading" @click="loadReservations">새로고침</button>
    </header>

    <section class="filter-card" aria-label="예약 검색 조건">
      <label v-if="isAdmin">
        점주
        <select v-model="filters.ownerId" @change="handleOwnerFilter">
          <option value="">전체 점주</option>
          <option v-for="owner in owners" :key="owner.ownerId" :value="String(owner.ownerId)">
            {{ owner.ownerName }}
          </option>
        </select>
      </label>
      <label>
        세차장
        <select v-model="filters.washSiteId">
          <option value="">전체 세차장</option>
          <option v-for="site in filteredWashSites" :key="site.washSiteId" :value="String(site.washSiteId)">
            {{ site.siteName }}
          </option>
        </select>
      </label>
      <label>
        예약 상태
        <select v-model="filters.status">
          <option value="">전체 상태</option>
          <option value="CONFIRMED">예약 확정</option>
          <option value="FULFILLED">이용 완료</option>
          <option value="CANCELED">예약 취소</option>
          <option value="NO_SHOW">노쇼</option>
        </select>
      </label>
      <label>
        시작일
        <input v-model="filters.startDate" type="date" />
      </label>
      <label>
        종료일
        <input v-model="filters.endDate" type="date" />
      </label>
      <label class="plate-filter">
        차량번호
        <input
          v-model.trim="filters.plateNumber"
          type="search"
          maxlength="20"
          placeholder="차량번호 일부 입력"
          @keyup.enter="search"
        />
      </label>
      <div class="filter-actions">
        <button type="button" class="secondary" @click="resetFilters">초기화</button>
        <button type="button" class="primary" @click="search">조회</button>
      </div>
    </section>

    <p v-if="errorMessage" class="notice error">{{ errorMessage }}</p>
    <p v-if="loading" class="state-card">예약 내역을 불러오고 있습니다.</p>

    <template v-else-if="reservations.length">
      <section class="table-card desktop-view">
        <table>
          <thead>
            <tr>
              <th>예약시간</th><th>세차장 / 세차기</th><th>고객</th><th>차량번호</th>
              <th>코스</th><th class="amount">금액</th><th>상태</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="reservation in reservations" :key="reservation.reservationId">
              <tr
                class="reservation-row"
                tabindex="0"
                role="button"
                :aria-expanded="expandedId === reservation.reservationId"
                :aria-label="`예약 #${reservation.reservationId} 상세보기`"
                @click="toggleDetail(reservation.reservationId)"
                @keydown.enter="toggleDetail(reservation.reservationId)"
                @keydown.space.prevent="toggleDetail(reservation.reservationId)"
              >
                <td><strong class="reservation-time">{{ formatDateTime(reservation.startTime) }}</strong></td>
                <td><strong>{{ reservation.siteName }}</strong><small>{{ reservation.deviceName }}</small></td>
                <td><strong>{{ reservation.customerName || "-" }}</strong><small>{{ reservation.customerPhone || "-" }}</small></td>
                <td class="plate">{{ reservation.plateNumber || "-" }}</td>
                <td>{{ reservation.courseName || "-" }}</td>
                <td class="amount">{{ formatPrice(reservation.reservationPrice) }}</td>
                <td><StatusBadge :status="reservation.status" /></td>
              </tr>
              <tr v-if="expandedId === reservation.reservationId" class="detail-row">
                <td colspan="7"><ReservationDetail :reservation="reservation" :allow-cancel="!isAdmin" /></td>
              </tr>
            </template>
          </tbody>
        </table>
      </section>

      <section class="mobile-view reservation-cards">
        <article
          v-for="reservation in reservations"
          :key="`mobile-${reservation.reservationId}`"
          class="reservation-card"
          tabindex="0"
          role="button"
          :aria-expanded="expandedId === reservation.reservationId"
          :aria-label="`예약 #${reservation.reservationId} 상세보기`"
          @click="toggleDetail(reservation.reservationId)"
          @keydown.enter="toggleDetail(reservation.reservationId)"
          @keydown.space.prevent="toggleDetail(reservation.reservationId)"
        >
          <header>
            <div><strong>{{ formatDate(reservation.startTime) }}</strong><span>{{ formatTimeRange(reservation) }}</span></div>
            <StatusBadge :status="reservation.status" />
          </header>
          <h2>{{ reservation.siteName }}</h2>
          <p>{{ reservation.deviceName }}</p>
          <dl>
            <div><dt>차량</dt><dd>{{ reservation.plateNumber }}</dd></div>
            <div><dt>고객</dt><dd>{{ reservation.customerName || "-" }}</dd></div>
            <div><dt>코스</dt><dd>{{ reservation.courseName || "-" }}</dd></div>
            <div><dt>금액</dt><dd>{{ formatPrice(reservation.reservationPrice) }}</dd></div>
          </dl>
          <ReservationDetail
            v-if="expandedId === reservation.reservationId"
            :reservation="reservation"
            :allow-cancel="!isAdmin"
          />
        </article>
      </section>
    </template>

    <section v-else class="state-card empty">
      <strong>검색 조건에 맞는 예약이 없습니다.</strong>
      <p>기간이나 상태 조건을 변경해서 다시 조회해 주세요.</p>
    </section>

    <nav v-if="totalPages > 1" class="pagination" aria-label="예약 목록 페이지">
      <button type="button" :disabled="page <= 1" @click="movePage(page - 1)">이전</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button type="button" :disabled="page >= totalPages" @click="movePage(page + 1)">다음</button>
    </nav>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from "vue"
import * as washSiteApi from "@/features/washSite/api/washSiteApi"
import * as reservationApi from "../api/reservationApi"

const props = defineProps({
  viewerRole: {
    type: String,
    default: "OWNER"
  }
})

const isAdmin = computed(() => props.viewerRole === "ADMIN")
const pageTitle = computed(() => isAdmin.value ? "전체 예약 관리" : "예약 관리")
const pageDescription = computed(() => isAdmin.value
  ? "전체 점주와 세차장의 예약을 조회할 수 있습니다."
  : "소유 세차장의 예약을 조회하고 필요한 경우 직접 취소할 수 있습니다.")
const owners = ref([])
const washSites = ref([])
const reservations = ref([])
const page = ref(1)
const totalPages = ref(0)
const loading = ref(false)
const cancelling = ref(false)
const errorMessage = ref("")
const expandedId = ref(null)
const cancelTargetId = ref(null)
const cancelReason = ref("")
const filters = reactive({ ownerId: "", washSiteId: "", status: "", startDate: "", endDate: "", plateNumber: "" })

const filteredWashSites = computed(() => {
  if (!filters.ownerId) return washSites.value
  return washSites.value.filter(
    site => String(site.ownerId) === filters.ownerId
  )
})

const StatusBadge = defineComponent({
  props: { status: String },
  setup(props) {
    return () => h("span", { class: "status-badge", "data-status": props.status }, statusLabel(props.status))
  }
})

const ReservationDetail = defineComponent({
  props: {
    reservation: { type: Object, required: true },
    allowCancel: { type: Boolean, default: true }
  },
  setup(props) {
    return () => h("div", {
      class: "detail-panel",
      onClick: event => event.stopPropagation(),
      onKeydown: event => event.stopPropagation()
    }, [
      h("dl", { class: "detail-list" }, [
        detailItem("예약번호", `#${props.reservation.reservationId}`),
        detailItem("예약시간", formatTimeRange(props.reservation)),
        detailItem("고객 연락처", props.reservation.customerPhone || "-"),
        detailItem("생성일", formatDateTime(props.reservation.createdAt)),
        props.reservation.fulfilledAt ? detailItem("이용 완료", formatDateTime(props.reservation.fulfilledAt)) : null,
        props.reservation.canceledAt ? detailItem("취소 시각", formatDateTime(props.reservation.canceledAt)) : null,
        props.reservation.cancelReason ? detailItem("취소 사유", props.reservation.cancelReason) : null
      ].filter(Boolean)),
      props.allowCancel && canCancel(props.reservation)
        ? h("div", { class: "cancel-panel" }, [
            cancelTargetId.value !== props.reservation.reservationId
              ? h("button", { class: "cancel-open", type: "button", onClick: () => openCancel(props.reservation.reservationId) }, "점주 취소")
              : h("div", { class: "cancel-form" }, [
                  h("label", { for: `owner-cancel-${props.reservation.reservationId}` }, "취소 사유"),
                  h("textarea", {
                    id: `owner-cancel-${props.reservation.reservationId}`,
                    rows: 3,
                    maxlength: 500,
                    placeholder: "장비 점검 등 취소 사유를 입력해 주세요.",
                    value: cancelReason.value,
                    onInput: event => { cancelReason.value = event.target.value }
                  }),
                  h("div", { class: "cancel-actions" }, [
                    h("button", { type: "button", onClick: closeCancel }, "닫기"),
                    h("button", {
                      class: "danger",
                      type: "button",
                      disabled: !cancelReason.value.trim() || cancelling.value,
                      onClick: () => cancelReservation(props.reservation.reservationId)
                    }, cancelling.value ? "처리 중" : "취소 확정")
                  ])
                ])
          ])
        : props.allowCancel && props.reservation.status === "CONFIRMED"
          ? h(
              "p",
              { class: "cutoff-note" },
              "예약 시작 1시간 전부터는 취소할 수 없습니다."
            )
          : props.allowCancel && props.reservation.status === "FULFILLED"
            ? h(
                "p",
                { class: "cutoff-note" },
                "세차 이용이 완료된 예약은 취소할 수 없습니다."
              )
          : null
    ].filter(Boolean))
  }
})

onMounted(async () => {
  await Promise.all([
    loadOwners(),
    loadWashSites(),
    loadReservations()
  ])
})

async function loadOwners() {
  if (!isAdmin.value) return
  try {
    const response = await washSiteApi.getOwnerList()
    owners.value = Array.isArray(response) ? response : []
  } catch {
    owners.value = []
  }
}

async function loadWashSites() {
  try {
    const response = await washSiteApi.getWashSiteList(props.viewerRole)
    washSites.value = Array.isArray(response) ? response : []
  } catch {
    washSites.value = []
  }
}

async function loadReservations() {
  loading.value = true
  errorMessage.value = ""
  try {
    const request = {
      page: page.value,
      size: 10,
      ownerId: filters.ownerId || undefined,
      washSiteId: filters.washSiteId || undefined,
      status: filters.status || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      plateNumber: filters.plateNumber || undefined
    }
    const response = isAdmin.value
      ? await reservationApi.getAdminReservations(request)
      : await reservationApi.getOwnerReservations(request)
    reservations.value = Array.isArray(response?.content) ? response.content : []
    totalPages.value = Number(response?.totalPages || 0)
  } catch (error) {
    reservations.value = []
    errorMessage.value = getErrorMessage(error, "예약 내역을 불러오지 못했습니다.")
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; closeDetail(); loadReservations() }
function resetFilters() {
  Object.assign(filters, { ownerId: "", washSiteId: "", status: "", startDate: "", endDate: "", plateNumber: "" })
  search()
}
function handleOwnerFilter() { filters.washSiteId = "" }
function movePage(next) { page.value = next; closeDetail(); loadReservations() }
function toggleDetail(id) {
  if (expandedId.value === id) closeDetail()
  else { expandedId.value = id; closeCancel() }
}
function closeDetail() { expandedId.value = null; closeCancel() }
function openCancel(id) { cancelTargetId.value = id; cancelReason.value = "" }
function closeCancel() { cancelTargetId.value = null; cancelReason.value = "" }

async function cancelReservation(id) {
  if (!cancelReason.value.trim()) return
  cancelling.value = true
  errorMessage.value = ""
  try {
    await reservationApi.cancelOwnerReservation(id, cancelReason.value.trim())
    closeDetail()
    await loadReservations()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, "예약을 취소하지 못했습니다.")
  } finally {
    cancelling.value = false
  }
}

function detailItem(label, value) { return h("div", [h("dt", label), h("dd", value)]) }
function formatDateTime(value) { return value ? String(value).replace("T", " ").slice(0, 16).replaceAll("-", ".") : "-" }
function formatDate(value) { return value ? String(value).slice(0, 10).replaceAll("-", ".") : "-" }
function formatTimeRange(r) { return `${String(r.startTime || "").slice(11,16)} ~ ${String(r.endTime || "").slice(11,16)}` }
function formatPrice(value) { return value == null ? "-" : `${Number(value).toLocaleString("ko-KR")}원` }
function statusLabel(status) { return { CONFIRMED: "예약 확정", FULFILLED: "이용 완료", CANCELED: "예약 취소", NO_SHOW: "노쇼" }[status] || status || "-" }
function canCancel(reservation) {
  if (reservation.status !== "CONFIRMED" || !reservation.startTime) return false
  return new Date(reservation.startTime).getTime() >= Date.now() + 60 * 60 * 1000
}
function getErrorMessage(error, fallback) { return error?.response?.data?.message || error?.response?.data?.detail || fallback }
</script>

<style scoped>
.owner-reservation-page { display: grid; gap: 18px; color: #172033; text-align: left; }
.page-heading,.filter-card,.table-card,.state-card,.reservation-card { border: 1px solid #dbe4ef; border-radius: 9px; background: #fff; box-shadow: 0 10px 24px rgba(15,23,42,.05); }
.page-heading { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:25px 28px; }
.eyebrow { margin:0 0 5px; color:#52749c; font-size:11px; font-weight:900; }
.page-heading h1 { margin:0; color:#0b1f3a; font-size:28px; font-weight:900; }
.page-heading p:last-child { margin-top:6px; color:#64748b; font-size:13px; }
.page-heading button,.filter-actions button { min-height:40px; padding:0 15px; border:1px solid #cad5e2; border-radius:7px; background:#fff; color:#334155; font-weight:900; cursor:pointer; }
.filter-card { display:grid; grid-template-columns:1.2fr 1fr 1fr 1fr 1.3fr auto; gap:12px; align-items:end; padding:17px; }
.filter-card label { display:grid; gap:5px; color:#52657d; font-size:11px; font-weight:900; }
.filter-card select,.filter-card input { width:100%; min-height:40px; padding:0 10px; border:1px solid #d2dce8; border-radius:7px; background:#fff; box-sizing:border-box; }
.filter-actions { display:flex; gap:7px; }
.filter-actions .primary { border-color:#0b1f3a; color:#fff; background:#0b1f3a; }
.notice { padding:12px 14px; border-radius:7px; font-size:12px; font-weight:800; }
.notice.error { color:#b42318; background:#fff1f1; }
.table-card { overflow-x:auto; }
table { width:100%; min-width:1050px; border-collapse:collapse; }
th,td { padding:11px 12px; border-bottom:1px solid #edf2f7; color:#344054; font-size:12px; text-align:left; vertical-align:middle; }
th { color:#52657d; background:#f6f8fb; font-weight:900; white-space:nowrap; }
.reservation-row { cursor:pointer; transition:background-color 120ms ease,box-shadow 120ms ease; }
.reservation-row:hover,.reservation-row:focus-visible { background:#eaf2ff; outline:none; box-shadow:inset 3px 0 0 #2563eb; }
.reservation-row[aria-expanded="true"] { background:#f2f7ff; box-shadow:inset 3px 0 0 #174d82; }
td strong,td small { display:block; } td small { margin-top:3px; color:#718096; font-size:11px; }
.amount { text-align:right; }.plate { color:#0b1f3a; font-weight:900; }
.reservation-time { color:#174d82; font-weight:900; }
.detail-row td { padding:0; background:#f8fafc; }
:deep(.status-badge) { display:inline-flex; justify-content:center; min-width:64px; padding:5px 8px; border-radius:999px; color:#087944; background:#e8f5f0; font-size:10px; font-weight:900; }
:deep(.status-badge[data-status="FULFILLED"]) { color:#31577f; background:#edf3fa; }
:deep(.status-badge[data-status="CANCELED"]) { color:#be123c; background:#fff1f2; }
:deep(.status-badge[data-status="NO_SHOW"]) { color:#b45309; background:#fff7e6; }
:deep(.detail-panel) { display:grid; gap:13px; padding:17px 20px; }
:deep(.detail-list) { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:9px; margin:0; }
:deep(.detail-list div) { padding:10px; border:1px solid #e5ebf2; border-radius:7px; background:#fff; }
:deep(.detail-list dt),:deep(.detail-list dd) { margin:0; }:deep(.detail-list dt) { color:#718096; font-size:10px; }:deep(.detail-list dd) { margin-top:3px; color:#172033; font-size:12px; font-weight:900; }
:deep(.cancel-panel) { display:grid; justify-items:end; gap:8px; }
:deep(.cutoff-note) { margin:0; padding:10px 12px; border-radius:7px; color:#7c5b20; background:#fff8e8; font-size:12px; }
:deep(.cancel-open) { min-height:34px; padding:0 12px; border:1px solid #efc4c4; border-radius:7px; color:#b42318; background:#fff; font-weight:900; cursor:pointer; }
:deep(.cancel-form) { display:grid; gap:7px; width:min(100%,480px); }:deep(.cancel-form label) { color:#52657d; font-size:11px; font-weight:900; }:deep(.cancel-form textarea) { padding:9px; border:1px solid #d7e0eb; border-radius:7px; resize:vertical; font:inherit; font-size:12px; }
:deep(.cancel-actions) { display:flex; justify-content:flex-end; gap:7px; }:deep(.cancel-actions button) { min-height:34px; padding:0 11px; border:1px solid #d6dfe9; border-radius:6px; background:#fff; font-weight:800; cursor:pointer; }:deep(.cancel-actions .danger) { border-color:#b42318; color:#fff; background:#b42318; }:deep(.cancel-actions button:disabled) { opacity:.5; }
.state-card { display:grid; place-items:center; gap:8px; min-height:200px; padding:25px; color:#64748b; text-align:center; }.state-card strong { color:#0b1f3a; font-size:18px; }
.mobile-view { display:none; }.pagination { display:flex; align-items:center; justify-content:center; gap:12px; }.pagination button { min-height:36px; padding:0 13px; border:1px solid #d2dce8; border-radius:7px; background:#fff; font-weight:800; }.pagination button:disabled { opacity:.4; }.pagination span { font-size:12px; font-weight:900; }
@media(max-width:1050px){.filter-card{grid-template-columns:repeat(3,minmax(0,1fr))}.filter-actions{justify-content:flex-end}}
@media(max-width:720px){.page-heading{align-items:stretch;flex-direction:column;padding:21px 17px}.filter-card{grid-template-columns:1fr 1fr}.plate-filter,.filter-actions{grid-column:1/-1}.desktop-view{display:none}.mobile-view{display:grid}.reservation-cards{gap:12px}.reservation-card{display:grid;gap:11px;padding:17px;cursor:pointer}.reservation-card:hover,.reservation-card:focus-visible{border-color:#9eb7d8;outline:none;background:#f8fbff}.reservation-card[aria-expanded="true"]{border-color:#7897bd;background:#f6f9fe}.reservation-card header{display:flex;justify-content:space-between;gap:10px}.reservation-card header>div{display:grid;gap:2px}.reservation-card header span{color:#52749c;font-size:12px;font-weight:900}.reservation-card h2{margin:0;color:#0b1f3a;font-size:18px;font-weight:900}.reservation-card>p{color:#64748b;font-size:12px}.reservation-card>dl{display:grid;grid-template-columns:1fr 1fr;gap:7px;margin:0}.reservation-card>dl div{padding:9px;border-radius:7px;background:#f6f8fb}.reservation-card dt,.reservation-card dd{margin:0}.reservation-card dt{color:#718096;font-size:10px}.reservation-card dd{margin-top:2px;font-size:12px;font-weight:900}:deep(.detail-list){grid-template-columns:1fr 1fr}}
</style>
