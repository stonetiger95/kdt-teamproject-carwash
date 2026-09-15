import axiosClient from "@/shared/api/axiosClient"

export function getPredictiveMaintenanceDashboard() {
    return axiosClient.get("/api/admin/predictive-maintenance/dashboard")
}

export function getLatestPredictiveMaintenanceDashboard() {
    return axiosClient.get("/api/admin/predictive-maintenance/dashboard/latest")
}

export function getLivePredictiveMaintenanceDashboard() {
    return axiosClient.get("/api/admin/predictive-maintenance/dashboard/live")
}

export function getPredictiveMaintenanceDashboardByMode(mode = "LIVE") {
    return axiosClient.get("/api/admin/predictive-maintenance/dashboard/mode", { params: { mode } })
}

export function startPredictiveMaintenanceDemo(telegramEnabled = false) {
    return axiosClient.post("/api/admin/predictive-maintenance/demo/start", { telegramEnabled })
}

export function stopPredictiveMaintenanceDemo() {
    return axiosClient.post("/api/admin/predictive-maintenance/demo/stop")
}

export function getPredictiveMaintenanceDemoStatus() {
    return axiosClient.get("/api/admin/predictive-maintenance/demo/status")
}

export function getPredictiveMaintenanceDemoAlerts(demoRunId, equipmentId) {
    return axiosClient.get(`/api/admin/predictive-maintenance/demo/${demoRunId}/alerts`, { params: { equipmentId } })
}

export function getEquipmentAlerts(equipmentId, mode = "LIVE", demoRunId) {
    return axiosClient.get(`/api/admin/predictive-maintenance/equipment/${encodeURIComponent(equipmentId)}/alerts`, {
        params: { mode, demoRunId }
    })
}

export function restartLivePredictiveMaintenanceDashboard() {
    return axiosClient.post("/api/admin/predictive-maintenance/dashboard/live/restart")
}

export function stopLivePredictiveMaintenanceDashboard() {
    return axiosClient.post("/api/admin/predictive-maintenance/dashboard/live/stop")
}

export function getNextPredictiveMaintenanceDashboard(currentTime) {
    return axiosClient.get(
        "/api/admin/predictive-maintenance/dashboard/next",
        { params: { currentTime } }
    )
}

export function getPredictiveMaintenanceDashboardAt(collectedAt) {
    return axiosClient.get(
        "/api/admin/predictive-maintenance/dashboard/at",
        { params: { collectedAt } }
    )
}

export function predictEquipment(payload) {
    return axiosClient.post("/api/admin/predictive-maintenance/predict", payload)
}

export function sendTelegramAlertMessages(alerts) {
    return axiosClient.post(
        "/api/admin/predictive-maintenance/telegram/alerts",
        { alerts }
    )
}

export function getEquipmentHistory(equipmentId, collectedAt, limit = 24, mode = "LIVE") {
    return axiosClient.get(
        `/api/admin/predictive-maintenance/equipment/${encodeURIComponent(equipmentId)}/history`,
        { params: { collectedAt, limit, mode } }
    )
}
