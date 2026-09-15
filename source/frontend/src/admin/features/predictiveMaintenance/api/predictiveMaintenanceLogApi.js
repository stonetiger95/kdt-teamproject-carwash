import axiosClient from '@/shared/api/axiosClient'

/** Server-side filtered PM sensor/prediction audit log. */
export function getPredictiveMaintenanceLogs(params) {
  return axiosClient.get('/api/admin/predictive-maintenance/logs', { params })
}

/** Reads the persisted observation-only v2 result; it never recomputes history. */
export function getStoredShadowPrediction(params) {
  return axiosClient.get('/api/admin/predictive-maintenance/logs/shadow', { params })
}
