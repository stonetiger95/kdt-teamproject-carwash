import axiosClient from "@/shared/api/axiosClient"

function managerBaseUrl(viewerRole) {
    if (viewerRole === "OWNER") return "/api/owner/benefits"
    if (viewerRole === "ADMIN") return "/api/admin/benefits"
    throw new Error("혜택 관리 권한이 없습니다.")
}

export function getBenefitPolicy(viewerRole, washSiteId) {
    return axiosClient.get(`${managerBaseUrl(viewerRole)}/sites/${washSiteId}/policy`)
}

export function saveBenefitPolicy(viewerRole, washSiteId, policy) {
    return axiosClient.put(`${managerBaseUrl(viewerRole)}/sites/${washSiteId}/policy`, policy)
}

export function getBenefitCampaigns(viewerRole, washSiteId) {
    return axiosClient.get(`${managerBaseUrl(viewerRole)}/sites/${washSiteId}/campaigns`)
}

export function createBenefitCampaign(viewerRole, campaign) {
    return axiosClient.post(`${managerBaseUrl(viewerRole)}/campaigns`, campaign)
}

export function updateBenefitCampaign(viewerRole, campaignId, campaign) {
    return axiosClient.put(`${managerBaseUrl(viewerRole)}/campaigns/${campaignId}`, campaign)
}

export function issueBenefitCoupon(viewerRole, campaignId, customerId) {
    return axiosClient.post(`${managerBaseUrl(viewerRole)}/campaigns/${campaignId}/coupons`, { customerId })
}

export function issueBenefitCoupons(viewerRole, campaignId, customerIds) {
    return axiosClient.post(`${managerBaseUrl(viewerRole)}/campaigns/${campaignId}/coupons/bulk`, { customerIds })
}

export function getBenefitCouponAudience(viewerRole, campaignId, params) {
    return axiosClient.get(`${managerBaseUrl(viewerRole)}/campaigns/${campaignId}/audience`, { params })
}

export function getMyBenefits() {
    return axiosClient.get("/api/customer/benefits")
}

export function getMyBenefitSummary() {
    return axiosClient.get("/api/customer/benefits/summary")
}

export function getMyVisitRewardProgress() {
    return axiosClient.get("/api/customer/benefits/visit-progress")
}

export function saveMyBenefitPreference(washSiteId, preference) {
    return axiosClient.patch(`/api/customer/benefits/sites/${washSiteId}/preference`, preference)
}

export function getMyCoupons(washSiteId, status = "AVAILABLE") {
    return axiosClient.get(`/api/customer/benefits/sites/${washSiteId}/coupons`, { params: { status } })
}

export const getMyAvailableCoupons = washSiteId => getMyCoupons(washSiteId, "AVAILABLE")

export function selectWashBenefit(washHistoryId, selection) {
    return axiosClient.put(`/api/customer/benefits/wash-history/${washHistoryId}/selection`, selection)
}

export function getPaymentBenefitRefundPolicy(viewerRole, paymentHistoryId) {
    const role = String(viewerRole || "CUSTOMER").toLowerCase()
    return axiosClient.get(`/api/${role}/benefits/payments/${paymentHistoryId}/refund-policy`)
}
