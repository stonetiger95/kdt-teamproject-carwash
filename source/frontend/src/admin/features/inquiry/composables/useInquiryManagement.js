import { computed, ref } from 'vue'
import * as inquiryApi from '@/admin/features/inquiry/api/inquiryApi'

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.response?.data?.detail || fallback
}

export function useInquiryManagement() {
  const inquiries = ref([])
  const selectedInquiry = ref(null)
  const refundPolicy = ref(null)
  const loading = ref(false)
  const detailLoading = ref(false)
  const actionLoading = ref(false)
  const error = ref('')
  const actionMessage = ref('')

  const counts = computed(() => ({
    total: inquiries.value.length,
    waiting: inquiries.value.filter((item) => item.status === 'WAITING').length,
    answered: inquiries.value.filter((item) => item.status === 'ANSWERED').length,
    refund: inquiries.value.filter((item) => item.inquiryType === 'REFUND').length,
  }))

  async function loadList() {
    loading.value = true
    error.value = ''
    try {
      const result = await inquiryApi.getInquiryList()
      inquiries.value = Array.isArray(result) ? result : []
    } catch (requestError) {
      inquiries.value = []
      error.value = errorMessage(requestError, '문의 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(inquiryId) {
    if (!inquiryId) return
    detailLoading.value = true
    error.value = ''
    actionMessage.value = ''
    refundPolicy.value = null
    try {
      selectedInquiry.value = await inquiryApi.getInquiry(inquiryId)
      if (selectedInquiry.value?.paymentHistoryId) {
        try {
          refundPolicy.value = await inquiryApi.getRefundPolicy(selectedInquiry.value.paymentHistoryId)
        } catch {
          refundPolicy.value = null
        }
      }
    } catch (requestError) {
      selectedInquiry.value = null
      error.value = errorMessage(requestError, '문의 상세 내용을 불러오지 못했습니다.')
    } finally {
      detailLoading.value = false
    }
  }

  async function submitAnswer(answer) {
    if (!selectedInquiry.value?.inquiryId || !String(answer || '').trim()) return false
    actionLoading.value = true
    actionMessage.value = ''
    try {
      const result = await inquiryApi.answerInquiry(selectedInquiry.value.inquiryId, String(answer).trim())
      if (result === 0) throw new Error('권한 없음')
      await Promise.all([loadDetail(selectedInquiry.value.inquiryId), loadList()])
      actionMessage.value = '답변이 등록되었습니다.'
      return true
    } catch (requestError) {
      error.value = errorMessage(requestError, '답변을 등록하지 못했습니다.')
      return false
    } finally {
      actionLoading.value = false
    }
  }

  async function submitRefund(refundInfo) {
    if (!selectedInquiry.value?.inquiryId) return false
    actionLoading.value = true
    actionMessage.value = ''
    try {
      const result = await inquiryApi.refundInquiry(selectedInquiry.value.inquiryId, refundInfo)
      if (result === 0) throw new Error('결제 내역 없음')
      await Promise.all([loadDetail(selectedInquiry.value.inquiryId), loadList()])
      actionMessage.value = '환불 처리가 완료되었습니다.'
      return true
    } catch (requestError) {
      error.value = errorMessage(requestError, '환불 처리에 실패했습니다.')
      return false
    } finally {
      actionLoading.value = false
    }
  }

  function clearSelection() {
    selectedInquiry.value = null
    refundPolicy.value = null
    actionMessage.value = ''
  }

  return {
    inquiries, selectedInquiry, refundPolicy, loading, detailLoading, actionLoading,
    error, actionMessage, counts, loadList, loadDetail, submitAnswer, submitRefund, clearSelection,
  }
}

