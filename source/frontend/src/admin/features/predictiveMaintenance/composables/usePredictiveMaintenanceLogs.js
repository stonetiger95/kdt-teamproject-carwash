import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPredictiveMaintenanceLogs, getStoredShadowPrediction } from '@/admin/features/predictiveMaintenance/api/predictiveMaintenanceLogApi'

const emptyPage = () => ({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })

export function usePredictiveMaintenanceLogs() {
  const route = useRoute()
  const filters = reactive({ mode: String(route.query.mode || 'LIVE').toUpperCase() === 'DEMO' ? 'DEMO' : 'LIVE', equipmentId: String(route.query.equipmentId || ''), riskLevel: 'ALL', fromAt: '', toAt: '' })
  const page = reactive(emptyPage())
  const loading = ref(false)
  const error = ref('')
  const selected = ref(null)
  const selectedShadow = ref(null)
  const shadowLoading = ref(false)
  async function load(targetPage = 0, targetSize = page.size) {
    loading.value = true; error.value = ''
    try {
      const response = await getPredictiveMaintenanceLogs({ mode: filters.mode, equipmentId: filters.equipmentId.trim() || undefined, riskLevel: filters.riskLevel === 'ALL' ? undefined : filters.riskLevel, fromAt: filters.fromAt || undefined, toAt: filters.toAt || undefined, page: targetPage, size: targetSize })
      Object.assign(page, emptyPage(), response || {})
    } catch (exception) { error.value = exception.response?.data?.message || exception.response?.data?.detail || '예지보전 데이터 로그를 불러오지 못했습니다.' } finally { loading.value = false }
  }
  function search() { return load(0, page.size) }
  function reset() { Object.assign(filters, { mode: filters.mode, equipmentId: '', riskLevel: 'ALL', fromAt: '', toAt: '' }); return load(0, page.size) }
  function changePage(event) { return load(event.page, event.rows) }
  async function openDetail(row) {
    selected.value = row
    selectedShadow.value = null
    shadowLoading.value = true
    try {
      selectedShadow.value = await getStoredShadowPrediction({
        mode: row.sourceMode,
        equipmentId: row.equipmentId,
        collectedAt: row.collectedAt,
      })
    } catch {
      // Migration 64 미적용 또는 미채점 행은 운영 로그 표시를 막지 않는다.
      selectedShadow.value = null
    } finally {
      shadowLoading.value = false
    }
  }
  onMounted(() => load())
  return { filters, page, loading, error, selected, selectedShadow, shadowLoading, load, search, reset, changePage, openDetail }
}
