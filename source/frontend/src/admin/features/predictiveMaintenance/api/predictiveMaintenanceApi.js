import axiosClient from '@/shared/api/axiosClient'

const BASE_URL = '/api/admin/predictive-maintenance'

export function getDashboardByMode(mode = 'LIVE') {
  return axiosClient.get(`${BASE_URL}/dashboard/mode`, { params: { mode } })
}

export function startDemo(telegramEnabled = false) {
  return axiosClient.post(`${BASE_URL}/demo/start`, { telegramEnabled })
}

export function stopDemo() {
  return axiosClient.post(`${BASE_URL}/demo/stop`)
}

export function pauseDemo() {
  return axiosClient.post(`${BASE_URL}/demo/pause`)
}

export function resumeDemo() {
  return axiosClient.post(`${BASE_URL}/demo/resume`)
}

export function stopDemoOnPageExit() {
  const backendUrl = String(import.meta.env.VITE_BACKEND_URL || '').replace(/\/$/, '')
  const token = localStorage.getItem('accessToken')
  return fetch(`${backendUrl}${BASE_URL}/demo/stop`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    keepalive: true,
  })
}

export function getEquipmentHistory(equipmentId, collectedAt, limit = 24, mode = 'LIVE') {
  return axiosClient.get(`${BASE_URL}/equipment/${encodeURIComponent(equipmentId)}/history`, {
    params: { collectedAt, limit, mode },
  })
}

export function getEquipmentAlerts(equipmentId, mode = 'LIVE', demoRunId) {
  return axiosClient.get(`${BASE_URL}/equipment/${encodeURIComponent(equipmentId)}/alerts`, {
    params: { mode, demoRunId },
  })
}

export function getEquipmentShadow(equipmentId, collectedAt, mode = 'LIVE') {
  return axiosClient.get(`${BASE_URL}/equipment/${encodeURIComponent(equipmentId)}/shadow`, {
    params: { collectedAt, mode },
  })
}

export function sendTelegramAlerts(alerts) {
  return axiosClient.post(`${BASE_URL}/telegram/alerts`, { alerts })
}


