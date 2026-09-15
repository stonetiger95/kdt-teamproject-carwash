
<template>
  <main class="home-page">
    <section class="hero">
      <section class="nearby-overview" aria-label="내 주변 세차장과 날씨">
        <div class="nearby-grid">
          <section class="mini-map-card" aria-label="내 주변 세차장 미니 지도">
            <div ref="miniMapElement" class="mini-map" />

            <div v-if="!mapReady" class="mini-map-overlay" role="status">
              <span class="map-loader" aria-hidden="true" />
              <p>{{ mapError || "지도를 불러오는 중입니다." }}</p>
            </div>

            <button
              class="location-refresh"
              type="button"
              :disabled="isLocating"
              @click="refreshNearbyOverview"
            >
              <span aria-hidden="true">⌖</span>
              {{ isLocating ? "위치 확인 중" : "현재 위치" }}
            </button>

            <footer class="mini-map-caption">
              <div>
                <strong>{{ locationSummary }}</strong>
                <span>{{ closestSiteSummary }}</span>
              </div>
              <div class="mini-map-actions">
                <span class="map-legend"><i /> 내 위치</span>
                <button
                  class="mini-map-directions-button"
                  type="button"
                  :disabled="!selectedSite || routeLoading"
                  @click="showSelectedSiteRoute"
                >
                  {{ routeLoading ? "경로 찾는 중" : "길찾기" }}
                </button>
                <RouterLink
                  class="mini-map-finder-link"
                  :to="{ name: 'public-wash-site-map' }"
                >
                  가까운 세차장 찾기
                  <span aria-hidden="true">→</span>
                </RouterLink>
              </div>
            </footer>
          </section>

          <article :class="['weather-card', `weather-${weatherVisualType}`]">
            <div class="weather-card-header">
              <div>
                <p class="nearby-kicker">TODAY</p>
                <h2>지금 내 주변 날씨</h2>
                <span>{{ weatherTimeLabel }}</span>
              </div>

              <div class="weather-animation" aria-hidden="true">
                <span class="weather-sun" />
                <span v-if="weatherVisualType !== 'sunny'" class="weather-cloud cloud-back" />
                <span v-if="weatherVisualType !== 'sunny'" class="weather-cloud cloud-front" />
                <span v-if="weatherVisualType === 'rain'" class="weather-rain">
                  <i /><i /><i />
                </span>
                <span v-if="weatherVisualType === 'snow'" class="weather-snow">
                  <i>✦</i><i>✦</i><i>✦</i>
                </span>
              </div>
            </div>

            <div v-if="weatherLoading" class="weather-state" role="status">
              날씨를 확인하는 중입니다.
            </div>

            <div v-else-if="weatherError" class="weather-state is-error" role="status">
              {{ weatherError }}
            </div>

            <template v-else>
              <div class="weather-primary">
                <strong>{{ currentTemperatureLabel }}</strong>
                <div>
                  <span>{{ weatherStatusText }}</span>
                  <small>{{ temperatureRangeLabel }}</small>
                </div>
              </div>

              <dl class="weather-details">
                <div>
                  <dt>강수확률</dt>
                  <dd>{{ precipitationProbability }}%</dd>
                </div>
                <div>
                  <dt>세차지수</dt>
                  <dd>{{ washCondition }}</dd>
                </div>
              </dl>
            </template>
          </article>
        </div>
      </section>

      <div class="access-grid">
        <section class="access-card member-access" aria-label="회원 로그인 메뉴">
          <LoginForm
            class="home-login-form"
            compact
            mode="home"
          />
        </section>

        <section class="access-card non-member" aria-label="비회원 이용 메뉴">
          <nav class="guest-reservation-actions" aria-label="비회원 예약 메뉴">
            <RouterLink
              class="guest-reservation-link primary"
              :to="{ name: 'non-member-reservation-create' }"
            >
              비회원 예약하기
            </RouterLink>
            <RouterLink
              class="guest-reservation-link"
              :to="{ name: 'non-member-reservation-lookup' }"
            >
              비회원 예약 조회
            </RouterLink>
          </nav>

          <section class="guest-wash-lookup" aria-label="비회원 세차 조회 및 결제">
            <div class="guest-wash-lookup-heading">
              <h3>비회원 세차·결제 조회</h3>
            </div>

            <NonMemberPlateLookup
              compact
              @selected="showGuestPayments"
              @cleared="clearGuestPayments"
            />

            <div
              v-if="displayedGuestWashHistories.length > 0"
              class="guest-results"
              aria-live="polite"
            >
              <article
                v-for="history in displayedGuestWashHistories"
                :key="history.washHistoryId"
                class="guest-payment"
              >
                <div class="guest-payment-info">
                  <strong>{{ history.plateNumber }}</strong>
                  <span>{{ history.siteName || "-" }}</span>
                </div>

                <dl class="guest-payment-details">
                  <div>
                    <dt>입차시간</dt>
                    <dd>{{ formatDateTime(history.entryTime) }}</dd>
                  </div>
                  <div>
                    <dt>세차코스</dt>
                    <dd>{{ history.courseName || "-" }}</dd>
                  </div>
                  <div>
                    <dt>결제금액</dt>
                    <dd>{{ formatAmount(history.amount) }}</dd>
                  </div>
                  <div>
                    <dt>결제상태</dt>
                    <dd>{{ formatPaymentStatus(history.paymentStatus) }}</dd>
                  </div>
                </dl>

                <button
                  v-if="isPaymentRequired(history.paymentStatus)"
                  class="payment-action"
                  type="button"
                  @click="goGuestPayment(history.washHistoryId)"
                >
                  결제하기
                </button>

                <button
                  type="button"
                  class="history-action"
                  @click="goGuestWashHistory(history)"
                >
                  전체 세차 내역 보기
                </button>
              </article>
            </div>
          </section>

        </section>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue"
