import axiosClient from "@/shared/api/axiosClient"

export function getWeather(params = {}) {
    return axiosClient.get(
        "/api/public/weather",
        { params }
    )
}