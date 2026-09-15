import { defineStore } from "pinia"
import { ref } from "vue"
import * as memberManageApi from "../api/memberManageApi"
import * as blackListApi from "@/features/blackList/api/blackListApi"

export const useMemberManageStore = defineStore("memberManage", () => {
    const members = ref([])
    const memberDetail = ref(null)

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
    const actionLoading = ref(false)
    const actionMessage = ref("")
    const actionSuccess = ref(false)

    const statusOptions = [
        { label: "전체", value: "" },
        { label: "활성", value: "ACTIVE" },
        { label: "정지", value: "SUSPENDED" },
        { label: "탈퇴", value: "WITHDRAWN" }
    ]

    const customerTypeOptions = [
        { label: "전체", value: "" },
        { label: "회원", value: "MEMBER" },
    ]

    async function fetchMembers(params = {}) {
        errorMessage.value = ""

        try {
            loading.value = true

            const res = await memberManageApi.getMemberList({
                page: pageInfo.value.page,
                size: pageInfo.value.size,
                ...params
            })

            members.value = res.content || []

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
            errorMessage.value = "회원 목록을 불러오는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function fetchMemberDetail(customerId) {
        errorMessage.value = ""

        try {
            loading.value = true
            memberDetail.value = await memberManageApi.getMemberDetail(customerId)
            return true
        } catch (e) {
            errorMessage.value = "회원 상세 정보를 불러오는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function saveMember(customerId, memberInfo) {
        errorMessage.value = ""

        try {
            loading.value = true
            memberDetail.value = await memberManageApi.updateMember(customerId, memberInfo)
            return true
        } catch (e) {
            errorMessage.value = "회원 정보를 저장하는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function registerVehicleBlackList(customerId, plateNumber, reason) {
        clearActionMessage()

        if (!reason || !reason.trim()) {
            actionMessage.value = "개별 블랙리스트 등록 사유를 입력해주세요."
            return false
        }

        try {
            actionLoading.value = true
            const response = await blackListApi.insertIntoBlackList(
                plateNumber,
                { reason: reason.trim() }
            )
            actionMessage.value = response || "개별 블랙리스트에 등록되었습니다."
            actionSuccess.value = true
            await fetchMemberDetail(customerId)
            return true
        } catch (e) {
            actionMessage.value = getErrorMessage(
                e,
                "개별 블랙리스트 등록에 실패했습니다."
            )
            return false
        } finally {
            actionLoading.value = false
        }
    }

    async function releaseVehicleBlackList(customerId, plateNumber) {
        clearActionMessage()

        try {
            actionLoading.value = true
            const response = await blackListApi.releaseFromBlackList(plateNumber)
            actionMessage.value = response || "개별 블랙리스트에서 해제되었습니다."
            actionSuccess.value = true
            await fetchMemberDetail(customerId)
            return true
        } catch (e) {
            actionMessage.value = getErrorMessage(
                e,
                "개별 블랙리스트 해제에 실패했습니다."
            )
            return false
        } finally {
            actionLoading.value = false
        }
    }

    function clearActionMessage() {
        actionMessage.value = ""
        actionSuccess.value = false
    }

    function getErrorMessage(error, fallbackMessage) {
        const data = error?.response?.data
        if (typeof data === "string" && data) {
            return data
        }
        return data?.message || fallbackMessage
    }

    function clearMemberDetail() {
        memberDetail.value = null
        clearActionMessage()
    }

    return {
        members,
        memberDetail,
        pageInfo,
        loading,
        errorMessage,
        actionLoading,
        actionMessage,
        actionSuccess,
        statusOptions,
        customerTypeOptions,

        fetchMembers,
        fetchMemberDetail,
        saveMember,
        registerVehicleBlackList,
        releaseVehicleBlackList,
        clearMemberDetail,

    }
})
