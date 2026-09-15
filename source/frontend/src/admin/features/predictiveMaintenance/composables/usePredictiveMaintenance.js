import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  getDashboardByMode,
  getEquipmentAlerts,
  getEquipmentHistory,
  getEquipmentShadow,
  sendTelegramAlerts,
  pauseDemo,
  resumeDemo,
  startDemo,
  stopDemo,
  stopDemoOnPageExit,
} from '@/admin/features/predictiveMaintenance/api/predictiveMaintenanceApi'

const createDashboard = () => ({
  mode: 'LIVE', demoRunId: null, simulationRunning: false, simulationPaused: false, telegramEnabled: false,
  collectedAt: null, totalEquipment: 0, normalCount: 0, warningCount: 0, criticalCount: 0, equipment: [],
})

export function usePredictiveMaintenance() {
  const route = useRoute()
  const dashboard = reactive(createDashboard())
  const selectedMode = ref('LIVE')
  const telegramEnabled = ref(false)
  const loading = ref(true)
  const error = ref('')
  const controlBusy = ref(false)
  const selected = ref(null)
  const detailLoading = ref(false)
  const detailHistory = ref([])
  const shadowPrediction = ref(null)
  const alertHistory = ref([])
  const newAlerts = ref([])
  const pendingAlerts = ref([])
  const alertDialogVisible = ref(false)
  const telegramMessage = ref('')
  const telegramError = ref(false)
  const acknowledgingAlert = ref(false)
  let pollTimer = null
  let presenceTimer = null
  let viewerChannel = null
  let disposed = false
  const viewerId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
  const activeViewers = new Map()

  const sortedEquipment = computed(() => [...(dashboard.equipment || [])].sort((a, b) =>
    riskRank(b) - riskRank(a) || String(a.equipmentId || '').localeCompare(String(b.equipmentId || ''), 'ko', { numeric: true }),
  ))
  const abnormalEquipment = computed(() => sortedEquipment.value.filter((item) => item.riskLevel !== 'NORMAL'))
  const pendingAlertCount = computed(() => pendingAlerts.value.length)
  const reversedHistory = computed(() => [...detailHistory.value].reverse())
  const healthStyle = computed(() => {
    const total = Math.max(1, Number(dashboard.totalEquipment || 0))
    const criticalEnd = Number(dashboard.criticalCount || 0) / total * 360
    const warningEnd = criticalEnd + Number(dashboard.warningCount || 0) / total * 360
    return { background: `conic-gradient(#dc2626 0 ${criticalEnd}deg,#d97706 ${criticalEnd}deg ${warningEnd}deg,#16835f ${warningEnd}deg 360deg)` }
  })
  const detailMetrics = computed(() => selected.value ? [
    metric('지난 경고', selected.value.warningEventCount || 0, '건', Math.min(100, Number(selected.value.warningEventCount || 0) * 10), Number(selected.value.warningEventCount || 0) ? 'warning' : 'normal'),
    metric('위험도', number(selected.value.riskScore), '/ 100', selected.value.riskScore, riskTone(selected.value.riskLevel)),
    metric('통신 성공률', number(selected.value.successRate), '%', selected.value.successRate, Number(selected.value.successRate) < 95 ? 'warning' : 'normal'),
    metric('응답시간', number(selected.value.responseTimeMs), 'ms', Number(selected.value.responseTimeMs) / 5, Number(selected.value.responseTimeMs) > 200 ? 'danger' : 'info'),
    metric('패킷 손실', number(selected.value.packetLossRate), '%', Number(selected.value.packetLossRate) * 10, Number(selected.value.packetLossRate) > 1 ? 'warning' : 'normal'),
    metric('신호 세기', number(selected.value.signalStrengthDbm), 'dBm', ((Number(selected.value.signalStrengthDbm) || -100) + 100) / 70 * 100, Number(selected.value.signalStrengthDbm) < -70 ? 'danger' : 'info'),
    metric('장비 온도', number(selected.value.temperatureC), '°C', Number(selected.value.temperatureC) / 60 * 100, Number(selected.value.temperatureC) > 45 ? 'warning' : 'normal'),
  ] : [])

  async function load({ detectAlerts = false } = {}) {
    if (loading.value && detectAlerts) return
    const previous = new Map((dashboard.equipment || []).map((item) => [item.equipmentId, alertRank(item)]))
    loading.value = true
    error.value = ''
    try {
      const response = await getDashboardByMode(selectedMode.value)
      Object.assign(dashboard, createDashboard(), response)
      telegramEnabled.value = Boolean(response.telegramEnabled || telegramEnabled.value)

      if (detectAlerts && previous.size) {
        const detected = (response.equipment || []).flatMap((item) => {
          const rank = alertRank(item)
          if (rank <= (previous.get(item.equipmentId) || 0)) return []
          return [{ ...item, eventKey: `${response.demoRunId || 'live'}-${item.equipmentId}-${item.collectedAt}-${rank}`, alertLabel: alertLabel(rank) }]
        })
        if (detected.length) await handleNewAlerts(detected)
      }

      if (selected.value) await syncSelected()
    } catch (exception) {
      error.value = apiMessage(exception, '예지보전 데이터를 불러오지 못했습니다.')
    } finally {
      loading.value = false
      schedulePoll()
    }
  }

  async function changeMode(mode) {
    if (selectedMode.value === 'DEMO' && mode !== 'DEMO' && demoActive() && !hasOtherViewer()) {
      await stopDemo().catch(() => {})
    }
    selectedMode.value = mode
    selected.value = null
    detailHistory.value = []
    shadowPrediction.value = null
    alertDialogVisible.value = false
    newAlerts.value = []
    pendingAlerts.value = []
    await load()
  }

  async function toggleDemo() {
    if (controlBusy.value) return
    controlBusy.value = true
    error.value = ''
    telegramMessage.value = ''
    try {
      if (demoActive()) await stopDemo()
      else await startDemo(telegramEnabled.value)
      pendingAlerts.value = []
      newAlerts.value = []
      alertDialogVisible.value = false
      await load()
    } catch (exception) {
      error.value = apiMessage(exception, '시연 상태를 변경하지 못했습니다.')
    } finally {
      controlBusy.value = false
    }
  }

  async function openDetail(item) {
    selected.value = item
    detailLoading.value = true
    detailHistory.value = []
    shadowPrediction.value = null
    try {
      const [historyResult, alertsResult, shadowResult] = await Promise.allSettled([
        getEquipmentHistory(item.equipmentId, item.collectedAt || dashboard.collectedAt, 24, selectedMode.value),
        getEquipmentAlerts(item.equipmentId, selectedMode.value, dashboard.demoRunId),
        getEquipmentShadow(item.equipmentId, item.collectedAt || dashboard.collectedAt, selectedMode.value),
      ])
      if (historyResult.status === 'rejected') throw historyResult.reason
      detailHistory.value = historyResult.value || []
      const alerts = alertsResult.status === 'fulfilled' ? alertsResult.value || [] : []
      selected.value = { ...item, warningEventCount: alerts.length }
      shadowPrediction.value = shadowResult.status === 'fulfilled'
        ? shadowResult.value
        : { status: 'FAILED', message: apiMessage(shadowResult.reason, 'v2 입력 상태를 확인하지 못했습니다.') }
    } catch (exception) {
      error.value = apiMessage(exception, '장비 시간별 기록을 불러오지 못했습니다.')
    } finally {
      detailLoading.value = false
    }
  }

  async function syncSelected() {
    const current = dashboard.equipment?.find((item) => item.equipmentId === selected.value?.equipmentId)
    if (!current) return
    selected.value = { ...selected.value, ...current }
    const nextHistory = detailHistory.value.filter((item) => item.collectedAt !== current.collectedAt)
    nextHistory.push(current)
    detailHistory.value = nextHistory.slice(-24)
  }

  async function handleNewAlerts(alerts) {
    const knownKeys = new Set(alertHistory.value.map((item) => item.eventKey))
    const decorated = alerts
      .filter((item) => !knownKeys.has(item.eventKey))
      .map((item) => ({ ...item, issue: issueSummary(item) }))
    if (!decorated.length) return

    alertHistory.value = [...decorated, ...alertHistory.value].slice(0, 10)
    if (alertDialogVisible.value) pendingAlerts.value.push(...decorated)
    else showNextAlert(decorated)

    if (telegramEnabled.value) dispatchTelegramAlerts(decorated)

    if (selectedMode.value === 'DEMO' && dashboard.simulationRunning) {
      try {
        const status = await pauseDemo()
        dashboard.simulationRunning = false
        dashboard.simulationPaused = status?.runStatus === 'PAUSED'
      } catch (exception) {
        error.value = apiMessage(exception, '위험 알림 발생 후 시연을 일시정지하지 못했습니다.')
      }
    }
  }

  function showNextAlert(alerts = pendingAlerts.value.splice(0, 1)) {
    if (!alerts.length) return
    const [current, ...remaining] = alerts
    newAlerts.value = [current]
    if (remaining.length) pendingAlerts.value.unshift(...remaining)
    alertDialogVisible.value = true
  }

  function dispatchTelegramAlerts(alerts) {
    telegramError.value = false
    telegramMessage.value = `텔레그램 알림 ${alerts.length}건 발송 요청 중`
    sendTelegramAlerts(alerts.map((item) => ({
        eventKey: item.eventKey, equipmentId: item.equipmentId, locationId: item.locationId,
        equipmentType: item.equipmentType, collectedAt: item.collectedAt,
        riskScore: Number(item.riskScore || 0), alertLevel: String(item.riskLevel || 'WARNING'),
        alertLabel: item.alertLabel, issueSummary: item.issue,
      }))).then(() => {
        telegramMessage.value = `텔레그램 알림 ${alerts.length}건을 발송 대기열에 등록했습니다.`
      }).catch((exception) => {
        telegramError.value = true
        telegramMessage.value = apiMessage(exception, '텔레그램 알림 요청에 실패했습니다.')
      })
  }

  async function acknowledgeAlerts() {
    if (acknowledgingAlert.value) return
    if (pendingAlerts.value.length) {
      showNextAlert()
      return
    }

    acknowledgingAlert.value = true
    alertDialogVisible.value = false
    newAlerts.value = []
    try {
      if (selectedMode.value === 'DEMO' && dashboard.simulationPaused) {
        const status = await resumeDemo()
        dashboard.simulationRunning = status?.runStatus === 'RUNNING'
        dashboard.simulationPaused = false
        await load()
      }
    } catch (exception) {
      error.value = apiMessage(exception, '시연을 다시 시작하지 못했습니다.')
    } finally {
      acknowledgingAlert.value = false
    }
  }

  function schedulePoll() {
    window.clearTimeout(pollTimer)
    if (disposed || (selectedMode.value === 'DEMO' && dashboard.simulationPaused)) return
    const delay = selectedMode.value === 'DEMO' && dashboard.simulationRunning ? 700 : 60000
    pollTimer = window.setTimeout(() => load({ detectAlerts: true }), delay)
  }

  function setupViewerPresence() {
    if (!('BroadcastChannel' in window)) return
    viewerChannel = new BroadcastChannel('admin-predictive-maintenance-viewers')
    viewerChannel.addEventListener('message', ({ data }) => {
      if (!data?.viewerId || data.viewerId === viewerId) return
      if (data.type === 'leave') activeViewers.delete(data.viewerId)
      else if (data.type === 'hello' || data.type === 'active') {
        activeViewers.set(data.viewerId, Date.now())
        if (data.type === 'hello') broadcastPresence('active')
      }
    })
    broadcastPresence('hello')
    presenceTimer = window.setInterval(() => broadcastPresence('active'), 10000)
  }

  function broadcastPresence(type) {
    viewerChannel?.postMessage({ type, viewerId })
  }

  function hasOtherViewer() {
    const activeAfter = Date.now() - 180000
    return [...activeViewers.values()].some((lastSeenAt) => lastSeenAt >= activeAfter)
  }

  function cleanup(useKeepalive = false) {
    if (disposed) return
    disposed = true
    window.clearTimeout(pollTimer)
    window.clearInterval(presenceTimer)
    window.removeEventListener('pagehide', handlePageHide)
    const keepSimulationRunning = hasOtherViewer()
    broadcastPresence('leave')
    viewerChannel?.close()
    viewerChannel = null
    if (selectedMode.value === 'DEMO' && demoActive() && !keepSimulationRunning) {
      const stopRequest = useKeepalive ? stopDemoOnPageExit() : stopDemo()
      stopRequest.catch(() => {})
    }
  }

  function handlePageHide() {
    cleanup(true)
  }

  function demoActive() {
    return dashboard.simulationRunning || dashboard.simulationPaused
  }

  async function initialize() {
    selectedMode.value = String(route.query.mode || 'LIVE').toUpperCase() === 'DEMO' ? 'DEMO' : 'LIVE'
    await load()
    if (selectedMode.value === 'DEMO' && dashboard.simulationPaused) {
      const pausedAlerts = (dashboard.equipment || []).flatMap((item) => {
        const rank = alertRank(item)
        if (!rank) return []
        return [{
          ...item,
          eventKey: `${dashboard.demoRunId}-${item.equipmentId}-${item.collectedAt}-${rank}`,
          alertLabel: alertLabel(rank),
        }]
      })
      await handleNewAlerts(pausedAlerts)
    }
    const equipmentId = Array.isArray(route.query.equipmentId) ? route.query.equipmentId[0] : route.query.equipmentId
    if (!equipmentId) return
    const target = dashboard.equipment?.find((item) => String(item.equipmentId) === String(equipmentId))
    if (target) await openDetail(target)
    else if (!error.value) error.value = `${equipmentId} 장비를 현재 수집 시점에서 찾지 못했습니다.`
  }

  onMounted(() => {
    setupViewerPresence()
    window.addEventListener('pagehide', handlePageHide)
    initialize()
  })
  onBeforeUnmount(() => cleanup(false))

  return {
    dashboard, selectedMode, telegramEnabled, loading, error, controlBusy,
    selected, detailLoading, detailHistory, shadowPrediction, reversedHistory, detailMetrics,
    alertHistory, newAlerts, pendingAlertCount, alertDialogVisible, telegramMessage, telegramError, acknowledgingAlert,
    sortedEquipment, abnormalEquipment, healthStyle,
    load, changeMode, toggleDemo, openDetail, acknowledgeAlerts,
  }
}

