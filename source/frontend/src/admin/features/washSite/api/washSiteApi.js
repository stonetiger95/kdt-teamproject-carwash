import axiosClient from '@/shared/api/axiosClient'

export function getOwners() {
  return axiosClient.get('/api/admin/owners')
}

export function getWashSites(ownerId) {
  return ownerId
    ? axiosClient.get(`/api/admin/owners/${ownerId}/wash-sites`)
    : axiosClient.get('/api/admin/wash-sites')
}

export function createWashSite(payload) {
  return axiosClient.post('/api/admin/wash-sites', payload)
}

export function updateWashSite(washSiteId, payload) {
  return axiosClient.patch(`/api/admin/wash-sites/${washSiteId}`, payload)
}

