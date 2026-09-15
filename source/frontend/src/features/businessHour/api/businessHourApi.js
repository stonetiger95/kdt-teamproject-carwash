import axiosClient from "@/shared/api/axiosClient"

export function getBusinessHours(washSiteId) {
    return axiosClient.get(
        `/api/owner/wash-sites/${washSiteId}/business-hours`
    )
}

export function updateBusinessHours(
    washSiteId,
    businessHours
) {
    return axiosClient.put(
        `/api/owner/wash-sites/${washSiteId}/business-hours`,
        businessHours
    )
}