import { RouterLink, useRouter } from "vue-router"
import LoginForm from "@/features/auth/components/LoginForm.vue"
import NonMemberPlateLookup from "@/features/washHistory/components/NonMemberPlateLookup.vue"
import { getWeather } from "@/features/weather/api/weatherApi.js"
import {
  getDrivingDirections,
  getMapWashSites
} from "@/features/washSite/api/washSiteApi.js"
import { loadNaverMapSdk } from "@/features/washSite/utils/naverMapLoader.js"

const router = useRouter()
const guestWashHistories = ref([])
const miniMapElement = ref(null)
const mapReady = ref(false)
const mapError = ref("")
const isLocating = ref(false)
const locationMessage = ref("현재 위치를 확인하고 있습니다.")
const userLocation = ref(null)
const nearbySites = ref([])
const selectedSiteId = ref(null)
const routeLoading = ref(false)
const routeMessage = ref("")
const weather = ref(null)
const weatherLoading = ref(true)
const weatherError = ref("")

const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.9780 }
let miniMap = null
let mapSdk = null
let userMarker = null
let siteMarkers = []
let siteMarkerListeners = []
let routePolyline = null

const locationSummary = computed(() => {
  if (isLocating.value) {
    return "현재 위치를 찾는 중"
  }

  if (userLocation.value) {
    return `반경 20km · 세차장 ${nearbySites.value.length}곳`
  }

  return "서울 중심 지도"
})

const closestSite = computed(() => nearbySites.value[0] || null)

const selectedSite = computed(() => {
  const selectedId = String(selectedSiteId.value ?? "")
  return nearbySites.value.find(site =>
    String(site.washSiteId ?? "") === selectedId
  ) || closestSite.value
})

const closestSiteSummary = computed(() => {
  if (routeMessage.value) {
    return routeMessage.value
  }

  const site = selectedSite.value
  if (!site) {
    return locationMessage.value
  }

  const hasDistance = site.distanceKm !== null
    && site.distanceKm !== undefined
    && String(site.distanceKm).trim() !== ""
  const distance = Number(site.distanceKm)
  const distanceLabel = hasDistance && Number.isFinite(distance)
    ? ` · ${distance.toFixed(distance < 10 ? 1 : 0)}km`
    : ""

  return `${site.siteName || "가까운 세차장"}${distanceLabel}`
})

const currentWeather = computed(() => weather.value?.current || null)
const todayForecast = computed(() => weather.value?.forecasts?.[0] || null)

const weatherVisualType = computed(() => {
  const precipitation = String(
    currentWeather.value?.precipitationType
      || todayForecast.value?.precipitationType
      || ""
  )
  const sky = String(currentWeather.value?.sky || todayForecast.value?.sky || "")

  if (precipitation.includes("눈")) {
    return "snow"
  }
  if (["비", "소나기"].some(value => precipitation.includes(value))) {
    return "rain"
  }
  if (sky.includes("구름") || sky.includes("흐림")) {
    return "cloudy"
  }
  return "sunny"
})

const weatherStatusText = computed(() => {
  const precipitation = currentWeather.value?.precipitationType
  if (precipitation && precipitation !== "없음") {
    return precipitation
  }
  return currentWeather.value?.sky || todayForecast.value?.sky || "날씨 정보 없음"
})

const precipitationProbability = computed(() =>
  currentWeather.value?.precipitationProbability
    ?? todayForecast.value?.precipitationProbability
    ?? 0
)

const washCondition = computed(() =>
  todayForecast.value?.washCondition || "예보 확인 필요"
)

const currentTemperatureLabel = computed(() => {
  const temperature = currentWeather.value?.temperature
  return temperature === null || temperature === undefined
    ? "--°"
    : `${temperature}°`
})

const temperatureRangeLabel = computed(() => {
  const minimum = todayForecast.value?.minTemperature
  const maximum = todayForecast.value?.maxTemperature
  if (minimum === null || minimum === undefined
    || maximum === null || maximum === undefined) {
    return "오늘 기온 정보 없음"
  }
  return `최저 ${minimum}° · 최고 ${maximum}°`
})

