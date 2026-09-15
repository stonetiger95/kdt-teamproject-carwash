import { defineStore } from "pinia"
import { ref } from "vue"
import * as plateDetectionLogApi from "../api/plateDetectionLogApi"

export const usePlateDetectionLogStore = defineStore("plateDetectionLog", () => {
    const plateDetectionLogs = ref([])
    const logDetail = ref(null)
    const logImageUrl = ref("")
    const pageInfo = ref({
        page: 1,
        size: 10,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false
    })

    const loading = ref(false)
    const errorMessage = ref("")

    const failureReasonOptions = [
        { label: "흐림", value: "BLUR" },
        { label: "각도 문제", value: "ANGLE" },
        { label: "어두움", value: "DARK" },
        { label: "빛 반사", value: "GLARE" },
        { label: "번호판 일부 가림", value: "PARTIAL" },
        { label: "문자 인식 오류", value: "OCR_ERROR" },
        { label: "번호판 탐지 오류", value: "YOLO_ERROR" },
        { label: "YOLO 신뢰도 낮음", value: "LOW_YOLO_CONFIDENCE" },
        { label: "OCR 신뢰도 낮음", value: "LOW_OCR_CONFIDENCE" },
        { label: "기타/미분류", value: "UNKNOWN" }
    ]

    const reviewStatusOptions = [
        { label: "정상", value: "NORMAL" },
        { label: "실패", value: "FAILED" },
        { label: "의심", value: "SUSPECT" },
        { label: "제외", value: "EXCLUDED" }
    ]

    const logFilterOptions = [
        { label: "전체", value: "ALL" },
        { label: "실패만", value: "FAILED_ONLY" },
        { label: "검토 필요", value: "NEED_REVIEW_ONLY" }
    ]

    const downloadingTrainingData = ref(false)

    async function fetchLogs(params = {}) {
        errorMessage.value = ""

        try {
            loading.value = true

            const res = await plateDetectionLogApi.getPlateDetectionLogList({
                page: pageInfo.value.page,
                size: pageInfo.value.size,
                ...params
            })

            plateDetectionLogs.value = res.content || []

            pageInfo.value = {
                page: res.page,
                size: res.size,
                totalElements: res.totalElements,
                totalPages: res.totalPages,
                hasNext: res.hasNext,
                hasPrevious: res.hasPrevious
            }

            return true
        } catch (e) {
            errorMessage.value = "번호판 탐지 로그를 불러오는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    function buildFilterParams(filter) {
        if (filter === "FAILED_ONLY") {
            return { failedOnly: true }
        }

        if (filter === "NEED_REVIEW_ONLY") {
            return { needReviewOnly: true }
        }

        return {}
    }

    async function fetchLogDetail(plateDetectionLogId) {
        errorMessage.value = ""

        try {
            loading.value = true
            logDetail.value =
                await plateDetectionLogApi.getPlateDetectionLog(plateDetectionLogId)
            return true
        } catch (e) {
            errorMessage.value = "번호판 탐지 로그 상세 정보를 불러오는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function saveReview(plateDetectionLogId, reviewInfo) {
        errorMessage.value = ""

        try {
            loading.value = true
            logDetail.value = await plateDetectionLogApi.reviewPlateDetectionLog(
                plateDetectionLogId,
                reviewInfo
            )
            return true
        } catch (e) {
            errorMessage.value = "번호판 탐지 로그 리뷰 저장에 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function fetchLogImage(plateDetectionLogId) {
        errorMessage.value = ""

        try {
            loading.value = true

            if (logImageUrl.value) {
                URL.revokeObjectURL(logImageUrl.value)
                logImageUrl.value = ""
            }

            const blob = await plateDetectionLogApi.getPlateDetectionLogImage(
                plateDetectionLogId
            )
            logImageUrl.value = URL.createObjectURL(blob)

            return true
        } catch (e) {
            errorMessage.value = "번호판 탐지 로그 이미지를 불러오는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function downloadTrainingData() {
        if (downloadingTrainingData.value) {
            return false
        }

        errorMessage.value = ""

        try {
            downloadingTrainingData.value = true

            const blob =
                await plateDetectionLogApi.downloadTrainingData()

            const downloadUrl = URL.createObjectURL(blob)
            const timestamp = new Date()
                .toISOString()
                .replace(/[-:]/g, "")
                .slice(0, 15)
            const link = document.createElement("a")

            link.href = downloadUrl
            link.download =
                `plate-training-data-${timestamp}.zip`

            document.body.appendChild(link)
            link.click()
            link.remove()
            URL.revokeObjectURL(downloadUrl)

            return true
        } catch (e) {
            console.error("학습 데이터 다운로드 실패:", e)
            errorMessage.value =
                "학습 데이터 다운로드에 실패했습니다."
            return false
        } finally {
            downloadingTrainingData.value = false
        }
    }

    function clearLogDetail() {
        logDetail.value = null

        if (logImageUrl.value) {
            URL.revokeObjectURL(logImageUrl.value)
            logImageUrl.value = ""
        }
    }

    return {
        plateDetectionLogs,
        logDetail,
        pageInfo,
        loading,
        errorMessage,
        failureReasonOptions,
        reviewStatusOptions,
        logFilterOptions,
        logImageUrl,
        downloadingTrainingData,

        downloadTrainingData,
        fetchLogs,
        buildFilterParams,
        fetchLogDetail,
        saveReview,
        fetchLogImage,
        clearLogDetail
    }
})
