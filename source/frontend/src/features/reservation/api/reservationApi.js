import axiosClient from "@/shared/api/axiosClient"

export function getCustomerWashSites() {
    return axiosClient.get("/api/customer/wash-sites")
}

export function getAvailableSlots(washSiteId, date) {
    return axiosClient.get(
        "/api/customer/reservations/available-slots",
        { params: { washSiteId, date } }
    )
}

export function createReservation(payload) {
    return axiosClient.post("/api/customer/reservations", payload)
}

export function getCustomerReservations(params = {}) {
    return axiosClient.get("/api/customer/reservations", { params })
}

export function getCustomerReservation(reservationId) {
    return axiosClient.get(`/api/customer/reservations/${reservationId}`)
}

export function cancelCustomerReservation(reservationId, cancelReason) {
    return axiosClient.patch(
        `/api/customer/reservations/${reservationId}/cancel`,
        { cancelReason }
    )
}

export function getOwnerReservations(params = {}) {
    return axiosClient.get("/api/owner/reservations", { params })
}

export function getOwnerReservation(reservationId) {
    return axiosClient.get(`/api/owner/reservations/${reservationId}`)
}

export function getOwnerReservationDashboard(date) {
    return axiosClient.get(
        "/api/owner/reservations/dashboard",
        { params: date ? { date } : {} }
    )
}

export function cancelOwnerReservation(reservationId, cancelReason) {
    return axiosClient.patch(
        `/api/owner/reservations/${reservationId}/cancel`,
        { cancelReason }
    )
}

export function getAdminReservations(params = {}) {
    return axiosClient.get("/api/admin/reservations", { params })
}

export function getAdminReservation(reservationId) {
    return axiosClient.get(`/api/admin/reservations/${reservationId}`)
}