const weatherTimeLabel = computed(() => {
  const time = currentWeather.value?.forecastTime || weather.value?.baseTime
  const timeLabel = formatForecastTime(time)
  const location = userLocation.value ? "내 주변" : "서울"
  return timeLabel ? `${timeLabel} 예보 · ${location}` : `${location} 예보`
})

const displayedGuestWashHistories = computed(() => {
  const paymentRequired = guestWashHistories.value.filter(history =>
    isPaymentRequired(history.paymentStatus)
  )

  return paymentRequired.length > 0
    ? paymentRequired
    : guestWashHistories.value.slice(0, 1)
})

onMounted(async () => {
  try {
    await initializeMiniMap()
    await Promise.all([
      loadNearbySites(null),
      loadNearbyWeather(null)
    ])
    refreshNearbyOverview()
  } catch {
    mapReady.value = false
    mapError.value = "미니 지도를 표시하지 못했습니다."
    isLocating.value = false
  }
})

onBeforeUnmount(() => {
  clearDrivingRoute()
  clearMapMarkers()
  miniMap = null
  mapSdk = null
})

async function initializeMiniMap() {
  mapReady.value = false
  mapError.value = ""

  try {
    mapSdk = await loadNaverMapSdk()
    await nextTick()
    miniMap = new mapSdk.Map(miniMapElement.value, {
      center: new mapSdk.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude),
      zoom: 12,
      zoomControl: false,
      mapDataControl: false,
      scaleControl: false,
      logoControl: false
    })
    mapReady.value = true
  } catch (error) {
    mapError.value = error?.message || "네이버 지도를 불러오지 못했습니다."
  }
}

async function refreshNearbyOverview() {
  if (isLocating.value) {
    return
  }

  isLocating.value = true
  locationMessage.value = "현재 위치를 확인하고 있습니다."
  let coordinates = null

  try {
    coordinates = await getCurrentCoordinates()
    userLocation.value = coordinates
    locationMessage.value = "현재 위치에서 가까운 순서입니다."
  } catch (error) {
    userLocation.value = null
    locationMessage.value = getLocationErrorMessage(error)
  }

  isLocating.value = false
  if (!coordinates && weather.value && nearbySites.value.length > 0) {
    return
  }

  await Promise.all([
    loadNearbySites(coordinates),
    loadNearbyWeather(coordinates)
  ])
}

function getCurrentCoordinates() {
  if (!navigator.geolocation) {
    return Promise.reject(new Error("unsupported"))
  }

  return new Promise((resolve, reject) => {
    let settled = false
    const timeoutId = window.setTimeout(() => {
      if (!settled) {
        settled = true
        reject({ code: 3 })
      }
    }, 9000)

    navigator.geolocation.getCurrentPosition(
      position => {
        if (settled) return
        settled = true
        window.clearTimeout(timeoutId)
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude
        })
      },
      error => {
        if (settled) return
        settled = true
        window.clearTimeout(timeoutId)
        reject(error)
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 300000
      }
    )
  })
}

function getLocationErrorMessage(error) {
  if (error?.code === 1) {
    return "위치 권한을 허용하면 주변 세차장을 표시합니다."
  }
  if (error?.code === 2) {
    return "현재 위치 신호를 확인할 수 없습니다."
  }
  if (error?.code === 3) {
    return "위치 확인 시간이 초과되었습니다."
  }
  return "현재 위치 버튼을 눌러 주변을 확인하세요."
}

async function loadNearbySites(coordinates) {
  try {
    const params = coordinates
      ? {
          latitude: coordinates.latitude,
          longitude: coordinates.longitude,
          radiusKm: 20
        }
      : {}
    let sites = await getMapWashSites(params)

    if (coordinates && (!Array.isArray(sites) || sites.length === 0)) {
      sites = await getMapWashSites()
    }

    nearbySites.value = normalizeNearbySites(sites, coordinates).slice(0, 5)

    const selectedSiteStillExists = nearbySites.value.some(site =>
      String(site.washSiteId ?? "") === String(selectedSiteId.value ?? "")
    )
    if (!selectedSiteStillExists) {
      selectedSiteId.value = nearbySites.value[0]?.washSiteId ?? null
    }
    clearDrivingRoute()
  } catch {
    nearbySites.value = []
    selectedSiteId.value = null
    clearDrivingRoute()
    locationMessage.value = "세차장 정보를 불러오지 못했습니다."
  }

  renderMiniMapMarkers()
}

function normalizeNearbySites(sites, coordinates) {
  const normalized = (Array.isArray(sites) ? sites : [])
    .filter(site => isCoordinate(site.latitude, site.longitude))
    .map(site => {
      if (!coordinates || Number.isFinite(Number(site.distanceKm))) {
        return site
      }
      return {
        ...site,
        distanceKm: calculateDistanceKm(
          coordinates.latitude,
          coordinates.longitude,
          Number(site.latitude),
          Number(site.longitude)
        )
      }
    })

  if (coordinates) {
    normalized.sort((left, right) =>
      Number(left.distanceKm ?? Number.POSITIVE_INFINITY)
      - Number(right.distanceKm ?? Number.POSITIVE_INFINITY)
    )
  }
  return normalized
}

