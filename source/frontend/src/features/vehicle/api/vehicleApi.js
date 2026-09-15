import axiosClient from "@/shared/api/axiosClient"

export function vehicleRegister(dto) {
    return axiosClient.post("/api/customer/vehicles", dto )
}

export function vehicleList() {
    return axiosClient.get("/api/customer/vehicles")
}

export function vehicleDelete(vehicleId) {
    return axiosClient.delete(`/api/customer/vehicles/${vehicleId}`)
}
