import axiosClient from "@/shared/api/axiosClient"

export function registerOwner(ownerInfo) {
    return axiosClient.post(
        "/api/admin/owners",
        ownerInfo
    )
}
