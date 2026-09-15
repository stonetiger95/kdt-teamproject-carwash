import axiosClient from "@/shared/api/axiosClient"

const BASE_URL = "/api/admin/plate-detection-logs"

// 번호판 탐지 기록 목록 조회
export function getPlateDetectionLogList(params) {
    return axiosClient.get(BASE_URL, { params })
}
// 번호판 탐지 기록 상세 조회
export function getPlateDetectionLog(plateDetectionLogId) {
    return axiosClient.get(`${BASE_URL}/${plateDetectionLogId}`)
}
// 번호판 탐지 기록 검토
export function reviewPlateDetectionLog(plateDetectionLogId, reviewInfo) {
    return axiosClient.patch(`${BASE_URL}/${plateDetectionLogId}/review`, reviewInfo)
}
// 번호판 탐지 기록 이미지 조회
export function getPlateDetectionLogImage(plateDetectionLogId) {
    return axiosClient.get(`/api/admin/plate-detection-logs/${plateDetectionLogId}/image`, { responseType: "blob" })
}

// 이미지와 검수 메타데이터가 포함된 학습 데이터 ZIP 다운로드
export function downloadTrainingData() {
    return axiosClient.get(
        `${BASE_URL}/training-data/export`,
        {
            responseType: "blob",
            timeout: 0
        }
    )
}
