<template>
    <main class="map-page">
        <header class="map-header">
            <div>
                <p :class="['eyebrow', activeProvider]">{{ activeProviderInfo.eyebrow }}</p>
                <h1>세차장 찾기</h1>
                <p>운영 중인 세차장의 위치와 기본 세차 정보를 확인합니다.</p>
            </div>

            <div class="header-actions">
                <div class="provider-switch" role="group" aria-label="지도 선택">
                    <button
                        v-for="provider in mapProviders"
                        :key="provider.id"
                        :class="{ active: activeProvider === provider.id }"
                        type="button"
                        :aria-pressed="activeProvider === provider.id"
                        @click="switchMapProvider(provider.id)"
                    >
                        {{ provider.label }}
                    </button>
                </div>
                <RouterLink class="home-link" :to="homeTarget">홈으로</RouterLink>
            </div>
        </header>

        <section class="search-toolbar" aria-label="세차장 검색">
            <label class="search-field">
                <span>세차장명 또는 주소</span>
                <input
                    v-model.trim="keyword"
                    type="search"
                    placeholder="예: 강남, 알파 세차장"
                    @keyup.enter="searchAll"
                >
            </label>

            <button class="primary-button" type="button" @click="searchAll">
                검색
            </button>
            <button class="secondary-button" type="button" @click="findNearbySites">
                내 주변
            </button>
            <button
                class="secondary-button"
                type="button"
                :disabled="!mapReady"
                @click="searchCurrentArea"
            >
                현재 지도에서 검색
            </button>
        </section>

        <p v-if="locationMessage" class="notice-text" role="status">
            {{ locationMessage }}
        </p>

        <section class="map-workspace">
            <aside class="site-panel" aria-label="세차장 목록">
                <div class="panel-heading">
                    <div>
                        <strong>검색 결과</strong>
                        <span>{{ visibleSites.length }}곳</span>
                    </div>
                    <div class="panel-controls">
                        <label v-if="isCustomer" class="favorite-filter">
                            <input
                                v-model="favoritesOnly"
                                type="checkbox"
                                @change="refreshVisibleMarkers"
                            >
                            <span>즐겨찾기만</span>
                        </label>
                        <button
                            v-if="keyword || userLocation"
                            class="text-button"
                            type="button"
                            @click="resetSearch"
                        >
                            초기화
                        </button>
                    </div>
                </div>

                <p v-if="store.favoriteError" class="favorite-error" role="alert">
                    {{ store.favoriteError }}
                </p>
                <p v-if="store.mapLoading" class="state-box">세차장을 불러오는 중입니다.</p>
                <p v-else-if="store.mapError" class="state-box error" role="alert">
                    {{ store.mapError }}
                </p>
                <p v-else-if="visibleSites.length === 0" class="state-box">
                    {{ favoritesOnly ? "즐겨찾기한 세차장이 없습니다." : "조건에 맞는 운영 중 세차장이 없습니다." }}
                </p>

                <div v-else class="site-list">
                    <article
                        v-for="site in visibleSites"
                        :key="site.washSiteId"
                        :class="['site-row', { selected: selectedSite?.washSiteId === site.washSiteId }]"
                    >
                        <button class="site-row-main" type="button" @click="selectSite(site)">
                            <span class="site-row-heading">
                                <strong>{{ site.siteName || `세차장 #${site.washSiteId}` }}</strong>
                                <span v-if="site.distanceKm !== null && site.distanceKm !== undefined">
                                    {{ Number(site.distanceKm).toFixed(1) }}km
                                </span>
                            </span>
                            <span class="site-address">{{ site.address || "주소 미등록" }}</span>
                            <span class="site-detail">
                                {{ site.courseName || "기본 코스" }}
                                <b>{{ formatAmount(site.washPrice) }}</b>
                            </span>
                        </button>
                        <div class="site-row-actions">
                            <button
                                v-if="isCustomer"
                                :class="['favorite-button', { active: store.isFavoriteWashSite(site.washSiteId) }]"
                                type="button"
                                :disabled="store.isFavoriteUpdating(site.washSiteId)"
                                :title="store.isFavoriteWashSite(site.washSiteId) ? '즐겨찾기 해제' : '즐겨찾기 추가'"
                                :aria-label="`${site.siteName || '세차장'} ${store.isFavoriteWashSite(site.washSiteId) ? '즐겨찾기 해제' : '즐겨찾기 추가'}`"
                                :aria-pressed="store.isFavoriteWashSite(site.washSiteId)"
                                @click="toggleFavorite(site)"
                            >
                                <span aria-hidden="true">
                                    {{ store.isFavoriteWashSite(site.washSiteId) ? "★" : "☆" }}
                                </span>
                            </button>
                            <button
                                :class="['site-directions-button', activeProvider]"
                                type="button"
                                :aria-label="`${site.siteName || '세차장'} ${activeProviderInfo.directionsLabel}`"
                                @click="openDirections(site)"
                            >
                                {{ activeProviderInfo.directionsLabel }}
                            </button>
                        </div>
                    </article>
                </div>
            </aside>

            <div class="map-stage">
                <div
                    ref="mapElement"
                    class="map-canvas"
                    :aria-label="`${activeProviderInfo.label} 세차장 지도`"
                ></div>

                <div v-if="sdkError" class="map-overlay error" role="alert">
                    <strong>{{ activeProviderInfo.label }}를 표시할 수 없습니다.</strong>
                    <span>{{ sdkError }}</span>
                    <small>목록은 계속 이용할 수 있습니다.</small>
                </div>

                <div v-else-if="!mapReady" class="map-overlay">
                    {{ activeProviderInfo.label }}를 준비하고 있습니다.
                </div>

                <section v-if="selectedSite" class="selected-site" aria-live="polite">
                    <div>
                        <strong>{{ selectedSite.siteName }}</strong>
                        <span>{{ selectedSite.address }}</span>
                    </div>
                    <div class="selected-site-actions">
                        <button
                            v-if="isCustomer"
                            :class="['favorite-button', { active: store.isFavoriteWashSite(selectedSite.washSiteId) }]"
                            type="button"
                            :disabled="store.isFavoriteUpdating(selectedSite.washSiteId)"
                            :title="store.isFavoriteWashSite(selectedSite.washSiteId) ? '즐겨찾기 해제' : '즐겨찾기 추가'"
                            :aria-label="store.isFavoriteWashSite(selectedSite.washSiteId) ? '즐겨찾기 해제' : '즐겨찾기 추가'"
                            :aria-pressed="store.isFavoriteWashSite(selectedSite.washSiteId)"
                            @click="toggleFavorite(selectedSite)"
                        >
                            <span aria-hidden="true">
                                {{ store.isFavoriteWashSite(selectedSite.washSiteId) ? "★" : "☆" }}
                            </span>
                        </button>
                        <button type="button" @click="openDirections(selectedSite)">
                            {{ activeProviderInfo.directionsLabel }}
                        </button>
                    </div>
                </section>
            </div>
        </section>
    </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue"
