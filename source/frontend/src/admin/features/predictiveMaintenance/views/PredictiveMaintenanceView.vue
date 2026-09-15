<script setup>
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import ToggleSwitch from 'primevue/toggleswitch'
import { computed } from 'vue'
import { usePredictiveMaintenance } from '@/admin/features/predictiveMaintenance/composables/usePredictiveMaintenance'

const modes = [{ label: '운영', value: 'LIVE' }, { label: '시연', value: 'DEMO' }]
const {
  dashboard, selectedMode, telegramEnabled, loading, error, controlBusy,
  selected, detailLoading, shadowPrediction, reversedHistory, detailMetrics,
  alertHistory, newAlerts, pendingAlertCount, alertDialogVisible, telegramMessage, telegramError, acknowledgingAlert,
  sortedEquipment, abnormalEquipment, healthStyle,
  load, changeMode, toggleDemo, openDetail, acknowledgeAlerts,
} = usePredictiveMaintenance()

const selectedIssues = computed(() => selected.value ? issues(selected.value) : [])
const detailVisible = computed({
  get: () => Boolean(selected.value),
  set: (visible) => { if (!visible) selected.value = null },
})
const healthDisplayStyle = computed(() => error.value ? { background: '#e5e9f0' } : healthStyle.value)

function riskLabel(level) { return ({ NORMAL: '정상', WARNING: '주의', CRITICAL: '위험' })[level] || '미확인' }
function riskSeverity(level) { return level === 'CRITICAL' ? 'danger' : level === 'WARNING' ? 'warn' : 'success' }
function riskClass(level) { return String(level || 'normal').toLowerCase() }
function riskColor(level) { return level === 'CRITICAL' ? '#dc2626' : level === 'WARNING' ? '#d97706' : '#16835f' }
function gaugeStyle(item) { const score = Math.max(0, Math.min(100, Number(item.riskScore || 0))); return { background: `conic-gradient(${riskColor(item.riskLevel)} ${score * 3.6}deg,#e7ebf1 0)` } }
function metricStyle(metric) { const colors = { danger: '#dc2626', warning: '#d97706', normal: '#16835f', info: '#2563eb' }; return { background: `conic-gradient(${colors[metric.tone] || colors.info} ${metric.percent * 3.6}deg,#e7ebf1 0)` } }
function score(value) { return Number(value || 0).toFixed(1) }
function dateTime(value) { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(date) }
function issueSummary(item) { return issues(item)[0]?.label || '이상 없음' }
function issues(item) { const result = []; if (item.successRate != null && item.successRate < 95) result.push({ label: '통신 성공률 저하', value: `${item.successRate}%` }); if (item.responseTimeMs != null && item.responseTimeMs > 200) result.push({ label: '응답시간 증가', value: `${item.responseTimeMs} ms` }); if (item.packetLossRate != null && item.packetLossRate > 1) result.push({ label: '패킷 손실 증가', value: `${item.packetLossRate}%` }); if (item.signalStrengthDbm != null && item.signalStrengthDbm < -70) result.push({ label: '신호 세기 약함', value: `${item.signalStrengthDbm} dBm` }); if (item.temperatureC != null && item.temperatureC > 45) result.push({ label: '장비 온도 상승', value: `${item.temperatureC}°C` }); return result }
function trendWidth(point) { return `${Math.max(2, Math.min(100, Number(point.riskScore || 0)))}%` }
function shadowStatusLabel(status) { return ({ SCORED: 'v2 산정 완료', WARMING_UP: '24시간 수집 중', UNKNOWN_DEVICE: '기준선 미등록', FAILED: '호출 실패' })[status] || '확인 중' }
function sourceLabel(item) { return ({ V2_ACTIVE: 'v2 주 판정', V1_FALLBACK: 'v1 자동 대체', V1_ACTIVE: 'v1 주 판정', V2_UNAVAILABLE: 'v2 산정 보류' })[item?.predictionSource] || item?.modelVersion || '모델 확인 중' }
function sourceSeverity(item) { return item?.predictionSource === 'V2_ACTIVE' ? 'success' : item?.predictionSource === 'V1_FALLBACK' ? 'warn' : 'secondary' }
</script>

