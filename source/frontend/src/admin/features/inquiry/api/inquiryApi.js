import axiosClient from '@/shared/api/axiosClient'

export function getInquiryList() {
  return axiosClient.get('/api/inquiries')
}

export function getInquiry(inquiryId) {
  return axiosClient.get(`/api/inquiries/${inquiryId}`)
}

export function answerInquiry(inquiryId, answer) {
  return axiosClient.patch(`/api/inquiries/${inquiryId}/answer`, { answer })
}

export function refundInquiry(inquiryId, refundInfo) {
  return axiosClient.post(`/api/inquiries/${inquiryId}/refund`, refundInfo)
}

export function getRefundPolicy(paymentHistoryId) {
  return axiosClient.get(`/api/admin/benefits/payments/${paymentHistoryId}/refund-policy`)
}