function riskRank(item) { return item.riskLevel === 'CRITICAL' ? 3 : item.riskLevel === 'WARNING' ? 2 : 1 }
function alertRank(item) { const score = Number(item.riskScore || 0); return item.riskLevel === 'CRITICAL' || score >= 95 ? 3 : score >= 80 ? 2 : score >= 50 || item.riskLevel === 'WARNING' ? 1 : 0 }
function alertLabel(rank) { return rank >= 3 ? '긴급정비' : rank >= 2 ? '긴급점검' : '주의' }
function riskTone(level) { return level === 'CRITICAL' ? 'danger' : level === 'WARNING' ? 'warning' : 'normal' }
function number(value) { return value == null ? '—' : Number(value).toFixed(1) }
function metric(label, value, unit, percent, tone) { return { label, value, unit, percent: Math.max(0, Math.min(100, Number(percent || 0))), tone } }
function issueSummary(item) { if (item.successRate != null && item.successRate < 95) return '통신 성공률 저하'; if (item.responseTimeMs != null && item.responseTimeMs > 200) return '응답시간 증가'; if (item.packetLossRate != null && item.packetLossRate > 1) return '패킷 손실 증가'; if (item.signalStrengthDbm != null && item.signalStrengthDbm < -70) return '신호 세기 약함'; if (item.temperatureC != null && item.temperatureC > 45) return '장비 온도 상승'; return '위험도 상승' }
function apiMessage(error, fallback) { return error.response?.data?.message || fallback }