function renderMiniMapMarkers() {
  try {
    clearMapMarkers()
    if (!mapReady.value || !miniMap || !mapSdk) {
      return
    }

    const bounds = new mapSdk.LatLngBounds()
    let markerCount = 0

    if (userLocation.value) {
      const position = new mapSdk.LatLng(
        userLocation.value.latitude,
        userLocation.value.longitude
      )
      userMarker = new mapSdk.Marker({
        map: miniMap,
        position,
        title: "현재 위치",
        zIndex: 100,
        icon: {
          content: '<div style="width:18px;height:18px;border:4px solid white;border-radius:50%;background:#2563eb;box-shadow:0 2px 8px rgba(37,99,235,.45)"></div>',
          anchor: new mapSdk.Point(13, 13)
        }
      })
      bounds.extend(position)
      markerCount += 1
    }

    nearbySites.value.slice(0, 5).forEach(site => {
      const position = new mapSdk.LatLng(site.latitude, site.longitude)
      const isSelected = String(site.washSiteId ?? "")
        === String(selectedSite.value?.washSiteId ?? "")
      const marker = new mapSdk.Marker({
        map: miniMap,
        position,
        title: `${site.siteName || "세차장"} - 클릭하여 선택`,
        zIndex: isSelected ? 80 : 20,
        icon: createWashSiteMarkerIcon(isSelected)
      })
      siteMarkerListeners.push(
        mapSdk.Event.addListener(marker, "click", () => selectNearbySite(site))
      )
      siteMarkers.push(marker)
      bounds.extend(position)
      markerCount += 1
    })

    if (markerCount > 1) {
      miniMap.fitBounds(bounds, { top: 34, right: 34, bottom: 34, left: 34 })
    } else if (userLocation.value) {
      miniMap.setCenter(new mapSdk.LatLng(
        userLocation.value.latitude,
        userLocation.value.longitude
      ))
      miniMap.setZoom(14)
    } else if (nearbySites.value[0]) {
      miniMap.setCenter(new mapSdk.LatLng(
        nearbySites.value[0].latitude,
        nearbySites.value[0].longitude
      ))
      miniMap.setZoom(13)
    }
  } catch {
    clearMapMarkers()
    mapReady.value = false
    mapError.value = "미니 지도를 표시하지 못했습니다. 지도 설정을 확인해 주세요."
  }
}

function clearMapMarkers() {
  if (mapSdk?.Event) {
    siteMarkerListeners.forEach(listener => mapSdk.Event.removeListener(listener))
  }
  siteMarkerListeners = []
  userMarker?.setMap(null)
  userMarker = null
  siteMarkers.forEach(marker => marker.setMap(null))
  siteMarkers = []
}

function createWashSiteMarkerIcon(isSelected) {
  const color = isSelected ? "#0f766e" : "#3b82f6"
  const label = isSelected ? "선택" : "세차"
  return {
    content: [
      '<div style="position:relative;width:34px;height:42px;cursor:pointer">',
      `<div style="display:flex;align-items:center;justify-content:center;width:32px;height:32px;border:3px solid white;border-radius:50%;color:white;background:${color};box-shadow:0 3px 9px rgba(15,51,82,.3);font-size:9px;font-weight:900">${label}</div>`,
      `<div style="position:absolute;left:11px;top:27px;width:10px;height:10px;background:${color};transform:rotate(45deg);z-index:-1"></div>`,
      '</div>'
    ].join(""),
    anchor: new mapSdk.Point(17, 39)
  }
}

function selectNearbySite(site) {
  if (!site) {
    return
  }

  selectedSiteId.value = site.washSiteId
  routeMessage.value = `${site.siteName || "세차장"}을 선택했습니다.`
  clearDrivingRoute({ keepMessage: true })
  renderMiniMapMarkers()

  if (miniMap && mapSdk && isCoordinate(site.latitude, site.longitude)) {
    miniMap.panTo(new mapSdk.LatLng(site.latitude, site.longitude))
  }
}

async function showSelectedSiteRoute() {
  routeMessage.value = ""
  if (!userLocation.value) {
    await refreshNearbyOverview()
  }

  const site = selectedSite.value
  if (!userLocation.value) {
    routeMessage.value = "현재 위치 권한을 허용한 뒤 길찾기를 이용해 주세요."
    return
  }
  if (!site || !isCoordinate(site.latitude, site.longitude)) {
    routeMessage.value = "길찾기를 이용할 세차장을 지도에서 선택해 주세요."
    return
  }
  if (!site.washSiteId) {
    routeMessage.value = "선택한 세차장 정보를 확인할 수 없습니다."
    return
  }

  routeLoading.value = true
  clearDrivingRoute()

  try {
    const route = await getDrivingDirections({
      startLatitude: userLocation.value.latitude,
      startLongitude: userLocation.value.longitude,
      washSiteId: site.washSiteId
    })
    drawDrivingRoute(route)
    routeMessage.value = formatRouteSummary(site, route)
  } catch (error) {
    routeMessage.value = getDirectionsErrorMessage(error)
  } finally {
    routeLoading.value = false
  }
}

