<script setup>
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import ProgressBar from 'primevue/progressbar'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PlateBboxEditor from '@/admin/features/plateDetectionLog/components/PlateBboxEditor.vue'
import PlateDetectionPreview from '@/admin/features/plateDetectionLog/components/PlateDetectionPreview.vue'
import { usePlateDetectionLogs } from '@/admin/features/plateDetectionLog/composables/usePlateDetectionLogs'

const route = useRoute()
const router = useRouter()
const store = reactive(usePlateDetectionLogs())
const startDate = ref(null)
const endDate = ref(null)
const plateNumber = ref(String(route.query.plateNumber || ''))
const logFilter = ref(normalizeLogFilter(route.query.filter))
const reviewDialogVisible = ref(false)
const reviewForm = reactive({ reviewStatus: '', correctedPlateNumber: '', failureReason: '', trainingUseYn: false, reviewMemo: '', bboxReviewStatus: '', reviewedBox: null })
const coordinateForm = reactive({ x1: null, y1: null, x2: null, y2: null })

const filterOptions = [{ label: '전체 로그', value: 'ALL' }, { label: '인식 실패', value: 'FAILED_ONLY' }, { label: '검토 필요', value: 'NEED_REVIEW_ONLY' }]
const reviewStatusOptions = [{ label: '정상', value: 'NORMAL' }, { label: '실패', value: 'FAILED' }, { label: '의심', value: 'SUSPECT' }, { label: '제외', value: 'EXCLUDED' }]
const failureReasonOptions = [{ label: '흐림', value: 'BLUR' }, { label: '각도 문제', value: 'ANGLE' }, { label: '어두움', value: 'DARK' }, { label: '빛 반사', value: 'GLARE' }, { label: '번호판 일부 가림', value: 'PARTIAL' }, { label: '문자 인식 오류', value: 'OCR_ERROR' }, { label: '크롭 실패', value: 'CROP_ERROR' }, { label: '번호판 탐지 오류', value: 'YOLO_ERROR' }, { label: 'YOLO 신뢰도 낮음', value: 'LOW_YOLO_CONFIDENCE' }, { label: 'OCR 신뢰도 낮음', value: 'LOW_OCR_CONFIDENCE' }, { label: '기타/미분류', value: 'UNKNOWN' }]
const bboxStatusOptions = [{ label: 'YOLO 영역 승인', value: 'ACCEPTED' }, { label: '좌표 보정', value: 'CORRECTED' }, { label: '번호판 없음', value: 'NO_PLATE' }]
const detailVisible = computed({ get: () => Boolean(route.query.plateDetectionLogId), set: (visible) => { if (!visible) closeDetail() } })
const prioritizedLogs = computed(() => [...store.logs].sort((a, b) =>
  reviewPriority(b).rank - reviewPriority(a).rank
  || new Date(a.detectedTime || 0) - new Date(b.detectedTime || 0)))
const currentSearch = computed(() => ({
  page: store.pageInfo.page, size: store.pageInfo.size,
  startDate: toApiDate(startDate.value), endDate: toApiDate(endDate.value),
  plateNumber: plateNumber.value.trim() || undefined,
  failedOnly: logFilter.value === 'FAILED_ONLY' || undefined,
  needReviewOnly: logFilter.value === 'NEED_REVIEW_ONLY' || undefined,
}))
const predictedBox = computed(() => store.detail ? ({ x1: store.detail.yoloX1, y1: store.detail.yoloY1, x2: store.detail.yoloX2, y2: store.detail.yoloY2 }) : null)
const isReviewed = computed(() => Boolean(store.detail?.reviewStatus && store.detail.reviewStatus !== 'UNREVIEWED'))
const nextReviewLog = computed(() => prioritizedLogs.value.find((item) => item.needReview && item.plateDetectionLogId !== store.detail?.plateDetectionLogId) || null)
const detailRisk = computed(() => detectionRisk(store.detail))
const expiryInfo = computed(() => imageExpiry(store.detail?.fileExpireTime))

