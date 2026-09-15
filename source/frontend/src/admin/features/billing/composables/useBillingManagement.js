import { ref } from 'vue'
import * as api from '@/admin/features/billing/api/billingApi'

function message(error, fallback) { return error?.response?.data?.message || error?.response?.data?.detail || fallback }

export function useBillingManagement() {
  const payments = ref([])
  const paymentDetail = ref(null)
  const dashboard = ref(null)
  const comparisons = ref([])
  const owners = ref([])
  const washSites = ref([])
  const loading = ref(false)
  const detailLoading = ref(false)
  const actionLoading = ref(false)
  const error = ref('')
  const successMessage = ref('')

  async function loadPayments(params = {}) {
    loading.value = true; error.value = ''
    try { const result = await api.getPayments(params); payments.value = Array.isArray(result) ? result : [] }
    catch (requestError) { payments.value = []; error.value = message(requestError, '결제 내역을 불러오지 못했습니다.') }
    finally { loading.value = false }
  }

  async function loadPaymentDetail(paymentId) {
    detailLoading.value = true; error.value = ''; paymentDetail.value = null
    try {
      const result = await api.getPayment(paymentId)
      paymentDetail.value = result || null
      return paymentDetail.value
    } catch (requestError) {
      error.value = message(requestError, '결제 상세 정보를 불러오지 못했습니다.')
      return null
    } finally { detailLoading.value = false }
  }

  async function loadFilterOptions(ownerId = '') {
    try {
      const [ownerResult, siteResult] = await Promise.all([owners.value.length ? Promise.resolve(owners.value) : api.getOwners(), api.getWashSites(ownerId)])
      owners.value = Array.isArray(ownerResult) ? ownerResult : []
      washSites.value = Array.isArray(siteResult) ? siteResult : []
    } catch (requestError) { error.value = message(requestError, '사업주·세차장 정보를 불러오지 못했습니다.') }
  }

  async function loadSettlement(params) {
    loading.value = true; error.value = ''; dashboard.value = null; comparisons.value = []
    try {
      const [summary, sites] = await Promise.all([api.getSettlementDashboard(params), api.getSiteComparisons(params)])
      dashboard.value = summary || null
      comparisons.value = Array.isArray(sites) ? sites : []
    } catch (requestError) { error.value = message(requestError, '정산 데이터를 불러오지 못했습니다.') }
    finally { loading.value = false }
  }

  async function refund(payment, amount, reason, searchParams) {
    const paymentId = payment.refundablePaymentId || payment.paymentId
    actionLoading.value = true; error.value = ''; successMessage.value = ''
    try {
      const result = await api.cancelPayment(paymentId, { refundAmount: amount, cancelReason: reason })
      if (result === 0) throw new Error('환불 불가')
      await loadPayments(searchParams)
      successMessage.value = '환불 처리가 완료되었습니다.'
      return true
    } catch (requestError) { error.value = message(requestError, '환불 처리에 실패했습니다.'); return false }
    finally { actionLoading.value = false }
  }

  async function refundPolicy(payment) {
    const paymentId = payment.refundablePaymentId || payment.paymentId
    try { return await api.getRefundPolicy(paymentId) }
    catch { return null }
  }

  async function reviewPayAppPayment(paymentId, payload, searchParams) {
    actionLoading.value = true; error.value = ''; successMessage.value = ''
    try {
      const result = await api.reviewPayAppPayment(paymentId, payload)
      paymentDetail.value = result || null
      await loadPayments(searchParams)
      successMessage.value = 'PayApp 수동 대사 결과가 반영되었습니다.'
      return true
    } catch (requestError) {
      error.value = message(requestError, 'PayApp 수동 대사 결과를 반영하지 못했습니다.')
      return false
    } finally { actionLoading.value = false }
  }

  return { payments, paymentDetail, dashboard, comparisons, owners, washSites, loading, detailLoading, actionLoading, error, successMessage, loadPayments, loadPaymentDetail, loadFilterOptions, loadSettlement, refund, refundPolicy, reviewPayAppPayment }
}

