import axiosClient from '@/shared/api/axiosClient'

export function getPayments(params) { return axiosClient.get('/api/payments', { params }) }
export function getPayment(paymentId) { return axiosClient.get(`/api/payments/${paymentId}`) }
export function reviewPayAppPayment(paymentId, payload) { return axiosClient.post(`/api/admin/payments/${paymentId}/reconciliation-review`, payload) }
export function cancelPayment(paymentId, payload) { return axiosClient.post(`/api/payments/${paymentId}/cancel`, payload) }
export function getRefundPolicy(paymentId) { return axiosClient.get(`/api/admin/benefits/payments/${paymentId}/refund-policy`) }
export function getSettlementDashboard(params) { return axiosClient.get('/api/admin/settlement/dashboard', { params }) }
export function getSiteComparisons(params) { return axiosClient.get('/api/admin/settlement/site-comparisons', { params }) }
export function getOwners() { return axiosClient.get('/api/admin/owners') }
export function getWashSites(ownerId) { return ownerId ? axiosClient.get(`/api/admin/owners/${ownerId}/wash-sites`) : axiosClient.get('/api/admin/wash-sites') }

