import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import * as plateApi from '@/admin/features/plateDetectionLog/api/plateDetectionLogApi'

function message(error, fallback) {
  return error?.response?.data?.message || error?.response?.data?.detail || fallback
}

export function usePlateDetectionLogs() {
  const logs = ref([])
  const detail = ref(null)
  const imageUrl = ref('')
  const loading = ref(false)
  const detailLoading = ref(false)
  const reviewLoading = ref(false)
  const downloading = ref(false)
  const error = ref('')
  const successMessage = ref('')
  const pageInfo = reactive({ page: 1, size: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false })

  const pageCounts = computed(() => ({
    failed: logs.value.filter((item) => item.success === false).length,
    needReview: logs.value.filter((item) => item.needReview).length,
    success: logs.value.filter((item) => item.success === true).length,
  }))

  function releaseImage() {
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }

  async function loadLogs(params = {}) {
    loading.value = true
    error.value = ''
    try {
      const result = await plateApi.getPlateDetectionLogs({ page: pageInfo.page, size: pageInfo.size, ...params })
      logs.value = Array.isArray(result?.content) ? result.content : []
      Object.assign(pageInfo, {
        page: Number(result?.page || 1), size: Number(result?.size || pageInfo.size),
        totalElements: Number(result?.totalElements || 0), totalPages: Number(result?.totalPages || 0),
        hasNext: Boolean(result?.hasNext), hasPrevious: Boolean(result?.hasPrevious),
      })
      return true
    } catch (requestError) {
      logs.value = []
      error.value = message(requestError, '번호판 인식 로그를 불러오지 못했습니다.')
      return false
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(logId) {
    detailLoading.value = true
    error.value = ''
    successMessage.value = ''
    releaseImage()
    try {
      detail.value = await plateApi.getPlateDetectionLog(logId)
      try {
        const blob = await plateApi.getPlateDetectionLogImage(logId)
        imageUrl.value = URL.createObjectURL(blob)
      } catch {
        imageUrl.value = ''
      }
      return true
    } catch (requestError) {
      detail.value = null
      error.value = message(requestError, '번호판 인식 상세 정보를 불러오지 못했습니다.')
      return false
    } finally {
      detailLoading.value = false
    }
  }

  async function saveReview(reviewInfo, currentSearch) {
    if (!detail.value?.plateDetectionLogId) return false
    reviewLoading.value = true
    error.value = ''
    try {
      detail.value = await plateApi.reviewPlateDetectionLog(detail.value.plateDetectionLogId, reviewInfo)
      await loadLogs(currentSearch)
      successMessage.value = '번호판 검토 결과가 저장되었습니다.'
      return true
    } catch (requestError) {
      error.value = message(requestError, '번호판 검토 결과를 저장하지 못했습니다.')
      return false
    } finally {
      reviewLoading.value = false
    }
  }

  async function exportTrainingData() {
    if (downloading.value) return
    downloading.value = true
    error.value = ''
    try {
      const blob = await plateApi.downloadTrainingData()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `plate-training-data-${new Date().toISOString().replace(/[-:]/g, '').slice(0, 15)}.zip`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      successMessage.value = '학습 데이터 다운로드를 시작했습니다.'
    } catch (requestError) {
      error.value = message(requestError, '학습 데이터 다운로드에 실패했습니다.')
    } finally {
      downloading.value = false
    }
  }

  function clearDetail() {
    detail.value = null
    releaseImage()
  }

  onBeforeUnmount(releaseImage)
  return { logs, detail, imageUrl, loading, detailLoading, reviewLoading, downloading, error, successMessage, pageInfo, pageCounts, loadLogs, loadDetail, saveReview, exportTrainingData, clearDetail }
}

