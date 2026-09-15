import axiosClient from "@/shared/api/axiosClient"

const BASE_URL = "/api/admin/vehicle"

export function getVehicleList(params) {
    return axiosClient.get(BASE_URL, { params })
}

export function getBlackList(params) {
    return axiosClient.get(`${BASE_URL}/blacklist`, { params })
}

export function getVehicleDetail(plateNumber) {
    return axiosClient.get(
        `${BASE_URL}/${encodeURIComponent(plateNumber)}`
    )
}

export function insertIntoBlackList(plateNumber, blackListInfo) {
    return axiosClient.post(
        `${BASE_URL}/${encodeURIComponent(plateNumber)}/blacklist`,
        blackListInfo
    )
}

export function releaseFromBlackList(plateNumber) {
    return axiosClient.patch(
        `${BASE_URL}/${encodeURIComponent(plateNumber)}/blacklist/release`
    )
}
