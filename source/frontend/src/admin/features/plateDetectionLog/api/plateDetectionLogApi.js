import axiosClient from '@/shared/api/axiosClient'

const BASE_URL = '/api/admin/plate-detection-logs'

export function getPlateDetectionLogs(params) {
  return axiosClient.get(BASE_URL, { params })
}

export function getPlateDetectionLog(logId) {
  return axiosClient.get(`${BASE_URL}/${logId}`)
}

export function getPlateDetectionLogImage(logId) {
  return axiosClient.get(`${BASE_URL}/${logId}/image`, { responseType: 'blob' })
}

export function reviewPlateDetectionLog(logId, reviewInfo) {
  return axiosClient.patch(`${BASE_URL}/${logId}/review`, reviewInfo)
}

export function downloadTrainingData() {
  return axiosClient.get(`${BASE_URL}/training-data/export`, { responseType: 'blob', timeout: 0 })
}

