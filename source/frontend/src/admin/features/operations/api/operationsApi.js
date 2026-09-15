import axiosClient from '@/shared/api/axiosClient'

export function getReservations(params) { return axiosClient.get('/api/admin/reservations', { params }) }
export function getReservation(id) { return axiosClient.get(`/api/admin/reservations/${id}`) }
export function getWashHistories(params) { return axiosClient.get('/api/admin/wash-history/page', { params }) }
export function getWashHistory(id) { return axiosClient.get(`/api/admin/wash-history/${id}`) }
export function getWashHistoryImage(id, type) { return axiosClient.get(`/api/admin/wash-history/${id}/images/${type}`, { responseType: 'blob' }) }
export function getOwners() { return axiosClient.get('/api/admin/owners') }
export function getWashSites(ownerId) { return ownerId ? axiosClient.get(`/api/admin/owners/${ownerId}/wash-sites`) : axiosClient.get('/api/admin/wash-sites') }