import { RouterLink } from "vue-router"
import { useAuthStore } from "@/features/auth/stores/authStore"
import { ROLE } from "@/shared/auth/roles"
import { useWashSiteStore } from "../stores/washSiteStore"
import { geocodeWithNaver, loadNaverMapSdk } from "../utils/naverMapLoader"
import { geocodeWithKakao, loadKakaoMapSdk } from "../utils/kakaoMapLoader"

const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.9780 }
const DEFAULT_ZOOM = 11
const MAP_PROVIDERS = [
    {
        id: "naver",
        label: "네이버 지도",
        eyebrow: "NAVER MAP",
        directionsLabel: "네이버 길찾기"
    },
    {
        id: "kakao",
        label: "카카오 지도",
        eyebrow: "KAKAO MAP",
        directionsLabel: "카카오 길찾기"
    }
]

const authStore = useAuthStore()
const store = useWashSiteStore()
const mapElement = ref(null)
const displaySites = ref([])
const selectedSite = ref(null)
const keyword = ref("")
const mapReady = ref(false)
const sdkError = ref("")
const locationMessage = ref("")
const userLocation = ref(null)
const favoritesOnly = ref(false)
const mapProviders = MAP_PROVIDERS
const activeProvider = ref(
    window.localStorage.getItem("washSiteMapProvider") === "kakao"
        ? "kakao"
        : "naver"
)

