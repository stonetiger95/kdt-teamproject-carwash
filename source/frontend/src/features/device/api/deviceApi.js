import axiosClient from "@/shared/api/axiosClient"

const BASE_URL = "/api/owner/devices"

export function getDeviceList() {
    return axiosClient.get(BASE_URL)
}

export function getDevice(deviceId) {
    return axiosClient.get(`${BASE_URL}/${deviceId}`)
}

export function updateDevice(deviceId, dto) {
    return axiosClient.patch(`${BASE_URL}/${deviceId}`, dto)
}

export function createDevice(dto){

    return axiosClient.post(
        BASE_URL,
        dto
    )
}

export function removeDevice(deviceId) {

    return axiosClient.patch(
        `${BASE_URL}/${deviceId}/remove`
    )

}



