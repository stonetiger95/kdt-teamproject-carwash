import { defineStore } from "pinia"
import { ref } from "vue"
import { useAuthStore } from "@/features/auth/stores/authStore"
import * as settlementApi from "../api/settlementApi"
import { createOwnerTwoSettlementDashboard } from "@/features/washHistory/utils/ownerTwoDummyData"

export const useSettlementStore = defineStore("settlement", () => {
    const authStore = useAuthStore()
    const dashboard = ref(null)
    const siteComparisons = ref([])
    const loading = ref(false)
    const siteComparisonLoading = ref(false)
    const error = ref("")
    const siteComparisonError = ref("")

    async function fetchDashboard(viewerRole, params) {
        loading.value = true
        error.value = ""
        dashboard.value = null

        try {
            if (viewerRole === "OWNER" && String(authStore.ownerId) === "2") {
                dashboard.value = createOwnerTwoSettlementDashboard(params)
                return
            }

            dashboard.value = await settlementApi.getSettlementDashboard(viewerRole, params)
        } catch (e) {
            error.value = "정산 데이터를 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchSiteComparisons(params) {
        siteComparisonLoading.value = true
        siteComparisonError.value = ""
        siteComparisons.value = []

        try {
            siteComparisons.value = await settlementApi.getAdminSiteComparisons(params)
        } catch (e) {
            siteComparisonError.value = "세차장별 비교 데이터를 불러오지 못했습니다."
        } finally {
            siteComparisonLoading.value = false
        }
    }

    return {
        dashboard,
        siteComparisons,
        loading,
        siteComparisonLoading,
        error,
        siteComparisonError,
        fetchDashboard,
        fetchSiteComparisons
    }
})