let map
let mapSdk
let infoWindow
let userMarker
let markerEntries = []
const geocodeCache = new Map()

const homeTarget = computed(() => {
    if (authStore.role === ROLE.CUSTOMER) {
        return { name: "customer-dashboard" }
    }
    return { name: "home" }
})

const activeProviderInfo = computed(() => (
    MAP_PROVIDERS.find(provider => provider.id === activeProvider.value)
    || MAP_PROVIDERS[0]
))

const isCustomer = computed(() => (
    authStore.role === ROLE.CUSTOMER && Boolean(authStore.accessToken)
))

const visibleSites = computed(() => {
    const favorites = new Set(store.favoriteWashSiteIds.map(Number))
    const sites = favoritesOnly.value
        ? displaySites.value.filter(site => favorites.has(Number(site.washSiteId)))
        : [...displaySites.value]

    return sites.sort((left, right) => {
        const favoriteOrder = Number(favorites.has(Number(right.washSiteId)))
            - Number(favorites.has(Number(left.washSiteId)))
        return favoriteOrder || 0
    })
})

onMounted(async () => {
    await initializeMap()
    if (isCustomer.value) {
        await store.fetchFavoriteWashSiteIds()
    }
    await loadSites()
})

onBeforeUnmount(() => {
    disposeMap()
})

async function initializeMap() {
    mapReady.value = false
    sdkError.value = ""

    try {
        const maps = activeProvider.value === "kakao"
            ? await loadKakaoMapSdk()
            : await loadNaverMapSdk()
        await nextTick()
        mapSdk = maps

        if (activeProvider.value === "kakao") {
            map = new maps.Map(mapElement.value, {
                center: new maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude),
                level: 8
            })
            if (maps.ZoomControl && maps.ControlPosition?.RIGHT) {
                map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT)
            }
            if (maps.MapTypeControl && maps.ControlPosition?.TOPRIGHT) {
                map.addControl(new maps.MapTypeControl(), maps.ControlPosition.TOPRIGHT)
            }
            infoWindow = new maps.InfoWindow({ removable: false })
        } else {
            map = new maps.Map(mapElement.value, {
                center: new maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude),
                zoom: DEFAULT_ZOOM,
                zoomControl: true,
                zoomControlOptions: {
                    position: maps.Position.TOP_RIGHT
                },
                mapTypeControl: true
            })
            infoWindow = new maps.InfoWindow({
                borderWidth: 0,
                backgroundColor: "transparent",
                disableAnchor: true
            })
        }
        mapReady.value = true
    } catch (error) {
        mapSdk = null
        sdkError.value = error.message || `${activeProviderInfo.value.label} SDK를 불러오지 못했습니다.`
    }
}

function switchMapProvider(providerId) {
    if (providerId === activeProvider.value) {
        return
    }

    window.localStorage.setItem("washSiteMapProvider", providerId)
    window.location.reload()
}

async function loadSites(params = {}, fitToResults = true) {
    selectedSite.value = null
    const result = await store.fetchMapWashSites(params)
    displaySites.value = await resolveMissingCoordinates(result)

    if (mapReady.value) {
        renderMarkers(fitToResults)
    }

    return displaySites.value
}

async function resolveMissingCoordinates(sites) {
    if (!mapReady.value) {
        return sites
    }

    return Promise.all(sites.map(async site => {
        if (isCoordinate(site.latitude, site.longitude) || !site.address) {
            return site
        }

        try {
            const cacheKey = `${activeProvider.value}:${site.address}`
            if (!geocodeCache.has(cacheKey)) {
                const coordinates = activeProvider.value === "kakao"
                    ? await geocodeWithKakao(site.address)
                    : await geocodeWithNaver(site.address)
                geocodeCache.set(cacheKey, coordinates)
            }
            return { ...site, ...geocodeCache.get(cacheKey) }
        } catch {
            return site
        }
    }))
}

