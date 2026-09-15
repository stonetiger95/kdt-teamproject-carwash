import { computed, ref } from "vue"
import { defineStore } from "pinia"
import * as blackListApi from "../api/blackListApi"

export const useBlackListStore = defineStore("blackList", () => {
    const vehicles = ref([])
    const vehicleDetail = ref(null)
    const blackList = ref([])

    const vehiclePageInfo = ref(createPageInfo())
    const blackListPageInfo = ref(createPageInfo())

    const loading = ref(false)
    const actionLoading = ref(false)
    const errorMessage = ref("")
    const actionMessage = ref("")
    const actionSuccess = ref(false)

    const canRegisterManual = computed(() =>
        Boolean(vehicleDetail.value)
        && !vehicleDetail.value.manualBlacklisted
    )
    const canReleaseManual = computed(() =>
        Boolean(vehicleDetail.value?.manualBlacklisted)
    )

    const blackListTypeOptions = [
        { label: "전체", value: "" },
        { label: "수동 등록", value: "MANUAL" },
        { label: "회원 정지", value: "CUSTOMER_SUSPENDED" }
    ]

    const activeOptions = [
        { label: "전체", value: null },
        { label: "활성", value: true },
        { label: "해제", value: false }
    ]

    async function fetchVehicles(params = {}) {
        errorMessage.value = ""

        try {
            loading.value = true

            const res = await blackListApi.getVehicleList({
                page: vehiclePageInfo.value.page,
                size: vehiclePageInfo.value.size,
                ...params
            })

            vehicles.value = res.content || []
            vehiclePageInfo.value = toPageInfo(res)
            return true
        } catch (e) {
            errorMessage.value = getErrorMessage(
                e,
                "차량 목록을 불러오는 데 실패했습니다."
            )
            return false
        } finally {
            loading.value = false
        }
    }

    async function fetchVehicleDetail(plateNumber) {
        errorMessage.value = ""
        clearActionMessage()
        vehicleDetail.value = null

        try {
            loading.value = true
            vehicleDetail.value =
                await blackListApi.getVehicleDetail(plateNumber)
            return true
        } catch (e) {
            errorMessage.value = getErrorMessage(
                e,
                "차량 상세 정보를 불러오는 데 실패했습니다."
            )
            return false
        } finally {
            loading.value = false
        }
    }

    async function fetchBlackList(params = {}) {
        errorMessage.value = ""

        try {
            loading.value = true

            const res = await blackListApi.getBlackList({
                page: blackListPageInfo.value.page,
                size: blackListPageInfo.value.size,
                ...params
            })

            blackList.value = res.content || []
            blackListPageInfo.value = toPageInfo(res)
            return true
        } catch (e) {
            errorMessage.value = getErrorMessage(
                e,
                "블랙리스트 이력을 불러오는 데 실패했습니다."
            )
            return false
        } finally {
            loading.value = false
        }
    }

    async function registerBlackList(plateNumber, reason) {
        clearActionMessage()

        if (!reason || !reason.trim()) {
            actionMessage.value = "블랙리스트 등록 사유를 입력해주세요."
            return false
        }

        try {
            actionLoading.value = true

            const response = await blackListApi.insertIntoBlackList(
                plateNumber,
                { reason: reason.trim() }
            )

            actionMessage.value = response || "블랙리스트에 등록되었습니다."
            actionSuccess.value = true
            await refreshVehicleDetail(plateNumber)
            return true
        } catch (e) {
            actionMessage.value = getErrorMessage(
                e,
                "블랙리스트 등록에 실패했습니다."
            )
            return false
        } finally {
            actionLoading.value = false
        }
    }

    async function releaseBlackList(plateNumber) {
        clearActionMessage()

        try {
            actionLoading.value = true

            const response =
                await blackListApi.releaseFromBlackList(plateNumber)

            actionMessage.value = response || "블랙리스트에서 해제되었습니다."
            actionSuccess.value = true
            await refreshVehicleDetail(plateNumber)
            return true
        } catch (e) {
            actionMessage.value = getErrorMessage(
                e,
                "블랙리스트 해제에 실패했습니다."
            )
            return false
        } finally {
            actionLoading.value = false
        }
    }

    async function refreshVehicleDetail(plateNumber) {
        vehicleDetail.value =
            await blackListApi.getVehicleDetail(plateNumber)

        const vehicle = vehicles.value.find(
            item => item.plateNumber === plateNumber
        )

        if (vehicle) {
            vehicle.blacklisted = vehicleDetail.value.blacklisted
        }
    }

    function clearVehicleDetail() {
        vehicleDetail.value = null
        errorMessage.value = ""
        clearActionMessage()
    }

    function clearActionMessage() {
        actionMessage.value = ""
        actionSuccess.value = false
    }

    function createPageInfo() {
        return {
            page: 1,
            size: 10,
            totalElements: 0,
            totalPages: 0,
            hasNext: false,
            hasPrevious: false
        }
    }

    function toPageInfo(response) {
        return {
            page: response.page,
            size: response.size,
            totalElements: response.totalElements,
            totalPages: response.totalPages,
            hasNext: response.hasNext,
            hasPrevious: response.hasPrevious
        }
    }

    function getErrorMessage(error, fallbackMessage) {
        const data = error?.response?.data

        if (typeof data === "string" && data) {
            return data
        }

        return data?.message || fallbackMessage
    }

    return {
        vehicles,
        vehicleDetail,
        blackList,
        vehiclePageInfo,
        blackListPageInfo,
        loading,
        actionLoading,
        errorMessage,
        actionMessage,
        actionSuccess,
        canRegisterManual,
        canReleaseManual,
        blackListTypeOptions,
        activeOptions,
        
        fetchVehicles,
        fetchVehicleDetail,
        fetchBlackList,
        registerBlackList,
        releaseBlackList,
        clearVehicleDetail,
        clearActionMessage
    }
})
