<script setup>
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminDashboard } from '@/admin/features/admin/composables/useAdminDashboard'
import { openPopupWindow } from '@/admin/shared/browser/openPopupWindow'

const router = useRouter()
const modeOptions = [
  { label: '운영', value: 'LIVE' },
  { label: '시연', value: 'DEMO' },
]

const {
  equipment, latestPlate, latestPlateImageUrl,
  loading, loaded, errors, selectedMode, lastUpdatedAt, refreshing, demoBusy,
  problemEquipment, criticalEquipment, unansweredInquiries, oldInquiries,
  plateReviewTotal, activeSites, inactiveSites, washSites,
  actionGroups, actionItems, actionItemCount, actionSummary,
  refreshAll, changeMode, toggleDemo,
} = useAdminDashboard()

const summaryCards = computed(() => [
  { label: '위험 장비', value: loading.equipment ? null : criticalEquipment.value, available: loaded.equipment, icon: 'pi pi-exclamation-triangle', tone: 'danger', error: errors.equipment, target: { name: 'admin-predictive-maintenance' } },
  { label: '미답변 문의', value: loading.inquiries ? null : unansweredInquiries.value.length, available: loaded.inquiries, icon: 'pi pi-comment', tone: 'warning', error: errors.inquiries, target: { name: 'admin-inquiries', query: { status: 'WAITING' } } },
  { label: '검토 대기', value: loading.plates ? null : plateReviewTotal.value, available: loaded.plates, icon: 'pi pi-search', tone: 'warning', error: errors.plates, target: { name: 'admin-plate-detection-logs', query: { filter: 'NEED_REVIEW_ONLY' } } },
  { label: '비활성 세차장', value: loading.washSites ? null : inactiveSites.value.length, available: loaded.washSites, icon: 'pi pi-building', tone: 'info', error: errors.washSites, target: { name: 'admin-wash-sites', query: { status: 'INACTIVE' } } },
])
const selectedActionType = ref('ALL')
const actionCategories = computed(() => [
  { type: 'equipment', label: '장비', count: actionSummary.value.equipment, icon: 'pi pi-desktop', tone: 'danger' },
  { type: 'inquiry', label: '장기 문의', count: actionSummary.value.inquiries, icon: 'pi pi-comment', tone: 'warning' },
  { type: 'plate', label: '번호판', count: actionSummary.value.plates, icon: 'pi pi-camera', tone: 'review' },
  { type: 'site', label: '사업장', count: actionSummary.value.sites, icon: 'pi pi-building', tone: 'site' },
])
const selectedCategory = computed(() => actionCategories.value.find((item) => item.type === selectedActionType.value) || null)
const filteredActionItems = computed(() => selectedActionType.value === 'ALL'
  ? actionItems.value
  : (actionGroups.value[selectedActionType.value] || []).slice(0, 8))
const actionDataUnavailable = computed(() => Boolean(errors.equipment && errors.inquiries && errors.plates && errors.washSites))
const actionDataPartial = computed(() => !actionDataUnavailable.value && Boolean(errors.equipment || errors.inquiries || errors.plates || errors.washSites))

function openSummaryCard(card) {
  return router.push(card.target)
}

function selectActionType(type) {
  selectedActionType.value = selectedActionType.value === type ? 'ALL' : type
}

function openSelectedActionPage() {
  const targets = {
    equipment: { name: 'admin-predictive-maintenance' },
    inquiry: { name: 'admin-inquiries', query: { status: 'WAITING' } },
    plate: { name: 'admin-plate-detection-logs', query: { filter: 'NEED_REVIEW_ONLY' } },
    site: { name: 'admin-wash-sites', query: { status: 'INACTIVE' } },
  }
  const target = targets[selectedActionType.value]
  if (target) router.push(target)
}

function openEquipment(item) {
  const query = {
    mode: selectedMode.value,
    ...(item?.equipmentId ? { equipmentId: item.equipmentId } : {}),
    ...(item?.collectedAt || equipment.collectedAt ? { collectedAt: item?.collectedAt || equipment.collectedAt } : {}),
    ...(equipment.demoRunId ? { demoRunId: equipment.demoRunId } : {}),
  }
  const url = router.resolve({ name: 'admin-predictive-maintenance', query }).href
  openPopupWindow(url, { name: 'adminPredictiveMaintenance', width: 1500, height: 950 })
}