function renderMarkers(fitToResults) {
    clearMarkers()
    const maps = getActiveMaps()
    if (!mapReady.value || !map || !maps) {
        return
    }
    const bounds = new maps.LatLngBounds()
    let coordinateCount = 0

    visibleSites.value.forEach(site => {
        if (!isCoordinate(site.latitude, site.longitude)) {
            return
        }

        const position = new maps.LatLng(site.latitude, site.longitude)
        const marker = new maps.Marker({
            map,
            position,
            title: site.siteName || "세차장"
        })
        const listener = addMapListener(marker, "click", () => selectSite(site))
        markerEntries.push({ marker, listener, washSiteId: site.washSiteId })
        bounds.extend(position)
        coordinateCount += 1
    })

    if (fitToResults && coordinateCount > 0) {
        if (activeProvider.value === "kakao") {
            map.setBounds(bounds, 72, 72, 72, 72)
        } else {
            map.fitBounds(bounds, { top: 72, right: 72, bottom: 72, left: 72 })
        }
        if (coordinateCount === 1) {
            setDetailZoom()
        }
    }
}

async function toggleFavorite(site) {
    const changed = await store.toggleFavoriteWashSite(site.washSiteId)
    if (!changed) {
        return
    }

    if (favoritesOnly.value
            && !store.isFavoriteWashSite(site.washSiteId)
            && selectedSite.value?.washSiteId === site.washSiteId) {
        selectedSite.value = null
        infoWindow?.close()
    }
    refreshVisibleMarkers()
}

function refreshVisibleMarkers() {
    if (mapReady.value) {
        renderMarkers(true)
    }
}

function clearMarkers() {
    markerEntries.forEach(({ marker, listener }) => {
        if (listener) {
            removeMapListener(listener)
        }
        marker.setMap(null)
    })
    markerEntries = []
}

function selectSite(site) {
    selectedSite.value = site
    if (!mapReady.value || !map || !infoWindow || !isCoordinate(site.latitude, site.longitude)) {
        return
    }

    const maps = getActiveMaps()
    if (!maps) {
        return
    }
    const position = new maps.LatLng(site.latitude, site.longitude)
    map.panTo(position)
    setDetailZoom()

    markerEntries.forEach(({ marker, washSiteId }) => {
        marker.setZIndex(washSiteId === site.washSiteId ? 100 : 1)
    })

    infoWindow.setContent(`
        <div style="min-width:220px;padding:14px 16px;border:1px solid #cbd5e1;border-radius:6px;background:#fff;box-shadow:0 8px 24px rgba(15,23,42,.16);text-align:left;font-family:system-ui,sans-serif">
            <strong style="display:block;color:#0f172a;font-size:15px">${escapeHtml(site.siteName || "세차장")}</strong>
            <span style="display:block;margin-top:6px;color:#64748b;font-size:12px;line-height:1.45">${escapeHtml(site.address || "주소 미등록")}</span>
            <span style="display:block;margin-top:8px;color:#0f766e;font-size:13px;font-weight:700">${escapeHtml(site.courseName || "기본 코스")} · ${escapeHtml(formatAmount(site.washPrice))}</span>
        </div>
    `)
    const selectedMarker = markerEntries.find(entry => entry.washSiteId === site.washSiteId)?.marker
    if (activeProvider.value === "kakao" && selectedMarker) {
        infoWindow.open(map, selectedMarker)
    } else {
        infoWindow.open(map, position)
    }
}

async function searchAll() {
    await loadSites({ keyword: keyword.value || undefined })
}

async function searchCurrentArea() {
    if (!mapReady.value) {
        return
    }

    const bounds = map.getBounds()
    const southWest = activeProvider.value === "kakao"
        ? bounds.getSouthWest()
        : bounds.getSW()
    const northEast = activeProvider.value === "kakao"
        ? bounds.getNorthEast()
        : bounds.getNE()
    await loadSites({
        keyword: keyword.value || undefined,
        south: southWest.lat(),
        north: northEast.lat(),
        west: southWest.lng(),
        east: northEast.lng()
    }, false)

    displaySites.value = displaySites.value.filter(site => {
        if (!isCoordinate(site.latitude, site.longitude)) {
            return false
        }

        return Number(site.latitude) >= southWest.lat()
            && Number(site.latitude) <= northEast.lat()
            && Number(site.longitude) >= southWest.lng()
            && Number(site.longitude) <= northEast.lng()
    })
    renderMarkers(false)
}

