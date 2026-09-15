import axiosClient from "@/shared/api/axiosClient"

function getBaseUrl(viewerRole) {
    if (viewerRole === "OWNER") {
        return "/api/owner/settlement"
    }

    if (viewerRole === "ADMIN") {
        return "/api/admin/settlement"
    }

    throw new Error("정산 조회 권한이 없는 사용자입니다.")
}

export function getSettlementDashboard(viewerRole, params) {
    return axiosClient.get(`${getBaseUrl(viewerRole)}/dashboard`, { params })
}

export function getAdminSiteComparisons(params) {
    return axiosClient.get("/api/admin/settlement/site-comparisons", { params })
}
