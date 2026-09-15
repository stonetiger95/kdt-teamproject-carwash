import axiosClient from '@/shared/api/axiosClient'

export function getMembers(params) {
  return axiosClient.get('/api/admin/member-manage', { params })
}

export function getMember(customerId) {
  return axiosClient.get(`/api/admin/member-manage/${customerId}`)
}

export function updateMember(customerId, payload) {
  return axiosClient.patch(`/api/admin/member-manage/${customerId}`, payload)
}

export function getVehicles(params) {
  return axiosClient.get('/api/admin/vehicle', { params })
}

export function getVehicle(plateNumber) {
  return axiosClient.get(`/api/admin/vehicle/${encodeURIComponent(plateNumber)}`)
}

export function addVehicleBlacklist(plateNumber, reason) {
  return axiosClient.post(`/api/admin/vehicle/${encodeURIComponent(plateNumber)}/blacklist`, { reason })
}

export function releaseVehicleBlacklist(plateNumber) {
  return axiosClient.patch(`/api/admin/vehicle/${encodeURIComponent(plateNumber)}/blacklist/release`)
}

