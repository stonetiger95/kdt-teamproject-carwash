import { defineStore } from "pinia"
import { ref } from "vue"
import { useAuthStore } from "@/features/auth/stores/authStore"
import * as washHistoryApi from "../api/washHistoryApi"
import { filterOwnerTwoWashHistories } from "../utils/ownerTwoDummyData"

export const useWashHistoryStore = defineStore("washHistory", () => {

    const authStore = useAuthStore()

    const washHistories = ref([])
    const washHistory = ref(null)
    const pageInfo = ref({
        page: 1,
        size: 10,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false
    })
    const loading = ref(false)
    const error = ref("")
    const entryImageUrl = ref("")
    const exitImageUrl = ref("")
    const imageLoading = ref(false)
    const imageError = ref("")
    const paymentRetrying = ref(false)
    const paymentActionMessage = ref("")
    const paymentActionSuccess = ref(false)

    async function fetchWashHistoryList(viewerRole, params = {}) {
        loading.value = true
        error.value = ""

        try {
            if (viewerRole === "OWNER" && String(authStore.ownerId) === "2") {
                const histories = filterOwnerTwoWashHistories(params)
                const page = Number(params.page || 1)
                const size = Number(params.size || pageInfo.value.size)
                const start = (page - 1) * size
                const totalPages = Math.ceil(histories.length / size)

                washHistories.value = histories.slice(start, start + size)
                pageInfo.value = {
                    page,
                    size,
                    totalElements: histories.length,
                    totalPages,
                    hasNext: page < totalPages,
                    hasPrevious: page > 1
                }
                return true
            }

            if (viewerRole === "ADMIN" || viewerRole === "OWNER") {
                const response = await washHistoryApi.getWashHistoryPage(
                    viewerRole,
                    {
                        page: pageInfo.value.page,
                        size: pageInfo.value.size,
                        ...params
                    }
                )

                washHistories.value = response.content || []
                pageInfo.value = {
                    page: response.page,
                    size: response.size,
                    totalElements: response.totalElements,
                    totalPages: response.totalPages,
                    hasNext: response.hasNext,
                    hasPrevious: response.hasPrevious
                }
                return true
            }

            washHistories.value = await washHistoryApi.getWashHistoryList(
                viewerRole,
                params
            )
            pageInfo.value = {
                page: 1,
                size: pageInfo.value.size,
                totalElements: washHistories.value.length,
                totalPages: washHistories.value.length > 0 ? 1 : 0,
                hasNext: false,
                hasPrevious: false
            }
            return true
        } catch (e) {
            error.value = "세차 이용 내역을 불러오지 못했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function fetchWashHistory(viewerRole, washHistoryId) {
        loading.value = true
        error.value = ""
        washHistory.value = null

        try {
            washHistory.value = await washHistoryApi.getWashHistory(viewerRole, washHistoryId)
        } catch (e) {
            error.value = "세차 상세 내역을 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchWashHistoryImages(viewerRole, washHistoryId) {
        clearImageUrls()
        imageLoading.value = true
        imageError.value = ""

        const imageTypes = ["entry", "exit"]
        const results = await Promise.allSettled(
            imageTypes.map(imageType =>
                washHistoryApi.getWashHistoryImage(
                    viewerRole,
                    washHistoryId,
                    imageType
                )
            )
        )

        results.forEach((result, index) => {
            if (result.status === "fulfilled") {
                const imageUrl = URL.createObjectURL(result.value)

                if (imageTypes[index] === "entry") {
                    entryImageUrl.value = imageUrl
                } else {
                    exitImageUrl.value = imageUrl
                }

                return
            }

            if (result.reason?.response?.status !== 404) {
                imageError.value = "입출차 사진을 불러오지 못했습니다."
            }
        })

        imageLoading.value = false
    }

    async function retryWashPayment(washHistoryId) {
        if (!authStore.canUseFeature("payment")) {
            paymentActionMessage.value = "정지회원은 결제를 재시도할 수 없습니다."
            paymentActionSuccess.value = false
            return false
        }

        paymentRetrying.value = true
        paymentActionMessage.value = ""
        paymentActionSuccess.value = false

        try {
            const response = await washHistoryApi.retryCustomerWashPayment(
                washHistoryId
            )
            paymentActionMessage.value = response.message
            paymentActionSuccess.value = true
            await fetchWashHistory("CUSTOMER", washHistoryId)
            return true
        } catch (e) {
            paymentActionMessage.value = e.response?.data?.message
                || "결제를 완료하지 못했습니다. 잔액과 자동결제 카드를 확인해 주세요."
            return false
        } finally {
            paymentRetrying.value = false
        }
    }

    function clearImageUrls() {
        if (entryImageUrl.value) {
            URL.revokeObjectURL(entryImageUrl.value)
            entryImageUrl.value = ""
        }

        if (exitImageUrl.value) {
            URL.revokeObjectURL(exitImageUrl.value)
            exitImageUrl.value = ""
        }
    }

    function clearWashHistoryDetail() {
        washHistory.value = null
        imageError.value = ""
        paymentActionMessage.value = ""
        paymentActionSuccess.value = false
        clearImageUrls()
    }

    return {
        washHistories,
        washHistory,
        pageInfo,
        loading,
        error,
        entryImageUrl,
        exitImageUrl,
        imageLoading,
        imageError,
        paymentRetrying,
        paymentActionMessage,
        paymentActionSuccess,
        fetchWashHistoryList,
        fetchWashHistory,
        fetchWashHistoryImages,
        retryWashPayment,
        clearWashHistoryDetail
    }

})
