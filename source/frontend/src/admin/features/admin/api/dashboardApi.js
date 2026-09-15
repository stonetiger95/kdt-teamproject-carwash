import axiosClient from '@/shared/api/axiosClient'

export function getEquipmentDashboard(mode = 'LIVE') {
  return axiosClient.get('/api/admin/predictive-maintenance/dashboard/mode', {
    params: { mode },
  })
}

export function getPlateDetectionLogs(size = 8) {
  return axiosClient.get('/api/admin/plate-detection-logs', {
    params: { page: 1, size },
  })
}

export function getPlateReviewLogs(size = 8) {
  return axiosClient.get('/api/admin/plate-detection-logs', {
    params: { page: 1, size, needReviewOnly: true },
  })
}

export function getPlateDetectionLogDetail(logId) {
  return axiosClient.get(`/api/admin/plate-detection-logs/${logId}`)
}

export function getPlateDetectionLogImage(logId) {
  return axiosClient.get(`/api/admin/plate-detection-logs/${logId}/image`, {
    responseType: 'blob',
  })
}

export function getInquiries() {
  return axiosClient.get('/api/inquiries')
}

export function getWashSites() {
  return axiosClient.get('/api/admin/wash-sites')
}

export function startEquipmentDemo(telegramEnabled = false) {
  return axiosClient.post('/api/admin/predictive-maintenance/demo/start', { telegramEnabled })
}

export function stopEquipmentDemo() {
  return axiosClient.post('/api/admin/predictive-maintenance/demo/stop')
}

