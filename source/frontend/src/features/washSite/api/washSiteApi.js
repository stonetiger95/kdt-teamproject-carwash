import axiosClient from "@/shared/api/axiosClient"

function getBaseUrl(viewerRole) {
    if (viewerRole === "OWNER") {
        return "/api/owner/wash-sites"
    }

    if (viewerRole === "ADMIN") {
        return "/api/admin/wash-sites"
    }

    throw new Error("세차장 관리 권한이 없습니다.")
}

export function getOwnerList() {
    return axiosClient.get("/api/admin/owners")
}

export function getWashSiteList(viewerRole) {
    return axiosClient.get(getBaseUrl(viewerRole))
}

export function getWashSiteListByOwner(ownerId) {
    return axiosClient.get(`/api/admin/owners/${ownerId}/wash-sites`)
}

export function getWashSite(viewerRole, washSiteId) {
    return axiosClient.get(`${getBaseUrl(viewerRole)}/${washSiteId}`)
}

export function createWashSite(viewerRole, washSiteInfo) {
    return axiosClient.post(getBaseUrl(viewerRole), washSiteInfo)
}

export function updateWashSite(viewerRole, washSiteId, washSiteInfo) {
    return axiosClient.patch(`${getBaseUrl(viewerRole)}/${washSiteId}`, washSiteInfo)
}

export function getMapWashSites(params = {}) {
    return axiosClient.get("/api/public/wash-sites/map", { params })
}

export function getMapWashSite(washSiteId) {
    return axiosClient.get(`/api/public/wash-sites/${washSiteId}/map`)
}

export function getDrivingDirections(params) {
    return axiosClient.get("/api/public/directions/driving", { params })
}

export function getFavoriteWashSiteIds() {
    return axiosClient.get("/api/customer/wash-sites/favorites")
}

export function addFavoriteWashSite(washSiteId) {
    return axiosClient.post(`/api/customer/wash-sites/${washSiteId}/favorite`)
}

export function removeFavoriteWashSite(washSiteId) {
    return axiosClient.delete(`/api/customer/wash-sites/${washSiteId}/favorite`)
}