onMounted(async () => {
  await store.loadLogs(currentSearch.value)
  if (route.query.plateDetectionLogId) await store.loadDetail(route.query.plateDetectionLogId)
})
watch(() => route.query.plateDetectionLogId, async (id, oldId) => { if (id && id !== oldId) await store.loadDetail(id) })
watch(() => route.query.filter, async (filter) => {
  const next = normalizeLogFilter(filter)
  if (next === logFilter.value) return
  logFilter.value = next
  await search(1)
})
watch(() => reviewForm.bboxReviewStatus, (status) => { if (status === 'NO_PLATE') Object.assign(coordinateForm, { x1: null, y1: null, x2: null, y2: null }) })

async function search(page = 1) { store.pageInfo.page = page; await store.loadLogs(currentSearch.value) }
async function changePage(event) { store.pageInfo.page = event.page + 1; store.pageInfo.size = event.rows; await store.loadLogs(currentSearch.value) }
function openDetail(log) { return router.push({ name: 'admin-plate-detection-logs', query: { ...route.query, plateDetectionLogId: log.plateDetectionLogId } }) }
function closeDetail() { store.clearDetail(); reviewDialogVisible.value = false; const query = { ...route.query }; delete query.plateDetectionLogId; router.push({ name: 'admin-plate-detection-logs', query }) }
function openReview() {
  const item = store.detail
  if (!item) return
  reviewForm.reviewStatus = item.reviewStatus === 'UNREVIEWED' ? '' : (item.reviewStatus || '')
  reviewForm.correctedPlateNumber = item.correctedPlateNumber || item.detectedPlateNumber || ''
  reviewForm.failureReason = item.failureReason || ''
  reviewForm.trainingUseYn = Boolean(item.trainingUseYn)
  reviewForm.reviewMemo = item.reviewMemo || ''
  const saved = [item.reviewedX1, item.reviewedY1, item.reviewedX2, item.reviewedY2].every(Number.isFinite)
  Object.assign(coordinateForm, saved ? { x1: item.reviewedX1, y1: item.reviewedY1, x2: item.reviewedX2, y2: item.reviewedY2 } : predictedBox.value)
  reviewForm.bboxReviewStatus = item.bboxReviewStatus || (validBox(coordinateForm) ? 'ACCEPTED' : 'NO_PLATE')
  reviewDialogVisible.value = true
}
async function submitReview() {
  const currentId = store.detail?.plateDetectionLogId
  const reviewedBox = reviewForm.bboxReviewStatus === 'NO_PLATE' ? null : { ...coordinateForm }
  if (reviewedBox && !validBox(reviewedBox)) return
  const success = await store.saveReview({ ...reviewForm, reviewedBox }, currentSearch.value)
  if (success) {
    reviewDialogVisible.value = false
    const next = prioritizedLogs.value.find((item) => item.needReview && item.plateDetectionLogId !== currentId)
    if (next) await openDetail(next)
  }
}
async function openNextReview() { if (nextReviewLog.value) await openDetail(nextReviewLog.value) }
function updateReviewedBox(box) {
  Object.assign(coordinateForm, box || { x1: null, y1: null, x2: null, y2: null })
}
function updateBboxStatus(status) {
  reviewForm.bboxReviewStatus = status
  if (status === 'NO_PLATE') updateReviewedBox(null)
}
function validBox(box) { return box && [box.x1, box.y1, box.x2, box.y2].every(Number.isFinite) && box.x2 > box.x1 && box.y2 > box.y1 }
function normalizeLogFilter(value) { return ['FAILED_ONLY', 'NEED_REVIEW_ONLY'].includes(String(value || '').toUpperCase()) ? String(value).toUpperCase() : 'ALL' }
function toApiDate(value) { if (!value) return undefined; const date = new Date(value); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function dateTime(value) { if (!value) return '—'; return String(value).replace('T', ' ').slice(0, 19) }
function confidence(value) { return value == null ? null : Math.max(0, Math.min(100, Number(value) * 100)) }
function percent(value) { const score = confidence(value); return score == null ? '—' : `${score.toFixed(1)}%` }
function confidenceClass(value) { const score = confidence(value); return score == null ? 'unknown' : score < 70 ? 'danger' : score < 90 ? 'warning' : 'normal' }
function reviewPriority(item) { if (item?.success === false && item?.needReview) return { label: '긴급 검토', severity: 'danger', rank: 4 }; if (item?.success === false) return { label: '실패 확인', severity: 'danger', rank: 3 }; if (item?.needReview) return { label: '검토 필요', severity: 'warn', rank: 2 }; return { label: '일반', severity: 'secondary', rank: 1 } }
function detectionRisk(item) { if (!item) return { label: '미확인', severity: 'secondary' }; const scores = [confidence(item.yoloConfidence), confidence(item.ocrConfidence)].filter((value) => value != null); const lowest = scores.length ? Math.min(...scores) : null; if (item.success === false || (item.needReview && lowest != null && lowest < 70)) return { label: '위험', severity: 'danger' }; if (item.needReview || (lowest != null && lowest < 90)) return { label: '주의', severity: 'warn' }; return { label: '정상', severity: 'success' } }
function imageExpiry(value) { if (!value) return { label: '미등록', message: '', severity: 'secondary', className: '' }; const expires = new Date(value); if (Number.isNaN(expires.getTime())) return { label: String(value), message: '', severity: 'secondary', className: '' }; const days = Math.ceil((expires.getTime() - Date.now()) / 86400000); if (days < 0) return { label: `${Math.abs(days)}일 경과`, message: '이미지 보관 기한이 지났습니다. 파일 제공 여부를 확인해 주세요.', severity: 'error', className: 'danger' }; if (days === 0) return { label: '오늘 만료', message: '이미지 보관 기한이 오늘까지입니다. 필요한 검토를 우선 처리해 주세요.', severity: 'warn', className: 'warning' }; if (days <= 7) return { label: `D-${days}`, message: `이미지 보관 기한이 ${days}일 남았습니다. 필요한 검토를 먼저 처리해 주세요.`, severity: 'warn', className: 'warning' }; return { label: `D-${days}`, message: '', severity: 'info', className: 'normal' } }
function reviewLabel(value) { return ({ NORMAL: '정상', FAILED: '실패', SUSPECT: '의심', EXCLUDED: '제외', UNREVIEWED: '미검토' })[value] || '미검토' }
function reviewSeverity(value) { return value === 'FAILED' ? 'danger' : value === 'SUSPECT' || value === 'UNREVIEWED' || !value ? 'warn' : value === 'NORMAL' ? 'success' : 'secondary' }
</script>

<template>
  <section class="plate-admin-page">
    <header class="plate-page-header"><div><p>PLATE RECOGNITION</p><h1>번호판 인식 로그</h1><span>인식 실패와 저신뢰 결과를 검토하고 학습 데이터를 관리합니다.</span></div><Button label="학습 데이터 내보내기" icon="pi pi-download" severity="secondary" outlined :loading="store.downloading" @click="store.exportTrainingData" /></header>
    <Message v-if="store.error" severity="error" :closable="false">{{ store.error }}</Message>
    <Message v-if="store.successMessage" severity="success" :closable="false">{{ store.successMessage }}</Message>

    <section class="plate-summary-grid">
      <article><span class="blue"><i class="pi pi-database" /></span><div><small>전체 로그</small><strong>{{ store.error ? '—' : store.pageInfo.totalElements }}</strong><em>검색 조건 기준</em></div></article>
      <article><span class="red"><i class="pi pi-times-circle" /></span><div><small>인식 실패</small><strong>{{ store.error ? '—' : store.pageCounts.failed }}</strong><em>현재 페이지</em></div></article>
      <article><span class="orange"><i class="pi pi-exclamation-triangle" /></span><div><small>검토 필요</small><strong>{{ store.error ? '—' : store.pageCounts.needReview }}</strong><em>현재 페이지</em></div></article>
      <article><span class="green"><i class="pi pi-check-circle" /></span><div><small>인식 성공</small><strong>{{ store.error ? '—' : store.pageCounts.success }}</strong><em>현재 페이지</em></div></article>
    </section>

    <section class="plate-log-panel">
      <header><div><p>DETECTION LOG</p><h2>탐지 로그 목록</h2></div><span>총 {{ store.error ? '—' : store.pageInfo.totalElements }}건 · 현재 페이지 우선순위 적용</span></header>
      <form class="plate-filter-bar" @submit.prevent="search(1)"><label><small>시작일</small><DatePicker v-model="startDate" date-format="yy-mm-dd" show-icon /></label><label><small>종료일</small><DatePicker v-model="endDate" date-format="yy-mm-dd" show-icon /></label><label class="plate-number-search"><small>번호판</small><span><i class="pi pi-search" /><InputText v-model.trim="plateNumber" placeholder="예: 12가3456" /></span></label><label><small>조회 조건</small><Select v-model="logFilter" :options="filterOptions" option-label="label" option-value="value" /></label><Button label="조회" icon="pi pi-search" type="submit" :loading="store.loading" /></form>
      <div v-if="store.loading && !store.logs.length" class="plate-loading"><ProgressSpinner /><strong>번호판 로그를 조회하고 있습니다.</strong></div>
      <DataTable v-else :value="prioritizedLogs" data-key="plateDetectionLogId" striped-rows scrollable :table-style="{ minWidth: '64rem' }" class="plate-data-table" @row-click="openDetail($event.data)">
        <Column field="plateDetectionLogId" header="ID" style="width: 6rem" />
        <Column header="검토 우선순위"><template #body="{ data }"><Tag :value="reviewPriority(data).label" :severity="reviewPriority(data).severity" /></template></Column>
        <Column field="detectedTime" header="탐지 시간"><template #body="{ data }">{{ dateTime(data.detectedTime) }}</template></Column>
        <Column field="siteName" header="세차장"><template #body="{ data }"><strong>{{ data.siteName || '—' }}</strong><small>{{ data.deviceName || '장비 미확인' }}</small></template></Column>
        <Column field="detectedPlateNumber" header="인식 번호판"><template #body="{ data }"><button type="button" class="detected-plate" @click.stop="openDetail(data)">{{ data.detectedPlateNumber || '인식 실패' }}</button></template></Column>
        <Column field="success" header="인식 결과"><template #body="{ data }"><Tag :value="data.success ? '성공' : '실패'" :severity="data.success ? 'success' : 'danger'" /></template></Column>
        <Column field="needReview" header="검토"><template #body="{ data }"><Tag :value="data.needReview ? '검토 필요' : '확인 완료'" :severity="data.needReview ? 'warn' : 'secondary'" /></template></Column>
        <template #empty><div class="plate-empty"><i class="pi pi-camera" /><strong>{{ store.error ? '로그 데이터를 표시할 수 없습니다.' : '조회된 번호판 로그가 없습니다.' }}</strong></div></template>
      </DataTable>
      <Paginator v-if="store.pageInfo.totalElements" :first="(store.pageInfo.page - 1) * store.pageInfo.size" :rows="store.pageInfo.size" :total-records="store.pageInfo.totalElements" :rows-per-page-options="[10,20,50]" @page="changePage" />
    </section>

    <Dialog v-model:visible="detailVisible" modal class="plate-detail-dialog" :header="store.detail ? `인식 로그 #${store.detail.plateDetectionLogId}` : '번호판 인식 상세'" :style="{ width: 'min(96vw, 1120px)' }">
      <div v-if="store.detailLoading" class="plate-loading detail"><ProgressSpinner /><strong>상세 정보와 이미지를 불러오고 있습니다.</strong></div>
      <div v-else-if="store.detail" class="plate-detail-content">
        <header class="plate-detail-title"><div><span>{{ store.detail.siteName || '세차장 미확인' }} · {{ store.detail.deviceName || '장비 미확인' }}</span><h2>{{ store.detail.detectedPlateNumber || '번호판 인식 실패' }}</h2><small>{{ dateTime(store.detail.detectedTime) }}</small></div><div><Tag :value="detailRisk.label" :severity="detailRisk.severity" /><Tag :value="reviewLabel(store.detail.reviewStatus)" :severity="reviewSeverity(store.detail.reviewStatus)" /><Button :label="isReviewed ? '검토 수정' : '검토하기'" icon="pi pi-pencil" @click="openReview" /><Button label="다음 미검토" icon="pi pi-arrow-right" severity="secondary" outlined :disabled="!nextReviewLog" @click="openNextReview" /></div></header>
        <PlateDetectionPreview :image-url="store.imageUrl" :box="predictedBox" />
        <section class="plate-confidence-grid"><article :class="confidenceClass(store.detail.yoloConfidence)"><header><span>YOLO 신뢰도</span><strong>{{ percent(store.detail.yoloConfidence) }}</strong></header><ProgressBar :value="confidence(store.detail.yoloConfidence) || 0" :show-value="false" /></article><article :class="confidenceClass(store.detail.ocrConfidence)"><header><span>OCR 신뢰도</span><strong>{{ percent(store.detail.ocrConfidence) }}</strong></header><ProgressBar :value="confidence(store.detail.ocrConfidence) || 0" :show-value="false" /></article></section>
        <dl class="plate-meta-grid"><div><dt>세차 기록</dt><dd>{{ store.detail.washHistoryId ? `#${store.detail.washHistoryId} 연결` : '미연결' }}</dd></div><div><dt>파일 만료일</dt><dd :class="['plate-expiry', expiryInfo.className]">{{ dateTime(store.detail.fileExpireTime) }} · {{ expiryInfo.label }}</dd></div><div><dt>검토자</dt><dd>{{ store.detail.reviewerId ? `관리자 #${store.detail.reviewerId}` : '미검토' }}</dd></div><div><dt>검토일</dt><dd>{{ dateTime(store.detail.reviewedAt) }}</dd></div></dl>
        <Message v-if="expiryInfo.message" :severity="expiryInfo.severity" :closable="false"><strong>이미지 보관 기한</strong> · {{ expiryInfo.message }}</Message>
        <Message v-if="store.detail.errorLog" severity="error" :closable="false"><strong>인식 오류</strong> · {{ store.detail.errorLog }}</Message>
      </div>
    </Dialog>

    <Dialog v-model:visible="reviewDialogVisible" modal header="번호판 인식 결과 검토" :style="{ width: 'min(96vw, 1180px)' }">
      <form class="plate-review-form" @submit.prevent="submitReview"><Message severity="info" :closable="false">촬영 이미지를 확인하고 번호판 및 탐지 영역 정보를 확정해 주세요.</Message><div class="plate-review-fields"><label><span>검토 결과</span><Select v-model="reviewForm.reviewStatus" :options="reviewStatusOptions" option-label="label" option-value="value" placeholder="결과 선택" required /></label><label><span>확정 번호판</span><InputText v-model.trim="reviewForm.correctedPlateNumber" placeholder="번호판 입력" /></label><label><span>실패·의심 사유</span><Select v-model="reviewForm.failureReason" :options="failureReasonOptions" option-label="label" option-value="value" show-clear placeholder="사유 선택" /></label><label><span>탐지 영역 상태</span><Select v-model="reviewForm.bboxReviewStatus" :options="bboxStatusOptions" option-label="label" option-value="value" /></label></div><PlateBboxEditor
        :image-url="store.imageUrl"
        :predicted-box="predictedBox"
        :model-value="validBox(coordinateForm) ? { ...coordinateForm } : null"
        :status="reviewForm.bboxReviewStatus"
        :padding-ratio="0.12"
        @update:model-value="updateReviewedBox"
        @update:status="updateBboxStatus"
      /><section v-if="reviewForm.bboxReviewStatus !== 'NO_PLATE'" class="bbox-coordinate-card"><header><div><h3>번호판 영역 좌표</h3><p>드래그 결과를 픽셀 단위로 정밀 보정할 수 있습니다.</p></div></header><div><label>X1<InputNumber v-model="coordinateForm.x1" :min="0" /></label><label>Y1<InputNumber v-model="coordinateForm.y1" :min="0" /></label><label>X2<InputNumber v-model="coordinateForm.x2" :min="0" /></label><label>Y2<InputNumber v-model="coordinateForm.y2" :min="0" /></label></div></section><label class="training-checkbox"><Checkbox v-model="reviewForm.trainingUseYn" binary input-id="training-use" /><span>재학습 데이터로 사용</span></label><label class="review-memo"><span>검토 메모</span><Textarea v-model="reviewForm.reviewMemo" rows="4" maxlength="1000" placeholder="검토 과정에서 확인한 내용을 기록하세요." /></label><footer><Button label="취소" severity="secondary" outlined type="button" @click="reviewDialogVisible = false" /><Button label="검토 결과 저장" icon="pi pi-check" type="submit" :loading="store.reviewLoading" :disabled="!reviewForm.reviewStatus" /></footer></form>
    </Dialog>
  </section>
</template>