function openAction(action) {
  if (action.type === 'equipment') return openEquipment(action.item)
  if (action.type === 'inquiry') return router.push({ name: 'admin-inquiry-detail', params: { inquiryId: action.item.inquiryId } })
  if (action.type === 'plate') return router.push({ name: 'admin-plate-detection-logs', query: { plateDetectionLogId: action.item.plateDetectionLogId } })
  return router.push({ name: 'admin-wash-sites', query: { washSiteId: action.item.washSiteId } })
}

function openLatestPlate() {
  if (!latestPlate.value?.plateDetectionLogId) return
  router.push({ name: 'admin-plate-detection-logs', query: { plateDetectionLogId: latestPlate.value.plateDetectionLogId } })
}

function riskLabel(level) {
  return level === 'CRITICAL' ? '위험' : level === 'WARNING' ? '주의' : '정상'
}

function riskTone(level) {
  return String(level || 'normal').toLowerCase()
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(date)
}

function formatShortDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date)
}

function waitingText(value) {
  if (!value) return '대기시간 미상'
  const hours = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 3600000))
  if (hours >= 24) return `${Math.floor(hours / 24)}일 ${hours % 24}시간 대기`
  if (hours > 0) return `${hours}시간 대기`
  return '1시간 미만 대기'
}
</script>

