import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  getEquipmentDashboard,
  getInquiries,
  getPlateDetectionLogDetail,
  getPlateDetectionLogImage,
  getPlateDetectionLogs,
  getPlateReviewLogs,
  getWashSites,
  startEquipmentDemo,
  stopEquipmentDemo,
} from '@/admin/features/admin/api/dashboardApi'

const emptyEquipment = () => ({
  mode: 'LIVE',
  simulationRunning: false,
  telegramEnabled: false,
  collectedAt: null,
  totalEquipment: 0,
  normalCount: 0,
  warningCount: 0,
  criticalCount: 0,
  equipment: [],
})

export function useAdminDashboard() {
  const equipment = reactive(emptyEquipment())
  const inquiries = ref([])
  const washSites = ref([])
  const plateLogs = ref([])
  const reviewPlateLogs = ref([])
  const plateReviewTotal = ref(0)
  const latestPlate = ref(null)
  const latestPlateImageUrl = ref('')
  const loading = reactive({ equipment: true, inquiries: true, washSites: true, plates: true })
  const loaded = reactive({ equipment: false, inquiries: false, washSites: false, plates: false })
  const errors = reactive({ equipment: '', inquiries: '', washSites: '', plates: '' })
  const selectedMode = ref('LIVE')
  const lastUpdatedAt = ref(null)
  const refreshing = ref(false)
  const demoBusy = ref(false)
  let plateTimer = null
  let equipmentTimer = null

  const problemEquipment = computed(() =>
    [...(equipment.equipment || [])]
      .filter((item) => item.riskLevel === 'CRITICAL' || item.riskLevel === 'WARNING' || Number(item.warningEventCount || 0) > 0)
      .sort((a, b) => riskRank(b) - riskRank(a) || Number(b.warningEventCount || 0) - Number(a.warningEventCount || 0)),
  )
  const criticalEquipment = computed(() => Number(equipment.criticalCount || 0))
  const unansweredInquiries = computed(() =>
    inquiries.value.filter((item) => {
      const status = String(item.status || '').toUpperCase()
      return status ? status === 'WAITING' : Number(item.answerCount || 0) === 0
    }).sort((a, b) =>
      new Date(a.createdAt || 0) - new Date(b.createdAt || 0),
    ),
  )
  const oldInquiries = computed(() => unansweredInquiries.value.filter((item) => waitingHours(item.createdAt) >= 24))
  const activeSites = computed(() => washSites.value.filter((item) => item.status === 'ACTIVE'))
  const inactiveSites = computed(() => washSites.value.filter((item) => item.status !== 'ACTIVE'))

  const equipmentActions = computed(() => problemEquipment.value.map((item) => ({
      key: `equipment-${item.equipmentId}`,
      type: 'equipment',
      tone: item.riskLevel === 'CRITICAL' ? 'danger' : 'warning',
      priorityLabel: item.riskLevel === 'CRITICAL' ? '긴급' : '높음',
      icon: 'pi pi-exclamation-triangle',
      owner: '장비 관제',
      title: item.riskLevel === 'CRITICAL' ? '위험 장비' : '주의 장비',
      detail: `${item.equipmentId}${item.warningEventCount ? ` · 경고 ${item.warningEventCount}건` : ''}`,
      wait: item.collectedAt || equipment.collectedAt,
      item,
    })))
  const inquiryActions = computed(() => oldInquiries.value.map((item) => ({
      key: `inquiry-${item.inquiryId}`,
      type: 'inquiry',
      tone: 'warning',
      priorityLabel: waitingHours(item.createdAt) >= 48 ? '긴급' : '높음',
      icon: 'pi pi-comment',
      owner: '고객 지원',
      title: '장기 미답변 문의',
      detail: item.title || '제목 없음',
      wait: item.createdAt,
      item,
    })))
  const plateActions = computed(() => reviewPlateLogs.value.map((item) => ({
      key: `plate-${item.plateDetectionLogId}`,
      type: 'plate',
      tone: item.success === false ? 'danger' : 'warning',
      priorityLabel: item.success === false ? '높음' : '검토',
      icon: 'pi pi-camera',
      owner: '번호판 검수',
      title: item.success === false ? '번호판 인식 실패' : '번호판 검토 필요',
      detail: item.detectedPlateNumber || '번호판 미인식',
      wait: item.detectedTime,
      item,
    })))
  const siteActions = computed(() => inactiveSites.value.map((item) => ({
      key: `site-${item.washSiteId}`,
      type: 'site',
      tone: 'info',
      priorityLabel: '확인',
      icon: 'pi pi-building',
      owner: '사업장 운영',
      title: '비활성 세차장',
      detail: `${item.siteName || `세차장 #${item.washSiteId}`} · ${item.ownerName || '사업주 미확인'}`,
      wait: null,
      statusText: '운영 상태 확인',
      item,
    })))
  const actionItemCount = computed(() => equipmentActions.value.length + inquiryActions.value.length + Number(plateReviewTotal.value || reviewPlateLogs.value.length) + siteActions.value.length)
  const actionSummary = computed(() => ({
    equipment: equipmentActions.value.length,
    inquiries: inquiryActions.value.length,
    plates: Number(plateReviewTotal.value || reviewPlateLogs.value.length),
    sites: siteActions.value.length,
  }))
  const actionGroups = computed(() => ({
    equipment: equipmentActions.value,
    inquiry: inquiryActions.value,
    plate: plateActions.value,
    site: siteActions.value,
  }))
  const actionItems = computed(() => [
    ...equipmentActions.value.slice(0, 3),
    ...inquiryActions.value.slice(0, 3),
    ...plateActions.value.slice(0, 3),
    ...siteActions.value.slice(0, 2),
  ])

  async function loadEquipment({ showLoading = true } = {}) {
    if (showLoading) loading.equipment = true
    errors.equipment = ''
    try {
      Object.assign(equipment, emptyEquipment(), await getEquipmentDashboard(selectedMode.value))
      loaded.equipment = true
    } catch (error) {
      errors.equipment = apiMessage(error, '장비 상태를 불러오지 못했습니다.')
    } finally {
      loading.equipment = false
      lastUpdatedAt.value = new Date()
    }
  }

  async function loadInquiries({ showLoading = true } = {}) {
    if (showLoading) loading.inquiries = true
    errors.inquiries = ''
    try {
      inquiries.value = await getInquiries() || []
      loaded.inquiries = true
    }
    catch (error) { errors.inquiries = apiMessage(error, '문의 목록을 불러오지 못했습니다.') }
    finally { loading.inquiries = false }
  }

  async function loadWashSites({ showLoading = true } = {}) {
    if (showLoading) loading.washSites = true
    errors.washSites = ''
    try {
      washSites.value = await getWashSites() || []
      loaded.washSites = true
    }
    catch (error) { errors.washSites = apiMessage(error, '세차장 현황을 불러오지 못했습니다.') }
    finally { loading.washSites = false }
  }

  async function loadPlateLogs({ showLoading = true } = {}) {
    if (showLoading) loading.plates = true
    errors.plates = ''
    try {
      const [response, reviewResponse] = await Promise.all([getPlateDetectionLogs(8), getPlateReviewLogs(8)])
      plateLogs.value = response.content || []
      reviewPlateLogs.value = reviewResponse.content || []
      plateReviewTotal.value = Number(reviewResponse.totalElements || reviewPlateLogs.value.length)
      loaded.plates = true
      const newest = plateLogs.value[0]
      if (newest && newest.plateDetectionLogId !== latestPlate.value?.plateDetectionLogId) {
        latestPlate.value = await getPlateDetectionLogDetail(newest.plateDetectionLogId)
        revokePlateImage()
        try {
          const blob = await getPlateDetectionLogImage(newest.plateDetectionLogId)
          latestPlateImageUrl.value = URL.createObjectURL(blob)
        } catch (error) {
          latestPlateImageUrl.value = ''
        }
      }
    } catch (error) {
      errors.plates = apiMessage(error, '번호판 인식 기록을 불러오지 못했습니다.')
    } finally {
      loading.plates = false
    }
  }

  async function refreshAll() {
    if (refreshing.value) return
    refreshing.value = true
    try {
      await Promise.allSettled([
        loadEquipment({ showLoading: false }),
        loadInquiries({ showLoading: false }),
        loadWashSites({ showLoading: false }),
        loadPlateLogs({ showLoading: false }),
      ])
      lastUpdatedAt.value = new Date()
    } finally {
      refreshing.value = false
    }
  }

  async function loadInitialDashboard() {
    await Promise.allSettled([loadEquipment(), loadInquiries(), loadWashSites(), loadPlateLogs()])
    lastUpdatedAt.value = new Date()
  }

  async function changeMode(mode) {
    selectedMode.value = mode
    await loadEquipment({ showLoading: false })
    scheduleEquipmentRefresh()
  }

  async function toggleDemo() {
    if (demoBusy.value) return
    demoBusy.value = true
    try {
      if (equipment.simulationRunning) await stopEquipmentDemo()
      else await startEquipmentDemo(Boolean(equipment.telegramEnabled))
      await loadEquipment({ showLoading: false })
    } catch (error) {
      errors.equipment = apiMessage(error, '시연 상태를 변경하지 못했습니다.')
    } finally {
      demoBusy.value = false
      scheduleEquipmentRefresh()
    }
  }

  function scheduleEquipmentRefresh() {
    window.clearTimeout(equipmentTimer)
    const delay = selectedMode.value === 'DEMO' && equipment.simulationRunning ? 1000 : 60000
    equipmentTimer = window.setTimeout(async () => {
      await loadEquipment({ showLoading: false })
      scheduleEquipmentRefresh()
    }, delay)
  }

  function revokePlateImage() {
    if (latestPlateImageUrl.value) URL.revokeObjectURL(latestPlateImageUrl.value)
    latestPlateImageUrl.value = ''
  }

  onMounted(async () => {
    await loadInitialDashboard()
    plateTimer = window.setInterval(() => loadPlateLogs({ showLoading: false }), 10000)
    scheduleEquipmentRefresh()
  })

  onBeforeUnmount(() => {
    window.clearInterval(plateTimer)
    window.clearTimeout(equipmentTimer)
    revokePlateImage()
  })

  return {
    equipment, inquiries, washSites, plateLogs, latestPlate, latestPlateImageUrl,
    loading, loaded, errors, selectedMode, lastUpdatedAt, refreshing, demoBusy,
    problemEquipment, criticalEquipment, unansweredInquiries, oldInquiries,
    reviewPlateLogs, plateReviewTotal, activeSites, inactiveSites,
    actionGroups, actionItems, actionItemCount, actionSummary,
    refreshAll, changeMode, toggleDemo,
  }
}

function riskRank(item) {
  return item.riskLevel === 'CRITICAL' ? 3 : item.riskLevel === 'WARNING' ? 2 : 1
}

function waitingHours(value) {
  if (!value) return 0
  return Math.max(0, (Date.now() - new Date(value).getTime()) / 3600000)
}

function apiMessage(error, fallback) {
  return error.response?.data?.message || fallback
}