function drawDrivingRoute(route) {
  if (!miniMap || !mapSdk || typeof mapSdk.Polyline !== "function") {
    throw new Error("미니 지도에 경로를 표시할 수 없습니다.")
  }

  const path = (Array.isArray(route?.path) ? route.path : [])
    .filter(point => isCoordinate(point?.latitude, point?.longitude))
    .map(point => new mapSdk.LatLng(point.latitude, point.longitude))
  if (path.length < 2) {
    throw new Error("표시할 차량 경로가 없습니다.")
  }

  routePolyline = new mapSdk.Polyline({
    map: miniMap,
    path,
    strokeColor: "#2563eb",
    strokeOpacity: 0.9,
    strokeWeight: 6,
    strokeLineCap: "round",
    strokeLineJoin: "round"
  })

  const bounds = new mapSdk.LatLngBounds()
  path.forEach(point => bounds.extend(point))
  miniMap.fitBounds(bounds, { top: 38, right: 38, bottom: 38, left: 38 })
}

function clearDrivingRoute({ keepMessage = false } = {}) {
  routePolyline?.setMap(null)
  routePolyline = null
  if (!keepMessage) {
    routeMessage.value = ""
  }
}

function formatRouteSummary(site, route) {
  const distanceMeters = Number(route?.distanceMeters)
  const durationMillis = Number(route?.durationMillis)
  const distance = Number.isFinite(distanceMeters)
    ? distanceMeters < 1000
      ? `${Math.round(distanceMeters)}m`
      : `${(distanceMeters / 1000).toFixed(1)}km`
    : "거리 확인 중"
  const minutes = Number.isFinite(durationMillis)
    ? Math.max(1, Math.round(durationMillis / 60000))
    : null
  return `${site.siteName || "선택한 세차장"} · 차량 ${distance}${minutes ? ` · 약 ${minutes}분` : ""}`
}

function getDirectionsErrorMessage(error) {
  const body = error?.response?.data
  return body?.detail
    || body?.message
    || error?.message
    || "길찾기 정보를 불러오지 못했습니다."
}

async function loadNearbyWeather(coordinates) {
  weatherLoading.value = true
  weatherError.value = ""

  try {
    const grid = coordinates
      ? toKmaGrid(coordinates.latitude, coordinates.longitude)
      : { nx: 60, ny: 127 }
    weather.value = await getWeather({
      location: coordinates ? "내 주변" : "서울",
      nx: grid.nx,
      ny: grid.ny
    })
  } catch {
    weather.value = null
    weatherError.value = "현재 날씨를 불러오지 못했습니다."
  } finally {
    weatherLoading.value = false
  }
}

function toKmaGrid(latitude, longitude) {
  const earthRadius = 6371.00877
  const gridSpacing = 5.0
  const standardLatitude1 = 30.0
  const standardLatitude2 = 60.0
  const originLongitude = 126.0
  const originLatitude = 38.0
  const originX = 43
  const originY = 136
  const degreesToRadians = Math.PI / 180.0
  const re = earthRadius / gridSpacing
  const slat1 = standardLatitude1 * degreesToRadians
  const slat2 = standardLatitude2 * degreesToRadians
  const olon = originLongitude * degreesToRadians
  const olat = originLatitude * degreesToRadians
  let sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5)
    / Math.tan(Math.PI * 0.25 + slat1 * 0.5)
  sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn)
  let sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5)
  sf = (Math.pow(sf, sn) * Math.cos(slat1)) / sn
  let ro = Math.tan(Math.PI * 0.25 + olat * 0.5)
  ro = (re * sf) / Math.pow(ro, sn)
  let ra = Math.tan(Math.PI * 0.25 + latitude * degreesToRadians * 0.5)
  ra = (re * sf) / Math.pow(ra, sn)
  let theta = longitude * degreesToRadians - olon
  if (theta > Math.PI) theta -= 2.0 * Math.PI
  if (theta < -Math.PI) theta += 2.0 * Math.PI
  theta *= sn

  return {
    nx: Math.floor(ra * Math.sin(theta) + originX + 0.5),
    ny: Math.floor(ro - ra * Math.cos(theta) + originY + 0.5)
  }
}

function isCoordinate(latitude, longitude) {
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
  const toRadians = value => value * Math.PI / 180
  const latitudeDelta = toRadians(toLatitude - fromLatitude)
  const longitudeDelta = toRadians(toLongitude - fromLongitude)
  const startLatitude = toRadians(fromLatitude)
  const endLatitude = toRadians(toLatitude)
  const haversine = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(startLatitude)
    * Math.cos(endLatitude)
    * Math.sin(longitudeDelta / 2) ** 2
  return earthRadiusKm * 2 * Math.atan2(
    Math.sqrt(haversine),
    Math.sqrt(1 - haversine)
  )
}

function formatForecastTime(value) {
  if (value === null || value === undefined || String(value).trim() === "") {
    return ""
  }
  const normalized = String(value || "").padStart(4, "0")
  return /^\d{4}$/.test(normalized)
    ? `${normalized.slice(0, 2)}:${normalized.slice(2)}`
    : ""
}

