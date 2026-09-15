import axiosClient from "@/shared/api/axiosClient"

const baseUrl = "/api/public/non-member-reservations"

export function getWashSites() {
    return axiosClient.get(`${baseUrl}/wash-sites`)
}

export function getAvailableSlots(washSiteId, date) {
    return axiosClient.get(`${baseUrl}/available-slots`, {
        params: { washSiteId, date }
    })
}

export function createReservation(payload) {
    return axiosClient.post(baseUrl, payload)
}

export function lookupReservation(payload) {
    return axiosClient.post(`${baseUrl}/lookup`, payload)
}

export function requestPayment(payload) {
    return axiosClient.post(`${baseUrl}/payments`, payload)
}

export function cancelReservation(reservationId, payload) {
    return axiosClient.patch(
        `${baseUrl}/${reservationId}/cancel`,
        payload,
        { timeout: 30000 }
    )
}
