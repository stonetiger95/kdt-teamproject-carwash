import { onBeforeUnmount, reactive, ref } from 'vue'
import * as api from '@/admin/features/operations/api/operationsApi'

function page(result, size = 10) { return { page: Number(result?.page || 1), size: Number(result?.size || size), totalElements: Number(result?.totalElements || 0), totalPages: Number(result?.totalPages || 0), hasNext: Boolean(result?.hasNext), hasPrevious: Boolean(result?.hasPrevious) } }
function message(error, fallback) { return error?.response?.data?.message || error?.response?.data?.detail || fallback }

export function useOperationsManagement() {
  const reservations = ref([]), histories = ref([]), reservationDetail = ref(null), historyDetail = ref(null)
  const owners = ref([]), washSites = ref([]), entryImageUrl = ref(''), exitImageUrl = ref('')
  const reservationPage = reactive(page(null)), historyPage = reactive(page(null))
  const loading = ref(false), detailLoading = ref(false), imageLoading = ref(false), error = ref(''), imageError = ref('')

  async function loadFilters(ownerId = '') {
    try { const [ownerResult, siteResult] = await Promise.all([owners.value.length ? Promise.resolve(owners.value) : api.getOwners(), api.getWashSites(ownerId)]); owners.value = Array.isArray(ownerResult) ? ownerResult : []; washSites.value = Array.isArray(siteResult) ? siteResult : [] }
    catch (requestError) { error.value = message(requestError, '사업주·세차장 정보를 불러오지 못했습니다.') }
  }
  async function loadReservations(params = {}) {
    loading.value = true; error.value = ''
    try { const result = await api.getReservations({ page: reservationPage.page, size: reservationPage.size, ...params }); reservations.value = Array.isArray(result?.content) ? result.content : []; Object.assign(reservationPage, page(result, reservationPage.size)) }
    catch (requestError) { reservations.value = []; error.value = message(requestError, '예약 내역을 불러오지 못했습니다.') }
    finally { loading.value = false }
  }
  async function loadHistories(params = {}) {
    loading.value = true; error.value = ''
    try { const result = await api.getWashHistories({ page: historyPage.page, size: historyPage.size, ...params }); histories.value = Array.isArray(result?.content) ? result.content : []; Object.assign(historyPage, page(result, historyPage.size)) }
    catch (requestError) { histories.value = []; error.value = message(requestError, '세차 내역을 불러오지 못했습니다.') }
    finally { loading.value = false }
  }
  async function loadReservationDetail(id) {
    detailLoading.value = true; error.value = ''
    try { reservationDetail.value = await api.getReservation(id); return true }
    catch (requestError) { reservationDetail.value = null; error.value = message(requestError, '예약 상세 정보를 불러오지 못했습니다.'); return false }
    finally { detailLoading.value = false }
  }
  function releaseImages() { if (entryImageUrl.value) URL.revokeObjectURL(entryImageUrl.value); if (exitImageUrl.value) URL.revokeObjectURL(exitImageUrl.value); entryImageUrl.value = ''; exitImageUrl.value = '' }
  async function loadHistoryDetail(id) {
    detailLoading.value = true; error.value = ''; imageError.value = ''; releaseImages()
    try {
      historyDetail.value = await api.getWashHistory(id)
      imageLoading.value = true
      const images = await Promise.allSettled([api.getWashHistoryImage(id, 'entry'), api.getWashHistoryImage(id, 'exit')])
      if (images[0].status === 'fulfilled') entryImageUrl.value = URL.createObjectURL(images[0].value)
      if (images[1].status === 'fulfilled') exitImageUrl.value = URL.createObjectURL(images[1].value)
      if (images.some((item) => item.status === 'rejected' && item.reason?.response?.status !== 404)) imageError.value = '일부 입출차 사진을 불러오지 못했습니다.'
      return true
    } catch (requestError) { historyDetail.value = null; error.value = message(requestError, '세차 상세 정보를 불러오지 못했습니다.'); return false }
    finally { detailLoading.value = false; imageLoading.value = false }
  }
  function clearDetails() { reservationDetail.value = null; historyDetail.value = null; imageError.value = ''; releaseImages() }
  onBeforeUnmount(releaseImages)
  return { reservations, histories, reservationDetail, historyDetail, owners, washSites, entryImageUrl, exitImageUrl, reservationPage, historyPage, loading, detailLoading, imageLoading, error, imageError, loadFilters, loadReservations, loadHistories, loadReservationDetail, loadHistoryDetail, clearDetails }
}