function showGuestPayments({ washHistories }) {
  guestWashHistories.value = Array.isArray(washHistories)
    ? washHistories
    : []
}

function clearGuestPayments() {
  guestWashHistories.value = []
}

function goGuestPayment(washHistoryId) {
  router.push({
    name: "non-member-payment-checkout",
    params: {
      washHistoryId: String(washHistoryId)
    }
  })
}

function goGuestWashHistory(history) {
  const plateNumber = String(history.plateNumber || "")

  router.push({
    name: "non-member-wash-history",
    query: {
      plateLast4: plateNumber.match(/(\d{4})$/)?.[1] || "",
      plateNumber
    }
  })
}

function formatAmount(amount) {
  if (amount === null || amount === undefined) {
    return "-"
  }

  return `${Number(amount).toLocaleString("ko-KR")}원`
}

function formatDateTime(value) {
  if (!value) {
    return "-"
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  })
}

function isPaymentRequired(paymentStatus) {
  return !["SUCCESS", "DONE", "CANCELED"].includes(
    String(paymentStatus || "").toUpperCase()
  )
}

function formatPaymentStatus(paymentStatus) {
  const status = String(paymentStatus || "").toUpperCase()
  const labels = {
    READY: "결제대기",
    WAITING: "결제대기",
    CANCELED: "결제취소",
    FAILED: "결제실패",
    SUCCESS: "결제완료",
    DONE: "결제완료"
  }

  return labels[status] || status || "-"
}
</script>

<style scoped>
.home-page {
  position: relative;
  min-height: max(100svh, var(--app-shell-height, 640px));
  background: #f4f7fb;
  color: #071b3a;
  font-family: "Jua", "Apple SD Gothic Neo", "맑은 고딕", sans-serif;
}

.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  min-height: max(100svh, var(--app-shell-height, 640px));
  padding: clamp(32px, 6vh, 64px) clamp(20px, 4vw, 64px) 64px;
  box-sizing: border-box;
}

.nearby-overview {
  width: min(100%, 680px);
  margin: 0 0 16px;
}

.nearby-kicker {
  margin: 0 0 3px;
  color: #557296;
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.12em;
}

.nearby-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(210px, 0.8fr);
  gap: 12px;
}

.mini-map-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  border: 1px solid #d6e3f0;
  border-radius: 12px;
  background: #e9f2f8;
}

.mini-map {
  width: 100%;
  height: 176px;
  background:
    linear-gradient(135deg, rgba(71, 134, 180, 0.07), rgba(15, 118, 110, 0.08)),
    #edf4f8;
}

.mini-map-overlay {
  position: absolute;
  inset: 0 0 53px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px;
  color: #486377;
  background: rgba(239, 246, 250, 0.94);
  text-align: center;
}

.mini-map-overlay p {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
}

.map-loader {
  width: 20px;
  height: 20px;
  border: 3px solid rgba(15, 118, 110, 0.2);
  border-top-color: #0f766e;
  border-radius: 50%;
  animation: map-spin 0.8s linear infinite;
}

.location-refresh {
  position: absolute;
  top: 10px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(128, 153, 175, 0.55);
  border-radius: 999px;
  color: #174052;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 5px 14px rgba(25, 59, 91, 0.15);
  font: inherit;
  font-size: 11px;
  font-weight: 900;
  cursor: pointer;
  z-index: 2;
}

.location-refresh:disabled {
  cursor: wait;
  opacity: 0.72;
}

.location-refresh:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.24);
  outline-offset: 2px;
}

.mini-map-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 53px;
  padding: 8px 11px;
  background: #ffffff;
  box-sizing: border-box;
}

.mini-map-caption > div:first-child {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.mini-map-caption strong,
.mini-map-caption span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-map-caption strong {
  color: #173452;
  font-size: 11px;
}

.mini-map-caption span {
  color: #6a7f94;
  font-size: 10px;
}

.map-legend {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 5px;
}

.map-legend i {
  width: 8px;
  height: 8px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 1px #2563eb;
}

.mini-map-actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
}

.mini-map-directions-button,
.mini-map-finder-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid #0f766e;
  border-radius: 999px;
  color: #0f766e;
  background: #ffffff;
  font-size: 10px;
  font-weight: 900;
  white-space: nowrap;
  text-decoration: none;
  transition: color 0.18s ease, background 0.18s ease;
}

.mini-map-directions-button {
  border-color: #2563eb;
  color: #2563eb;
  font-family: inherit;
  cursor: pointer;
}

.mini-map-directions-button:hover:not(:disabled) {
  color: #ffffff;
  background: #2563eb;
}

.mini-map-directions-button:disabled {
  border-color: #c5d1df;
  color: #91a0b1;
  cursor: not-allowed;
  background: #f7f9fb;
}

.mini-map-finder-link span {
  color: inherit;
  font-size: 11px;
}

.mini-map-finder-link:hover {
  color: #ffffff;
  background: #0f766e;
}

