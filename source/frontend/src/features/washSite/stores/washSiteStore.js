import { defineStore } from "pinia"
import { ref } from "vue"
import * as washSiteApi from "../api/washSiteApi"

export const useWashSiteStore = defineStore("washSite", () => {
    const owners = ref([])
    const washSites = ref([])
    const washSite = ref(null)
    const loading = ref(false)
    const error = ref("")
    const mapWashSites = ref([])
    const selectedMapSite = ref(null)
    const mapLoading = ref(false)
    const mapError = ref("")
    const favoriteWashSiteIds = ref([])
    const favoriteUpdatingIds = ref([])
    const favoriteError = ref("")

    async function fetchOwners() {
        loading.value = true
        error.value = ""

        try {
            owners.value = await washSiteApi.getOwnerList()
        } catch (e) {
            error.value = "사업주 목록을 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchWashSites(viewerRole, ownerId = "") {
        loading.value = true
        error.value = ""

        try {
            if (viewerRole === "ADMIN" && ownerId) {
                washSites.value = await washSiteApi.getWashSiteListByOwner(ownerId)
            } else {
                washSites.value = await washSiteApi.getWashSiteList(viewerRole)
            }
        } catch (e) {
            error.value = "세차장 목록을 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchWashSite(viewerRole, washSiteId) {
        loading.value = true
        error.value = ""
        washSite.value = null

        try {
            washSite.value = await washSiteApi.getWashSite(viewerRole, washSiteId)
        } catch (e) {
            error.value = "세차장 상세 정보를 불러오지 못했습니다."
        } finally {
            loading.value = false
        }
    }

    async function fetchMapWashSites(params = {}) {
        mapLoading.value = true
        mapError.value = ""

        try {
            mapWashSites.value = await washSiteApi.getMapWashSites(params)
            return mapWashSites.value
        } catch (e) {
            mapError.value = e.response?.data?.message || "세차장 위치를 불러오지 못했습니다."
            return []
        } finally {
            mapLoading.value = false
        }
    }

    async function fetchMapWashSite(washSiteId) {
        mapError.value = ""

        try {
            selectedMapSite.value = await washSiteApi.getMapWashSite(washSiteId)
            return selectedMapSite.value
        } catch (e) {
            mapError.value = e.response?.data?.message || "세차장 상세 정보를 불러오지 못했습니다."
            return null
        }
    }

    async function fetchFavoriteWashSiteIds() {
        favoriteError.value = ""

        try {
            const ids = await washSiteApi.getFavoriteWashSiteIds()
            favoriteWashSiteIds.value = Array.isArray(ids)
                ? ids.map(Number).filter(Number.isFinite)
                : []
            return favoriteWashSiteIds.value
        } catch (e) {
            favoriteError.value = e.response?.data?.message || "즐겨찾기를 불러오지 못했습니다."
            return []
        }
    }

    function isFavoriteWashSite(washSiteId) {
        return favoriteWashSiteIds.value.includes(Number(washSiteId))
    }

    function isFavoriteUpdating(washSiteId) {
        return favoriteUpdatingIds.value.includes(Number(washSiteId))
    }

    async function toggleFavoriteWashSite(washSiteId) {
        const normalizedId = Number(washSiteId)
        if (!Number.isFinite(normalizedId) || isFavoriteUpdating(normalizedId)) {
            return false
        }

        favoriteError.value = ""
        favoriteUpdatingIds.value = [...favoriteUpdatingIds.value, normalizedId]
        const wasFavorite = isFavoriteWashSite(normalizedId)

        try {
            if (wasFavorite) {
                await washSiteApi.removeFavoriteWashSite(normalizedId)
                favoriteWashSiteIds.value = favoriteWashSiteIds.value
                    .filter(id => id !== normalizedId)
            } else {
                await washSiteApi.addFavoriteWashSite(normalizedId)
                favoriteWashSiteIds.value = [
                    normalizedId,
                    ...favoriteWashSiteIds.value.filter(id => id !== normalizedId)
                ]
            }
            return true
        } catch (e) {
            favoriteError.value = e.response?.data?.message || "즐겨찾기를 변경하지 못했습니다."
            return false
        } finally {
            favoriteUpdatingIds.value = favoriteUpdatingIds.value
                .filter(id => id !== normalizedId)
        }
    }

    async function saveWashSite(viewerRole, washSiteInfo, washSiteId = null) {
        loading.value = true
        error.value = ""

        try {
            if (washSiteId) {
                await washSiteApi.updateWashSite(viewerRole, washSiteId, washSiteInfo)
            } else {
                await washSiteApi.createWashSite(viewerRole, washSiteInfo)
            }

            return true
        } catch (e) {
            error.value = "세차장 정보를 저장하지 못했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    return {
        owners,
        washSites,
        washSite,
        loading,
        error,
        mapWashSites,
        selectedMapSite,
        mapLoading,
        mapError,
        favoriteWashSiteIds,
        favoriteUpdatingIds,
        favoriteError,
        fetchOwners,
        fetchWashSites,
        fetchWashSite,
        fetchMapWashSites,
        fetchMapWashSite,
        fetchFavoriteWashSiteIds,
        isFavoriteWashSite,
        isFavoriteUpdating,
        toggleFavoriteWashSite,
        saveWashSite
    }
})