function findNearbySites() {
    locationMessage.value = ""
    if (!navigator.geolocation) {
        locationMessage.value = "이 브라우저에서는 현재 위치를 사용할 수 없습니다."
        return
    }

    navigator.geolocation.getCurrentPosition(async position => {
        userLocation.value = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude
        }
        locationMessage.value = "현재 위치에서 가까운 순서로 표시합니다."
        showUserLocation()
        const nearbySites = await loadSites({
            keyword: keyword.value || undefined,
            latitude: userLocation.value.latitude,
            longitude: userLocation.value.longitude,
            radiusKm: 20
        })

        if (nearbySites.length === 0 && mapReady.value) {
            const allSites = await store.fetchMapWashSites({
                keyword: keyword.value || undefined
            })
            const resolvedSites = await resolveMissingCoordinates(allSites)

            displaySites.value = resolvedSites
                .filter(site => isCoordinate(site.latitude, site.longitude))
                .map(site => ({
                    ...site,
                    distanceKm: calculateDistanceKm(
                        userLocation.value.latitude,
                        userLocation.value.longitude,
                        Number(site.latitude),
                        Number(site.longitude)
                    )
                }))
                .filter(site => site.distanceKm <= 20)
                .sort((left, right) => left.distanceKm - right.distanceKm)
            renderMarkers(true)
        }
    }, () => {
        locationMessage.value = "위치 권한이 없어 전체 세차장을 표시합니다."
    }, {
        enableHighAccuracy: false,
        timeout: 8000,
        maximumAge: 300000
    })
}

function showUserLocation() {
    if (!mapReady.value || !map || !userLocation.value) {
        return
    }

    const maps = getActiveMaps()
    if (!maps) {
        return
    }
    const position = new maps.LatLng(
        userLocation.value.latitude,
        userLocation.value.longitude
    )
    userMarker?.setMap(null)
    userMarker = new maps.Marker({
        map,
        position,
        title: "현재 위치",
        zIndex: 200
    })
    map.panTo(position)
}

async function resetSearch() {
    keyword.value = ""
    userLocation.value = null
    locationMessage.value = ""
    userMarker?.setMap(null)
    userMarker = null
    await loadSites()
}

function openDirections(site) {
    if (activeProvider.value === "kakao") {
        const name = encodeURIComponent(site.siteName || "세차장")
        if (!isCoordinate(site.latitude, site.longitude)) {
            window.open(`https://map.kakao.com/link/search/${name}`, "_blank", "noopener,noreferrer")
            return
        }

        window.open(
            `https://map.kakao.com/link/to/${name},${site.latitude},${site.longitude}`,
            "_blank",
            "noopener,noreferrer"
        )
        return
    }

    if (!isCoordinate(site.latitude, site.longitude)) {
        window.open(`https://map.naver.com/p/search/${encodeURIComponent(site.siteName || site.address)}`, "_blank", "noopener,noreferrer")
        return
    }

    const destination = `${site.longitude},${site.latitude},${encodeURIComponent(site.siteName || "세차장")},PLACE_POI`
    window.open(`https://map.naver.com/p/directions/-/${destination}/-/car`, "_blank", "noopener,noreferrer")
}

function getActiveMaps() {
    return mapSdk || null
}

function addMapListener(target, eventName, listener) {
    const maps = getActiveMaps()
    if (!maps) {
        return null
    }
    if (activeProvider.value === "kakao") {
        maps.event.addListener(target, eventName, listener)
        return { target, eventName, listener }
    }
    return maps.Event.addListener(target, eventName, listener)
}

function removeMapListener(listener) {
    const maps = getActiveMaps()
    if (!maps) {
        return
    }
    if (activeProvider.value === "kakao") {
        maps.event.removeListener(listener.target, listener.eventName, listener.listener)
        return
    }
    maps.Event.removeListener(listener)
}