.mini-map-directions-button:focus-visible,
.mini-map-finder-link:focus-visible {
  outline: 3px solid rgba(15, 118, 110, 0.22);
  outline-offset: 2px;
}

.weather-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  padding: 15px;
  border: 1px solid #d7e4f3;
  border-radius: 12px;
  color: #123252;
  background: linear-gradient(145deg, #edf7ff, #f8fbff 64%);
  box-sizing: border-box;
}

.weather-card::after {
  content: "";
  position: absolute;
  right: -38px;
  bottom: -54px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: rgba(89, 153, 229, 0.08);
  pointer-events: none;
}

.weather-rain,
.weather-snow {
  background: linear-gradient(145deg, #edf4ff, #f5fbff 64%);
}

.weather-cloudy {
  background: linear-gradient(145deg, #eef3f8, #f9fbfd 64%);
}

.weather-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.weather-card-header h2 {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
}

.weather-card-header > div:first-child > span {
  display: block;
  margin-top: 4px;
  color: #698198;
  font-size: 9px;
}

.weather-animation {
  position: relative;
  flex: 0 0 68px;
  width: 68px;
  height: 58px;
}

.weather-sun {
  position: absolute;
  top: 4px;
  right: 9px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #ffc84d;
  box-shadow: 0 0 0 8px rgba(255, 200, 77, 0.16);
  animation: sun-pulse 3s ease-in-out infinite;
}

.weather-cloud {
  position: absolute;
  display: block;
  height: 17px;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 5px 13px rgba(67, 99, 133, 0.18);
  animation: cloud-float 4s ease-in-out infinite;
}

.weather-cloud::before,
.weather-cloud::after {
  content: "";
  position: absolute;
  bottom: 5px;
  border-radius: 50%;
  background: inherit;
}

.weather-cloud::before {
  left: 9px;
  width: 20px;
  height: 20px;
}

.weather-cloud::after {
  right: 8px;
  width: 15px;
  height: 15px;
}

.cloud-back {
  top: 19px;
  left: 4px;
  width: 48px;
  opacity: 0.75;
}

.cloud-front {
  top: 29px;
  right: 0;
  width: 54px;
  animation-delay: -1.5s;
}

.weather-animation .weather-rain,
.weather-animation .weather-snow {
  position: absolute;
  top: 44px;
  right: 5px;
  display: flex;
  gap: 8px;
  width: 45px;
  background: transparent;
}

.weather-rain i {
  width: 2px;
  height: 8px;
  border-radius: 999px;
  background: #4c8fe7;
  transform: rotate(15deg);
  animation: rain-fall 0.9s ease-in infinite;
}

.weather-rain i:nth-child(2),
.weather-snow i:nth-child(2) {
  animation-delay: -0.35s;
}

.weather-rain i:nth-child(3),
.weather-snow i:nth-child(3) {
  animation-delay: -0.65s;
}

.weather-snow i {
  color: #79aef0;
  font-size: 10px;
  font-style: normal;
  animation: snow-fall 1.8s ease-in-out infinite;
}

.weather-primary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.weather-primary > strong {
  color: #0b3260;
  font-size: 34px;
  line-height: 1;
}

.weather-primary > div {
  display: grid;
  gap: 3px;
}

.weather-primary span {
  font-size: 12px;
  font-weight: 900;
}

.weather-primary small {
  color: #72869a;
  font-size: 9px;
}

.weather-details {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 6px;
  margin: 15px 0 0;
  padding-top: 10px;
  border-top: 1px solid rgba(147, 177, 208, 0.35);
}

.weather-details > div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.weather-details dt,
.weather-details dd {
  margin: 0;
  font-size: 10px;
}

.weather-details dt {
  color: #71879c;
}

.weather-details dd {
  color: #163c62;
  font-weight: 900;
  text-align: right;
}

.weather-state {
  display: flex;
  align-items: center;
  min-height: 96px;
  color: #667f96;
  font-size: 11px;
  line-height: 1.5;
}

.weather-state.is-error {
  color: #a04747;
}

@keyframes map-spin {
  to { transform: rotate(360deg); }
}

@keyframes sun-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.08); }
}

@keyframes cloud-float {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(4px); }
}

@keyframes rain-fall {
  0% { opacity: 0; transform: translateY(-3px) rotate(15deg); }
  50% { opacity: 1; }
  100% { opacity: 0; transform: translateY(6px) rotate(15deg); }
}

@keyframes snow-fall {
  0%, 100% { opacity: 0.45; transform: translateY(-2px) rotate(0); }
  50% { opacity: 1; transform: translateY(5px) rotate(90deg); }
}

.access-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 14px;
  width: min(100%, 680px);
  margin: 0;
}

.access-card {
  min-width: 0;
  padding: 20px 18px 18px;
  border: 1px solid rgba(188, 207, 231, 0.88);
  border-top: 3px solid #5f91dc;
  border-radius: 12px;
  background: rgba(251, 253, 255, 0.92);
  box-shadow: 0 18px 38px rgba(45, 84, 139, 0.12);
  backdrop-filter: blur(10px);
  box-sizing: border-box;
}

