import { computed, ref } from 'vue'
import * as washSiteApi from '@/admin/features/washSite/api/washSiteApi'

function message(error, fallback) {
  return error?.response?.data?.message || error?.response?.data?.detail || fallback
}

export function useWashSiteManagement() {
  const owners = ref([])
  const washSites = ref([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const saveError = ref('')
  const successMessage = ref('')

  const counts = computed(() => ({
    total: washSites.value.length,
    active: washSites.value.filter((site) => site.status === 'ACTIVE').length,
    inactive: washSites.value.filter((site) => site.status === 'INACTIVE').length,
    owners: new Set(washSites.value.map((site) => site.ownerId).filter(Boolean)).size,
  }))

  async function load(ownerId = '') {
    loading.value = true
    error.value = ''
    try {
      const [ownerResult, siteResult] = await Promise.all([
        owners.value.length ? Promise.resolve(owners.value) : washSiteApi.getOwners(),
        washSiteApi.getWashSites(ownerId),
      ])
      owners.value = Array.isArray(ownerResult) ? ownerResult : []
      washSites.value = Array.isArray(siteResult) ? siteResult : []
    } catch (requestError) {
      washSites.value = []
      error.value = message(requestError, '세차장 정보를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  async function save(payload, washSiteId = null, ownerFilter = '') {
    saving.value = true
    error.value = ''
    saveError.value = ''
    successMessage.value = ''
    try {
      if (washSiteId) await washSiteApi.updateWashSite(washSiteId, payload)
      else await washSiteApi.createWashSite(payload)
      await load(ownerFilter)
      successMessage.value = washSiteId ? '세차장 정보가 수정되었습니다.' : '새 세차장이 등록되었습니다.'
      return true
    } catch (requestError) {
      saveError.value = message(requestError, '세차장 정보를 저장하지 못했습니다.')
      return false
    } finally {
      saving.value = false
    }
  }

  return { owners, washSites, loading, saving, error, saveError, successMessage, counts, load, save }
}