function setDetailZoom() {
    if (activeProvider.value === "kakao") {
        if (map.getLevel() > 4) {
            map.setLevel(4)
        }
        return
    }

    if (map.getZoom() < 14) {
        map.setZoom(14)
    }
}

function disposeMap() {
    if (mapReady.value) {
        clearMarkers()
        userMarker?.setMap(null)
        infoWindow?.close()
    }
    markerEntries = []
    userMarker = null
    infoWindow = null
    map = null
    mapSdk = null
    mapReady.value = false
}

function isCoordinate(latitude, longitude) {
    if (
        latitude === null
        || latitude === undefined
        || String(latitude).trim() === ""
        || longitude === null
        || longitude === undefined
        || String(longitude).trim() === ""
    ) {
        return false
    }

    const normalizedLatitude = Number(latitude)
    const normalizedLongitude = Number(longitude)

    return Number.isFinite(normalizedLatitude)
        && Number.isFinite(normalizedLongitude)
        && normalizedLatitude >= 33
        && normalizedLatitude <= 39.5
        && normalizedLongitude >= 124
        && normalizedLongitude <= 132
}

function calculateDistanceKm(fromLatitude, fromLongitude, toLatitude, toLongitude) {
    const earthRadiusKm = 6371.0088
    const latitudeDelta = toRadians(toLatitude - fromLatitude)
    const longitudeDelta = toRadians(toLongitude - fromLongitude)
    const startLatitude = toRadians(fromLatitude)
    const endLatitude = toRadians(toLatitude)
    const haversine = Math.sin(latitudeDelta / 2) ** 2
        + Math.cos(startLatitude)
        * Math.cos(endLatitude)
        * Math.sin(longitudeDelta / 2) ** 2

    return earthRadiusKm * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

function toRadians(value) {
    return value * Math.PI / 180
}

function formatAmount(value) {
    if (value === null || value === undefined) {
        return "가격 미설정"
    }
    return `${Number(value).toLocaleString("ko-KR")}원`
}

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;")
}
</script>

<style scoped>
.map-page {
    width: 100%;
    min-height: calc(100vh - 88px);
    color: #172033;
    background: #f4f7fb;
    box-sizing: border-box;
}

.map-header,
.search-toolbar,
.notice-text {
    max-width: 1280px;
    margin-inline: auto;
}

.map-header {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 24px;
    padding: 28px 28px 18px;
    text-align: left;
}

.map-header h1 {
    margin: 2px 0 7px;
    color: #0a1931;
    font-size: 30px;
    letter-spacing: 0;
}

.map-header p {
    color: #64748b;
    font-size: 14px;
}

.map-header .eyebrow {
    margin: 0 0 10px;
    color: #0f766e;
    font-size: 11px;
    font-weight: 900;
}

.map-header .eyebrow.naver {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 0 8px;
    border-radius: 4px;
    color: #ffffff;
    background: #03c75a;
}

.map-header .eyebrow.kakao {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 0 8px;
    border-radius: 4px;
    color: #191919;
    background: #fee500;
}

.header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
}

.provider-switch {
    display: inline-grid;
    grid-template-columns: repeat(2, minmax(92px, 1fr));
    overflow: hidden;
    border: 1px solid #b8c5d4;
    border-radius: 7px;
    background: #ffffff;
}

.provider-switch button {
    min-height: 42px;
    padding: 0 14px;
    border: 0;
    color: #64748b;
    background: transparent;
    font-size: 13px;
    font-weight: 800;
    cursor: pointer;
}

.provider-switch button + button {
    border-left: 1px solid #d7e0eb;
}

.provider-switch button:hover,
.provider-switch button:focus-visible {
    color: #0f172a;
    background: #eef2f6;
}

.provider-switch button.active {
    color: #ffffff;
    background: #172033;
}

.home-link,
.primary-button,
.secondary-button,
.selected-site button {
    min-height: 42px;
    padding: 0 18px;
    border-radius: 7px;
    font-size: 14px;
    font-weight: 800;
    cursor: pointer;
    box-sizing: border-box;
}

