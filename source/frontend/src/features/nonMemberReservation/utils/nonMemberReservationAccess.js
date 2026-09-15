const keyPrefix = "nonMemberReservationAccess:"

export function saveReservationAccess(reservationId, phone) {
    sessionStorage.setItem(
        `${keyPrefix}${reservationId}`,
        JSON.stringify({ reservationId, phone })
    )
}

export function getReservationAccess(reservationId) {
    try {
        const value = sessionStorage.getItem(`${keyPrefix}${reservationId}`)
        return value ? JSON.parse(value) : null
    } catch {
        return null
    }
}

export function removeReservationAccess(reservationId) {
    sessionStorage.removeItem(`${keyPrefix}${reservationId}`)
}
