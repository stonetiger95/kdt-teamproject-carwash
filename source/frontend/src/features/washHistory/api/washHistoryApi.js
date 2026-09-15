import axiosClient from "@/shared/api/axiosClient"
import {
    nonMemberAccessConfig
} from "@/shared/utils/nonMemberAccess"

function getBaseUrl(viewerRole) {
    if (viewerRole === "CUSTOMER") {
        return "/api/customer/wash-history"
    }

    if (viewerRole === "OWNER") {
        return "/api/owner/wash-history"
    }

    if (viewerRole === "ADMIN") {
        return "/api/admin/wash-history"
    }

    throw new Error(
        "지원하지 않는 세차 내역 조회 권한입니다."
    )
}

export function getWashHistoryList(viewerRole, params = {}) {
    return axiosClient.get(getBaseUrl(viewerRole), { params })
}

export function getWashHistoryPage(viewerRole, params = {}) {
    return axiosClient.get(`${getBaseUrl(viewerRole)}/page`, { params })
}

export function getWashHistory(viewerRole, washHistoryId) {
    return axiosClient.get(`${getBaseUrl(viewerRole)}/${washHistoryId}`)
}

export function getWashHistoryImage(viewerRole, washHistoryId, imageType) {
    return axiosClient.get(
        `${getBaseUrl(viewerRole)}/${washHistoryId}/images/${imageType}`,
        { responseType: "blob" }
    )
}

export function retryCustomerWashPayment(washHistoryId) {
    return axiosClient.post(
        `/api/customer/wash-history/${washHistoryId}/retry-payment`
    )
}

export function getNonMemberWashHistoryList(
    plateLast4
) {
    return axiosClient.get(
        "/api/public/wash-history/pending",
        {
            params: {
                plateLast4
            }
        }
    )
}

export function getNonMemberWashHistoryByPlate(
    plateNumber
) {
    return axiosClient.get(
        "/api/public/wash-history",
        {
            ...nonMemberAccessConfig(),
            params: {
                plateNumber
            }
        }
    )
}

export function issueNonMemberAccessToken(
    plateNumber
) {
    return axiosClient.post(
        "/api/public/wash-history/access",
        {
            plateNumber
        }
    )
}