.home-link {
    display: inline-flex;
    align-items: center;
    border: 1px solid #cbd5e1;
    color: #334155;
    background: #ffffff;
    text-decoration: none;
}

.search-toolbar {
    display: grid;
    grid-template-columns: minmax(280px, 1fr) max-content max-content max-content;
    gap: 10px;
    align-items: end;
    padding: 0 28px 18px;
}

.search-field {
    display: grid;
    gap: 6px;
    color: #475569;
    font-size: 12px;
    font-weight: 800;
    text-align: left;
}

.search-field input {
    width: 100%;
    height: 42px;
    padding: 0 13px;
    border: 1px solid #cbd5e1;
    border-radius: 7px;
    color: #172033;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
}

.primary-button,
.selected-site button {
    border: 1px solid #0f766e;
    color: #ffffff;
    background: #0f766e;
}

.secondary-button {
    border: 1px solid #b8c5d4;
    color: #233a59;
    background: #ffffff;
}

.secondary-button:disabled {
    color: #94a3b8;
    background: #eef2f6;
    cursor: not-allowed;
}

.notice-text {
    padding: 0 28px 14px;
    color: #0f766e;
    font-size: 13px;
    font-weight: 700;
    text-align: left;
}

.map-workspace {
    display: grid;
    grid-template-columns: minmax(300px, 380px) minmax(0, 1fr);
    width: 100%;
    min-height: 620px;
    border-top: 1px solid #d7e0eb;
    border-bottom: 1px solid #d7e0eb;
    background: #ffffff;
}

.site-panel {
    display: flex;
    flex-direction: column;
    min-width: 0;
    max-height: 720px;
    border-right: 1px solid #d7e0eb;
    background: #ffffff;
}

.panel-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 62px;
    padding: 0 18px;
    border-bottom: 1px solid #e2e8f0;
}

.panel-heading > div:first-child {
    display: flex;
    align-items: baseline;
    gap: 8px;
}

.panel-controls {
    display: flex;
    align-items: center;
    gap: 10px;
}

.favorite-filter {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: #475569;
    font-size: 12px;
    font-weight: 800;
    cursor: pointer;
}

.favorite-filter input {
    width: 16px;
    height: 16px;
    margin: 0;
    accent-color: #0f766e;
}

.panel-heading strong {
    color: #0f172a;
    font-size: 16px;
}

.panel-heading span {
    color: #64748b;
    font-size: 12px;
}

.text-button {
    border: 0;
    color: #0f766e;
    background: transparent;
    font-weight: 800;
    cursor: pointer;
}

.site-list {
    overflow-y: auto;
}

.site-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) max-content;
    align-items: center;
    width: 100%;
    border-bottom: 1px solid #e7edf4;
    color: #334155;
    background: #ffffff;
}

.site-row:hover,
.site-row.selected {
    background: #eef7f5;
    box-shadow: inset 4px 0 #0f766e;
}

.site-row-main {
    display: grid;
    gap: 7px;
    min-width: 0;
    padding: 16px 12px 16px 18px;
    border: 0;
    color: inherit;
    background: transparent;
    text-align: left;
    cursor: pointer;
}

.site-row-main:focus-visible,
.site-directions-button:focus-visible {
    outline: 2px solid #0f766e;
    outline-offset: -3px;
}

.site-directions-button {
    min-width: 96px;
    min-height: 38px;
    margin: 0;
    padding: 0 10px;
    border: 1px solid #0f766e;
    border-radius: 6px;
    color: #0f766e;
    background: #ffffff;
    font-size: 12px;
    font-weight: 800;
    cursor: pointer;
}

.site-row-actions,
.selected-site-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}

.site-row-actions {
    margin-right: 14px;
}

.favorite-button {
    display: inline-grid;
    place-items: center;
    width: 38px;
    height: 38px;
    padding: 0;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    color: #64748b;
    background: #ffffff;
    font-size: 22px;
    line-height: 1;
    cursor: pointer;
}