<template>
  <section class="ops-dashboard">
    <header class="ops-page-header">
      <div>
        <h1>통합 운영 대시보드</h1>
        <p>지금 확인하고 처리해야 할 운영 문제를 우선 표시합니다.</p>
      </div>
      <div class="dashboard-refresh">
        <span aria-live="polite"><i class="pi pi-clock" /> {{ refreshing ? '정보 갱신 중' : `최종 갱신 ${formatDateTime(lastUpdatedAt)}` }}</span>
        <Button icon="pi pi-refresh" label="새로고침" severity="secondary" outlined size="small" :loading="refreshing" :disabled="refreshing" @click="refreshAll()" />
      </div>
    </header>

    <div class="ops-summary-grid">
      <button v-for="card in summaryCards" :key="card.label" type="button" class="ops-summary-card" :class="`tone-${card.tone}`" :aria-label="`${card.label} ${card.available ? card.value : '조회 불가'}건 관리 페이지로 이동`" @click="openSummaryCard(card)">
        <span class="summary-card-icon"><i :class="card.icon" /></span>
        <div><span>{{ card.label }}</span><small v-if="card.error">{{ card.available ? '갱신 실패 · 이전 값 표시' : '연결 오류' }}</small></div>
        <ProgressSpinner v-if="card.value === null && !card.error" class="summary-spinner" stroke-width="5" />
        <strong v-else>{{ card.available ? card.value : '—' }}</strong>
        <i class="pi pi-angle-right summary-card-arrow" aria-hidden="true" />
      </button>
    </div>

    <div class="ops-dashboard-grid">
      <article class="ops-panel equipment-monitor">
        <header class="ops-panel-header">
          <div><h2>장비 상태 모니터링</h2></div>
          <Button label="전체 화면" icon="pi pi-external-link" icon-pos="right" text size="small" @click="openEquipment()" />
        </header>
        <div class="equipment-controls">
          <SelectButton :model-value="selectedMode" :options="modeOptions" option-label="label" option-value="value" :allow-empty="false" @update:model-value="changeMode" />
          <span>{{ selectedMode === 'LIVE' ? '운영 데이터' : equipment.simulationRunning ? '시연 재생 중' : '시연 정지' }}</span>
          <Button v-if="selectedMode === 'DEMO'" :label="equipment.simulationRunning ? '시연 정지' : '시연 시작'" :icon="equipment.simulationRunning ? 'pi pi-stop' : 'pi pi-play'" :severity="equipment.simulationRunning ? 'danger' : 'primary'" size="small" :loading="demoBusy" @click="toggleDemo" />
        </div>
        <div v-if="loading.equipment" class="panel-state"><ProgressSpinner /><span>장비 상태 확인 중</span></div>
        <div v-else-if="errors.equipment && !loaded.equipment" class="panel-state error"><i class="pi pi-exclamation-circle" /><strong>장비 연결 오류</strong><span>{{ errors.equipment }}</span><Button label="다시 시도" size="small" text @click="refreshAll()" /></div>
        <template v-else>
          <div class="equipment-overview">
            <div class="equipment-total"><strong>{{ equipment.totalEquipment }}</strong><span>전체 장비</span><small>{{ formatDateTime(equipment.collectedAt) }}</small></div>
            <dl>
              <div class="normal"><dt>정상</dt><dd>{{ equipment.normalCount }}</dd></div>
              <div class="warning"><dt>주의</dt><dd>{{ equipment.warningCount }}</dd></div>
              <div class="critical"><dt>위험</dt><dd>{{ equipment.criticalCount }}</dd></div>
            </dl>
          </div>
          <div v-if="problemEquipment.length" class="problem-equipment-list">
            <button v-for="item in problemEquipment.slice(0, 12)" :key="item.equipmentId" type="button" :class="riskTone(item.riskLevel)" @click="openEquipment(item)">
              <i /><span><strong>{{ item.equipmentId }}</strong><small>{{ riskLabel(item.riskLevel) }} · 경고 {{ item.warningEventCount || 0 }}건</small></span><i class="pi pi-angle-right" />
            </button>
          </div>
          <div v-else class="panel-state success"><i class="pi pi-check-circle" /><strong>{{ equipment.totalEquipment ? '문제 장비가 없습니다.' : '수집된 장비 데이터가 없습니다.' }}</strong><span>{{ equipment.totalEquipment ? '현재 주의 또는 위험 상태가 감지되지 않았습니다.' : '운영 데이터가 수집되면 이곳에 표시됩니다.' }}</span></div>
        </template>
      </article>

      <article class="ops-panel urgent-panel">
        <header class="ops-panel-header"><div><h2>처리 대기 업무</h2></div><Tag v-if="actionDataUnavailable" value="조회 불가" severity="danger" /><button v-else type="button" class="action-total-filter" :class="{ active: selectedActionType === 'ALL', partial: actionDataPartial }" :aria-pressed="selectedActionType === 'ALL'" @click="selectedActionType = 'ALL'">{{ actionDataPartial ? '일부 갱신 실패' : `전체 ${actionItemCount}건` }}</button></header>
        <div v-if="!actionDataUnavailable" class="action-category-summary">
          <button v-for="category in actionCategories" :key="category.type" type="button" :class="[category.tone, { active: selectedActionType === category.type }]" :aria-pressed="selectedActionType === category.type" :aria-label="`${category.label} ${category.count}건 필터`" @click="selectActionType(category.type)"><i :class="category.icon" /><span>{{ category.label }}</span><strong>{{ category.count }}</strong></button>
        </div>
        <div v-if="actionDataUnavailable" class="panel-state error compact"><i class="pi pi-exclamation-circle" /><strong>처리 목록을 집계할 수 없습니다.</strong><span>일부 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</span></div>
        <div v-else-if="filteredActionItems.length" class="action-list">
          <button v-for="action in filteredActionItems" :key="action.key" type="button" :class="`tone-${action.tone}`" @click="openAction(action)">
            <span class="action-icon"><i :class="action.icon" /></span><span class="action-main"><strong>{{ action.title }} <em>{{ action.priorityLabel }}</em></strong><small>{{ action.detail }}</small><span><b>{{ action.owner }}</b> · {{ action.statusText || waitingText(action.wait) }}</span></span><i class="pi pi-angle-right" />
          </button>
        </div>
        <div v-else class="panel-state success compact"><i class="pi pi-check-circle" /><strong>{{ selectedCategory ? `${selectedCategory.label} 처리 대기 업무가 없습니다.` : '긴급 처리 항목이 없습니다.' }}</strong><span>연결된 데이터 기준입니다.</span></div>
        <button v-if="selectedCategory && selectedCategory.count" type="button" class="action-filter-footer" @click="openSelectedActionPage">전체 {{ selectedCategory.label }} {{ selectedCategory.count }}건 보기 <i class="pi pi-arrow-right" /></button>
      </article>

      <article class="ops-panel site-panel">
        <header class="ops-panel-header"><div><h2>세차장 운영 현황</h2></div><RouterLink to="/admin/wash-sites">관리 →</RouterLink></header>
        <div v-if="loading.washSites" class="panel-state compact"><ProgressSpinner /></div>
        <div v-else-if="errors.washSites && !loaded.washSites" class="panel-state error compact"><i class="pi pi-exclamation-circle" /><span>{{ errors.washSites }}</span></div>
        <template v-else>
          <div class="site-metrics"><div><span>전체</span><strong>{{ washSites.length }}</strong></div><div class="active"><span>운영 가능</span><strong>{{ activeSites.length }}</strong></div><div class="inactive"><span>비활성</span><strong>{{ inactiveSites.length }}</strong></div></div>
          <ul class="site-status-list"><li v-for="site in inactiveSites.slice(0, 4)" :key="site.washSiteId"><span><i />{{ site.siteName }}</span><Tag value="비활성" severity="secondary" /></li><li v-if="!inactiveSites.length"><span class="all-normal"><i class="pi pi-check" /> 비활성 세차장이 없습니다.</span></li></ul>
        </template>
      </article>

      <article class="ops-panel plate-panel">
        <header class="ops-panel-header"><div><h2>최근 번호판 인식</h2></div><RouterLink to="/admin/plate-detection-logs">전체 로그 →</RouterLink></header>
        <div v-if="loading.plates" class="panel-state compact"><ProgressSpinner /></div>
        <div v-else-if="errors.plates && !loaded.plates" class="panel-state error compact"><i class="pi pi-exclamation-circle" /><span>{{ errors.plates }}</span></div>
        <button v-else-if="latestPlate" type="button" class="latest-plate-card" @click="openLatestPlate">
          <div class="plate-image"><img v-if="latestPlateImageUrl" :src="latestPlateImageUrl" alt="최근 번호판 인식 차량" /><i v-else class="pi pi-car" /></div>
          <div class="plate-content"><div><Tag :value="latestPlate.needReview ? '검토 필요' : latestPlate.success ? '인식 성공' : '인식 실패'" :severity="latestPlate.needReview ? 'warn' : latestPlate.success ? 'success' : 'danger'" /><time>{{ formatShortDate(latestPlate.detectedTime) }}</time></div><strong>{{ latestPlate.detectedPlateNumber || '번호판 미인식' }}</strong><dl><div><dt>세차장</dt><dd>{{ latestPlate.siteName || '—' }}</dd></div><div><dt>장비</dt><dd>{{ latestPlate.deviceName || '—' }}</dd></div></dl></div>
        </button>
        <div v-else class="panel-state compact"><i class="pi pi-camera" /><strong>인식 기록이 없습니다.</strong></div>
      </article>

      <article class="ops-panel inquiry-panel">
        <header class="ops-panel-header"><div><h2>최근 미답변 문의</h2></div><div class="inquiry-counts"><span>미답변 <strong>{{ loaded.inquiries ? unansweredInquiries.length : '—' }}</strong></span><span>24시간 이상 <strong>{{ loaded.inquiries ? oldInquiries.length : '—' }}</strong></span><RouterLink to="/admin/inquiries">전체 문의 →</RouterLink></div></header>
        <div v-if="loading.inquiries" class="panel-state compact"><ProgressSpinner /></div>
        <div v-else-if="errors.inquiries && !loaded.inquiries" class="panel-state error compact"><i class="pi pi-exclamation-circle" /><span>{{ errors.inquiries }}</span></div>
        <div v-else-if="unansweredInquiries.length" class="inquiry-table"><button v-for="item in unansweredInquiries.slice(0, 6)" :key="item.inquiryId" type="button" @click="router.push({ name: 'admin-inquiry-detail', params: { inquiryId: item.inquiryId } })"><Tag :value="item.inquiryType || '문의'" severity="secondary" /><span class="writer-role">{{ item.writerRole || '—' }}</span><strong>{{ item.title }}</strong><span>{{ item.writerName || item.writerId || '—' }}</span><time :class="{ overdue: waitingText(item.createdAt).includes('일') }">{{ waitingText(item.createdAt) }}</time><i class="pi pi-angle-right" /></button></div>
        <div v-else class="panel-state success compact"><i class="pi pi-check-circle" /><strong>미답변 문의가 없습니다.</strong></div>
      </article>
    </div>
  </section>
</template>