.member-access,
.non-member {
  display: flex;
  flex-direction: column;
}

.non-member {
  border-top-color: #43aeba;
  padding: 20px 18px 18px;
  background: rgba(248, 252, 252, 0.93);
}

.access-heading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  max-width: 390px;
  margin: 0 auto;
  text-align: center;
}

.access-kicker {
  margin: 0;
  color: #557296;
  font-size: 11px;
  font-weight: 900;
}

.access-heading h2 {
  margin: 0;
  color: #071b3a;
  font-size: 20px;
  line-height: 1.35;
}

.home-login-form {
  width: 100%;
  gap: 10px;
  margin: 16px auto 0;
}

.home-login-form :deep(.social-login),
.home-login-form :deep(.form-actions) {
  width: 100%;
}

.home-login-form :deep(.password-help),
.home-login-form :deep(.error-message) {
  width: 100%;
}

.home-login-form :deep(label) {
  gap: 5px;
  font-size: 13px;
}

.home-login-form :deep(input),
.home-login-form :deep(.primary-button),
.home-login-form :deep(.secondary-button) {
  min-height: 42px;
}

.home-login-form :deep(.password-help) {
  padding-top: 10px;
}

.guest-reservation-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  width: min(100%, 360px);
  margin: 20px auto 0;
}

.guest-reservation-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #8ca6b5;
  border-radius: 8px;
  color: #174052;
  background: #ffffff;
  font-size: 13px;
  font-weight: 900;
  text-align: center;
  text-decoration: none;
}

.guest-reservation-link.primary {
  border-color: #0f766e;
  color: #ffffff;
  background: #0f766e;
}

.guest-reservation-link:hover {
  background: #edf7f7;
}

.guest-reservation-link.primary:hover {
  border-color: #0b5f59;
  background: #0b5f59;
}

.guest-reservation-link:focus-visible {
  outline: 3px solid rgba(15, 118, 110, 0.24);
  outline-offset: 2px;
}

.guest-wash-lookup {
  width: min(100%, 360px);
  margin: 24px auto 0;
  padding-top: 22px;
  border-top: 1px solid #d8e5e7;
}

.guest-wash-lookup-heading {
  text-align: center;
}

.guest-wash-lookup-heading h3 {
  margin: 0;
  color: #071b3a;
  font-size: 18px;
}

.guest-wash-lookup-heading .access-kicker {
  margin-top: 7px;
}

.non-member :deep(.plate-lookup.compact) {
  width: 100%;
  margin-top: 16px;
}

.non-member :deep(.plate-lookup label) {
  margin-bottom: 6px;
  font-size: 13px;
}

.guest-results {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.guest-payment {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #d8e3e1;
  border-radius: 8px;
  background: #ffffff;
}

.guest-payment-info {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #52677c;
  font-size: 12px;
}

.guest-payment-info strong {
  color: #071b3a;
  font-size: 16px;
}

.guest-payment-details {
  display: grid;
  gap: 7px;
  margin: 0;
}

.guest-payment-details > div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px;
}

.guest-payment-details dt,
.guest-payment-details dd {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
}

.guest-payment-details dt {
  color: #708097;
}

.guest-payment-details dd {
  color: #233a59;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.guest-payment button {
  min-height: 40px;
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.payment-action {
  color: #ffffff;
  background: #0f766e;
  border: 1px solid #0f766e;
}

.payment-action:hover {
  background: #0b5f59;
  border-color: #0b5f59;
}

.history-action {
  color: #233a59;
  background: #ffffff;
  border: 1px solid #bdc9d8;
}

.history-action:hover {
  background: #edf3f7;
  border-color: #8ea0b6;
}

@media (max-width: 1100px) {
  .hero {
    min-height: auto;
    padding: 40px 20px 56px;
  }
}

@media (max-width: 820px) {
  .nearby-overview {
    width: min(100%, 520px);
  }

  .nearby-grid {
    grid-template-columns: 1fr;
  }

  .weather-card {
    min-height: 190px;
  }

  .access-grid {
    grid-template-columns: 1fr;
    width: min(100%, 520px);
  }

  .non-member {
    padding: 24px;
  }

}

@media (max-width: 520px) {
  .hero {
    padding: 28px 16px 48px;
  }

  .access-card {
    padding: 22px 18px 20px;
  }

  .mini-map {
    height: 165px;
  }

  .mini-map-caption {
    align-items: flex-start;
    flex-direction: column;
  }

  .mini-map-actions {
    justify-content: flex-end;
    width: 100%;
  }

  .home-login-form :deep(.credential-login),
  .home-login-form :deep(.form-actions),
  .home-login-form :deep(.password-help),
  .home-login-form :deep(.error-message),
  .guest-reservation-actions,
  .guest-wash-lookup {
    width: 100%;
  }

}

@media (prefers-reduced-motion: reduce) {
  .map-loader,
  .weather-sun,
  .weather-cloud,
  .weather-rain i,
  .weather-snow i {
    animation: none;
  }
}
</style>
