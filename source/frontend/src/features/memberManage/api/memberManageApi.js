import axiosClient from "@/shared/api/axiosClient"

const BASE_URL = "/api/admin/member-manage"

export function getMemberList(params) {
    return axiosClient.get(BASE_URL, { params })
}

export function getMemberDetail(customerId) {
    return axiosClient.get(`${BASE_URL}/${customerId}`)
}

export function updateMember(customerId, memberInfo) {
    return axiosClient.patch(`${BASE_URL}/${customerId}`, memberInfo)
}