.favorite-button:hover,
.favorite-button:focus-visible,
.favorite-button.active {
    border-color: #eab308;
    color: #ca8a04;
    background: #fffbeb;
}

.favorite-button:disabled {
    opacity: 0.55;
    cursor: wait;
}

.favorite-error {
    margin: 0;
    padding: 9px 18px;
    color: #991b1b;
    background: #fff5f5;
    font-size: 12px;
    font-weight: 700;
}

.site-directions-button:hover {
    color: #ffffff;
    background: #0f766e;
}

.site-directions-button.naver {
    border-color: #03c75a;
    color: #ffffff;
    background: #03c75a;
}

.site-directions-button.naver:hover {
    border-color: #02b351;
    color: #ffffff;
    background: #02b351;
}

.site-directions-button.kakao {
    border-color: #fee500;
    color: #191919;
    background: #fee500;
}

.site-directions-button.kakao:hover {
    border-color: #f4dc00;
    color: #191919;
    background: #f4dc00;
}

.site-row-heading,
.site-detail {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.site-row-heading strong {
    min-width: 0;
    overflow-wrap: anywhere;
    color: #0f172a;
    font-size: 15px;
}

.site-row-heading span {
    flex: 0 0 auto;
    color: #0f766e;
    font-size: 12px;
    font-weight: 800;
}

.site-address,
.site-detail {
    color: #64748b;
    font-size: 12px;
    line-height: 1.45;
}

.site-detail b {
    color: #172033;
}

.state-box {
    margin: 18px;
    padding: 18px;
    border: 1px solid #e2e8f0;
    border-radius: 7px;
    color: #64748b;
    background: #f8fafc;
    font-size: 13px;
}

.state-box.error,
.map-overlay.error {
    color: #991b1b;
    background: #fff5f5;
}

.map-stage {
    position: relative;
    min-width: 0;
    min-height: 620px;
    overflow: hidden;
}

.map-canvas {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    background: #e7edf4;
}

.map-overlay {
    position: absolute;
    inset: 0;
    z-index: 5;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 28px;
    color: #475569;
    background: #edf2f7;
    text-align: center;
    box-sizing: border-box;
}

.map-overlay small {
    color: #64748b;
}

.selected-site {
    position: absolute;
    right: 18px;
    bottom: 18px;
    left: 18px;
    z-index: 4;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding: 14px 16px;
    border: 1px solid #cbd5e1;
    border-radius: 7px;
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
    text-align: left;
}

.selected-site > div {
    display: grid;
    gap: 3px;
    min-width: 0;
}

.selected-site strong,
.selected-site span {
    overflow-wrap: anywhere;
}

.selected-site span {
    color: #64748b;
    font-size: 12px;
}

@media (max-width: 900px) {
    .search-toolbar {
        grid-template-columns: 1fr 1fr;
    }

    .search-field {
        grid-column: 1 / -1;
    }

    .map-workspace {
        grid-template-columns: 1fr;
    }

    .site-panel {
        max-height: 350px;
        border-right: 0;
        border-bottom: 1px solid #d7e0eb;
    }

    .map-stage {
        min-height: 520px;
    }
}

@media (max-width: 560px) {
    .map-header {
        flex-direction: column;
        align-items: flex-start;
        padding: 22px 16px 16px;
    }

    .header-actions {
        width: 100%;
    }

    .provider-switch {
        flex: 1 1 auto;
    }

    .map-header h1 {
        font-size: 25px;
    }

    .home-link {
        flex: 0 0 auto;
        padding-inline: 12px;
    }

    .search-toolbar {
        grid-template-columns: 1fr;
        padding: 0 16px 16px;
    }

    .notice-text {
        padding-inline: 16px;
    }

    .map-stage {
        min-height: 430px;
    }

    .site-row {
        grid-template-columns: minmax(0, 1fr);
    }

    .site-row-main {
        padding-bottom: 10px;
    }

    .site-directions-button {
        flex: 1 1 auto;
        width: auto;
    }

    .site-row-actions {
        margin: 0 16px 14px;
    }

    .selected-site {
        align-items: stretch;
        flex-direction: column;
    }
}
</style>
