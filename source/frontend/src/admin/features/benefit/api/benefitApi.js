import axiosClient from '@/shared/api/axiosClient'

const BASE_URL = '/api/admin/benefits'

export function getOwners() { return axiosClient.get('/api/admin/owners') }
export function getWashSites(ownerId) { return ownerId ? axiosClient.get(`/api/admin/owners/${ownerId}/wash-sites`) : axiosClient.get('/api/admin/wash-sites') }
export function getPolicy(washSiteId) { return axiosClient.get(`${BASE_URL}/sites/${washSiteId}/policy`) }
export function savePolicy(washSiteId, policy) { return axiosClient.put(`${BASE_URL}/sites/${washSiteId}/policy`, policy) }
export function getCampaigns(washSiteId) { return axiosClient.get(`${BASE_URL}/sites/${washSiteId}/campaigns`) }
export function getCampaignStatuses(washSiteId) { return axiosClient.get(`${BASE_URL}/sites/${washSiteId}/campaign-status`) }
export function createCampaign(campaign) { return axiosClient.post(`${BASE_URL}/campaigns`, campaign) }
export function updateCampaign(campaignId, campaign) { return axiosClient.put(`${BASE_URL}/campaigns/${campaignId}`, campaign) }
export function issueCoupons(campaignId, customerIds) { return axiosClient.post(`${BASE_URL}/campaigns/${campaignId}/coupons/bulk`, { customerIds }) }
export function getCouponAudience(campaignId, params) { return axiosClient.get(`${BASE_URL}/campaigns/${campaignId}/audience`, { params }) }
export function expireCoupons(washSiteId) { return axiosClient.post(`${BASE_URL}/coupons/expire`, null, { params: { washSiteId } }) }

