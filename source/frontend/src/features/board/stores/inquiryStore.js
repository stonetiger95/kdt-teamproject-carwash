import { defineStore } from "pinia"
import { ref } from "vue"
import * as inquiryApi from "../api/inquiryApi"

export const useInquiryStore = defineStore("inquiry", () => {

    const inquiries = ref([])

    const inquiry = ref(null)

    const usedWashSites = ref([])

    const loading = ref(false)

    const siteLoading = ref(false)

    const error = ref("")

    async function fetchInquiryList() {

        loading.value = true
        error.value = ""

        try {
            inquiries.value = await inquiryApi.getInquiryList()
        } catch (e) {
            error.value = "문의 목록을 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchInquiry(inquiryId) {

        loading.value = true
        error.value = ""

        try {
            inquiry.value = await inquiryApi.getInquiry(inquiryId)
        } catch (e) {
            error.value = "문의 내용을 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchUsedWashSites() {

        siteLoading.value = true
        error.value = ""

        try {
            usedWashSites.value = await inquiryApi.getUsedWashSites()
        } catch (e) {
            usedWashSites.value = []
            error.value = "이용한 세차장 목록을 불러오지 못했습니다."
        } finally {
            siteLoading.value = false
        }
    }

    async function createInquiry(inquiryInfo) {

        error.value = ""

        try {
            await inquiryApi.createInquiry(inquiryInfo)
            return true
        } catch (e) {
            error.value = e.response?.data?.message
                || e.response?.data?.detail
                || "문의를 등록하지 못했습니다."
            return false
        }
    }

    async function updateInquiry(inquiryId, inquiryInfo) {

        error.value = ""

        try {
            const result = await inquiryApi.updateInquiry(inquiryId, inquiryInfo)

            if (result === 0) {
                error.value = "이 문의를 수정할 권한이 없습니다."
                return false
            }

            return true
        } catch (e) {
            error.value = e.response?.status === 409
                ? "답변이 등록된 문의는 수정할 수 없습니다."
                : "문의를 수정하지 못했습니다."
            return false
        }
    }

    async function answerInquiry(inquiryId, answerInfo) {

        error.value = ""

        try {
            const result = await inquiryApi.answerInquiry(inquiryId, answerInfo)

            if (result === 0) {
                error.value = "이 문의에 답변할 권한이 없습니다."
                return false
            }

            return true
        } catch (e) {
            error.value = "답변을 추가하지 못했습니다."
            return false
        }
    }

    async function refundInquiry(inquiryId, refundInfo) {
        error.value = ""

        try {
            const result = await inquiryApi.refundInquiry(inquiryId, refundInfo)

            if (result === 0) {
                error.value = "환불할 결제 내역을 찾을 수 없습니다."
                return false
            }

            return true
        } catch (e) {
            error.value = e.response?.data?.message
                || "환불 처리에 실패했습니다."
            return false
        }
    }

    async function deleteInquiry(inquiryId) {

        error.value = ""

        try {
            const result = await inquiryApi.deleteInquiry(inquiryId)

            if (result === 0) {
                error.value = "이 문의를 삭제할 권한이 없습니다."
                return false
            }

            return true
        } catch (e) {
            error.value = e.response?.status === 409
                ? "답변이 등록된 문의는 삭제할 수 없습니다."
                : "문의를 삭제하지 못했습니다."
            return false
        }
    }

    return {
        inquiries,
        inquiry,
        usedWashSites,
        loading,
        siteLoading,
        error,
        fetchInquiryList,
        fetchInquiry,
        fetchUsedWashSites,
        createInquiry,
        updateInquiry,
        answerInquiry,
        refundInquiry,
        deleteInquiry
    }

})