<template>
  <section class="pm-admin-page">
    <header class="pm-page-header">
      <div><p>PREDICTIVE MAINTENANCE</p><h1>설비 상태 모니터링</h1><span>장비별 위험도와 통신·센서 이상을 확인합니다.</span></div>
      <div class="pm-header-controls">
        <SelectButton :model-value="selectedMode" :options="modes" option-label="label" option-value="value" :allow-empty="false" @update:model-value="changeMode" />
        <div v-if="selectedMode === 'DEMO'" class="telegram-control"><ToggleSwitch v-model="telegramEnabled" input-id="telegram-alert" /><label for="telegram-alert">텔레그램 알림</label></div>
        <Button v-if="selectedMode === 'DEMO'" :label="dashboard.simulationPaused ? '알림 확인 대기' : dashboard.simulationRunning ? '시연 정지' : '시연 시작'" :icon="dashboard.simulationPaused ? 'pi pi-pause' : dashboard.simulationRunning ? 'pi pi-stop' : 'pi pi-play'" :severity="dashboard.simulationRunning ? 'danger' : 'primary'" :loading="controlBusy" :disabled="dashboard.simulationPaused" @click="toggleDemo" />
        <RouterLink :to="{ name: 'admin-predictive-maintenance-logs', query: { mode: selectedMode } }"><Button label="데이터 로그" icon="pi pi-database" severity="secondary" outlined /></RouterLink>
        <Button label="새로고침" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load()" />
      </div>
    </header>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="telegramMessage" :severity="telegramError ? 'error' : 'success'" :closable="false">{{ telegramMessage }}</Message>

    <div v-if="loading && !dashboard.collectedAt" class="pm-full-state"><ProgressSpinner /><strong>설비 데이터를 확인하고 있습니다.</strong></div>
    <template v-else>
      <section class="pm-overview-grid">
        <article class="pm-severity-card critical"><span>위험 장비</span><strong>{{ error ? '—' : dashboard.criticalCount }}</strong><small>즉시 점검 필요</small></article>
        <article class="pm-severity-card warning"><span>주의 장비</span><strong>{{ error ? '—' : dashboard.warningCount }}</strong><small>상태 확인 필요</small></article>
        <article class="pm-health-card">
          <div><p>FLEET HEALTH</p><h2>전체 장비 상태</h2><small>{{ dateTime(dashboard.collectedAt) }}</small></div>
          <div class="pm-health-donut" :style="healthDisplayStyle"><span><strong>{{ error ? '—' : dashboard.totalEquipment }}</strong><small>전체</small></span></div>
          <dl><div class="critical"><dt>위험</dt><dd>{{ error ? '—' : dashboard.criticalCount }}</dd></div><div class="warning"><dt>주의</dt><dd>{{ error ? '—' : dashboard.warningCount }}</dd></div><div class="normal"><dt>정상</dt><dd>{{ error ? '—' : dashboard.normalCount }}</dd></div></dl>
        </article>
        <article class="pm-alert-card"><header><div><p>RECENT ALERT</p><h2>최근 이상 알림</h2></div><Tag :value="`${alertHistory.length}건`" severity="secondary" /></header><div v-if="alertHistory.length" class="pm-alert-list"><button v-for="item in alertHistory.slice(0,4)" :key="item.eventKey" type="button" @click="openDetail(item)"><i :class="riskClass(item.riskLevel)" /><span><strong>{{ item.equipmentId }}</strong><small>{{ item.alertLabel }} · {{ item.issue }}</small></span><b>{{ score(item.riskScore) }}</b></button></div><div v-else class="pm-all-clear"><i class="pi pi-check-circle" /><span>이번 화면에서 새로 감지된 알림이 없습니다.</span></div></article>
      </section>

      <section class="pm-equipment-section">
        <header><div><p>EQUIPMENT</p><h2>장비별 모니터링</h2></div><span>문제 장비 우선 · {{ error ? '—' : dashboard.totalEquipment }}대</span></header>
        <div v-if="sortedEquipment.length" class="pm-equipment-grid">
          <button v-for="item in sortedEquipment" :key="item.equipmentId" type="button" class="pm-equipment-card" :class="riskClass(item.riskLevel)" @click="openDetail(item)">
            <header><strong>{{ item.equipmentId }}</strong><Tag :value="riskLabel(item.riskLevel)" :severity="riskSeverity(item.riskLevel)" /></header>
            <div class="pm-risk-gauge" :style="gaugeStyle(item)"><span><strong>{{ score(item.riskScore) }}</strong><small>위험도</small></span></div>
            <div class="pm-equipment-meta"><span>{{ item.locationId || '위치 미지정' }}</span><span>{{ sourceLabel(item) }}</span></div>
            <p>{{ issueSummary(item) }}</p>
          </button>
        </div>
        <div v-else class="pm-full-state compact"><i class="pi pi-chart-line" /><strong>{{ error ? '장비 데이터를 표시할 수 없습니다.' : '수집된 장비 데이터가 없습니다.' }}</strong></div>
      </section>
    </template>

    <Dialog v-model:visible="alertDialogVisible" modal header="이상 장비 감지" class="pm-alert-dialog" :style="{ width: 'min(92vw, 560px)' }" :closable="false" :dismissable-mask="false" :close-on-escape="false">
      <p class="dialog-description">시연을 일시정지했습니다. 현재 알림을 확인한 뒤 계속 진행해 주세요.<br><strong v-if="pendingAlertCount">대기 중인 추가 알림 {{ pendingAlertCount }}건</strong></p>
      <div class="dialog-alert-list"><button v-for="item in newAlerts" :key="item.eventKey" type="button" :class="riskClass(item.riskLevel)" @click="openDetail(item)"><span><strong>{{ item.equipmentId }}</strong><small>{{ item.issue }}</small></span><b>{{ item.alertLabel }} · {{ score(item.riskScore) }}</b></button></div>
      <template #footer>
        <Button :label="pendingAlertCount ? `확인 후 다음 알림 (${pendingAlertCount}건)` : '확인 후 시연 계속'" icon="pi pi-check" :loading="acknowledgingAlert" @click="acknowledgeAlerts" />
      </template>
    </Dialog>

    <Dialog v-model:visible="detailVisible" modal class="pm-detail-dialog" :header="selected ? `${selected.equipmentId} 설비 진단` : '설비 진단'" :style="{ width: 'min(96vw, 1180px)' }">
      <div v-if="selected" class="pm-detail-content">
        <header class="pm-detail-header"><div><span>{{ selected.equipmentType || '장비' }} · {{ selected.locationId || '위치 미지정' }}</span><small>{{ dateTime(selected.collectedAt) }}</small></div><Tag :value="riskLabel(selected.riskLevel)" :severity="riskSeverity(selected.riskLevel)" /></header>
        <div v-if="detailLoading" class="pm-full-state compact"><ProgressSpinner /><strong>최근 이력을 불러오고 있습니다.</strong></div>
        <template v-else>
          <section class="pm-model-comparison">
            <article><header><span>현재 주 판정</span><Tag :value="sourceLabel(selected)" :severity="sourceSeverity(selected)" /></header><strong>{{ score(selected.riskScore) }}점 · {{ riskLabel(selected.riskLevel) }}</strong><small>{{ selected.modelVersion || '모델 미확인' }} · {{ selected.fallbackReason || 'v2 13개 특성으로 대시보드와 알림에 사용' }}</small></article>
            <article><header><span>v2 데이터 계약</span><Tag :value="shadowStatusLabel(shadowPrediction?.status || selected.v2Status)" :severity="(shadowPrediction?.status || selected.v2Status) === 'SCORED' ? 'info' : 'secondary'" /></header><strong v-if="shadowPrediction?.status === 'SCORED'">{{ score(shadowPrediction.riskScore) }}점 · {{ riskLabel(shadowPrediction.riskLevel) }}</strong><strong v-else-if="selected.predictionSource === 'V2_ACTIVE'">{{ score(selected.riskScore) }}점 · {{ riskLabel(selected.riskLevel) }}</strong><strong v-else>점수 미산정</strong><small>{{ shadowPrediction?.message || selected.fallbackReason || `${shadowPrediction?.modelVersion || selected.modelVersion || 'logistic-v2-active'} · 13특성 · 최근 ${shadowPrediction?.historyRows || 0}개 연속 로그` }}</small></article>
          </section>
          <section class="pm-metric-grid"><article v-for="metric in detailMetrics" :key="metric.label"><h3>{{ metric.label }}</h3><div class="pm-metric-gauge" :style="metricStyle(metric)"><span><strong>{{ metric.value }}</strong><small>{{ metric.unit }}</small></span></div></article></section>
          <section v-if="selectedIssues.length" class="pm-issue-strip"><span v-for="issue in selectedIssues" :key="issue.label"><i class="pi pi-exclamation-triangle" />{{ issue.label }} <strong>{{ issue.value }}</strong></span></section>
          <section class="pm-history-layout"><article><header><h3>시간별 위험도 추이</h3><span>최근 {{ reversedHistory.length }}개 시점</span></header><div class="pm-trend-chart"><div v-for="point in reversedHistory.slice().reverse()" :key="point.collectedAt"><time>{{ dateTime(point.collectedAt) }}</time><span><i :class="riskClass(point.riskLevel)" :style="{ width: trendWidth(point) }" /></span><strong>{{ score(point.riskScore) }}</strong></div><p v-if="!reversedHistory.length">조회된 이력이 없습니다.</p></div></article><article><header><h3>설비 상태 로그</h3><span>{{ issueSummary(selected) }}</span></header><div class="pm-history-log"><div v-for="point in reversedHistory" :key="point.collectedAt"><time>{{ dateTime(point.collectedAt) }}</time><Tag :value="riskLabel(point.riskLevel)" :severity="riskSeverity(point.riskLevel)" /><strong>{{ score(point.riskScore) }}</strong><span>{{ issueSummary(point) }}</span></div><p v-if="!reversedHistory.length">조회된 이력이 없습니다.</p></div></article></section>
        </template>
      </div>
    </Dialog>
  </section>
</template>

