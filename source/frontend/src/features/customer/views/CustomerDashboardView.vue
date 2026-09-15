<template>
    <main class="customer-dashboard">
        <h1 class="sr-only">고객 대시보드</h1>

        <section
            class="weather-panel panel-card"
            aria-labelledby="weather-title"
        >
            <header class="panel-header">
                <div>
                    <p class="panel-kicker">WEATHER</p>
                    <h2 id="weather-title">
                        {{ weather?.location || "서울" }} 날씨
                    </h2>
                </div>
                <div class="weather-header-actions">
                    <label class="weather-location-field">
                        <span>지역</span>
                        <select
                            v-model="selectedWeatherLocation"
                            :disabled="weatherLoading"
                            aria-label="날씨 지역 선택"
                            @change="changeWeatherLocation"
                        >
                            <option
                                v-for="location in weatherLocations"
                                :key="location.value"
                                :value="location.value"
                            >
                                {{ location.label }}
                            </option>
                        </select>
                    </label>
                    <span class="weather-guide">
                        세차 전 3일 예보를 확인하세요
                    </span>
                </div>
            </header>

            <p v-if="weatherLoading" class="panel-state weather-state">
                날씨 정보를 불러오는 중입니다.
            </p>
            <p
                v-else-if="weatherError"
                class="panel-state weather-state error"
            >
                {{ weatherError }}
            </p>
            <p
                v-else-if="weatherForecasts.length === 0"
                class="panel-state weather-state"
            >
                표시할 날씨 정보가 없습니다.
            </p>

            <div v-else class="weather-grid">
                <article
                    v-for="forecast in weatherForecasts"
                    :key="forecast.date"
                    class="weather-card"
                >
                    <header>
                        <strong>{{ weatherDayLabel(forecast.date) }}</strong>
                        <time :datetime="forecast.date">
                            {{ formatWeatherDate(forecast.date) }}
                        </time>
                    </header>

                    <div class="weather-summary">
                        <span class="weather-icon" aria-hidden="true">
                            {{ weatherIcon(forecast) }}
                        </span>
                        <div>
                            <strong class="weather-sky">
                                {{ forecast.sky }}
                            </strong>
                            <p class="weather-temperature">
                                <span>{{ forecast.minTemperature }}℃</span>
                                <b>{{ forecast.maxTemperature }}℃</b>
                            </p>
                        </div>
                    </div>

                    <dl class="weather-details">
                        <div>
                            <dt>강수확률</dt>
                            <dd>{{ forecast.precipitationProbability }}%</dd>
                        </div>
                        <div>
                            <dt>강수형태</dt>
                            <dd>{{ forecast.precipitationType }}</dd>
                        </div>
                    </dl>

                    <p class="wash-condition">
                        {{ forecast.washCondition }}
                    </p>
                </article>
            </div>
        </section>

        <section class="dashboard-overview">
            <aside class="account-summary panel-card" aria-label="고객 결제 요약">
                <section class="summary-section wallet-section">
                    <span class="summary-label">선불 잔액</span>
                    <strong>{{ formatAmount(walletBalance) }}</strong>
                    <small v-if="paymentStore.wallet?.updatedAt">
                        최근 반영 {{ formatDateTime(paymentStore.wallet.updatedAt) }}
                    </small>
                    <small v-else>아직 충전 내역이 없습니다.</small>

                    <div
                        v-if="hasBenefits"
                        class="wallet-benefits"
                    >
                        <div class="benefit-summary-item">
                            <small>세차장별 마일리지 합계</small>
                            <b>{{ formatNumber(totalMileageBalance) }}점</b>
                        </div>
                        <div class="benefit-summary-item">
                            <small>사용 가능 쿠폰</small>
                            <b>{{ formatNumber(totalAvailableCouponCount) }}개</b>
                        </div>
                        <RouterLink
                            class="benefit-summary-link"
                            :to="{ name: 'customer-benefits' }"
                            aria-label="마일리지와 쿠폰 혜택 확인"
                        >
                            혜택 확인
                        </RouterLink>
                    </div>
                </section>

                <section class="summary-section card-section">
                    <span class="summary-label">자동결제 카드</span>
                    <template v-if="defaultBillingCard">
                        <strong class="card-name">
                            {{ defaultBillingCard.cardName || "등록카드" }}
                        </strong>
                        <span class="card-number">
                            {{ defaultBillingCard.cardNoMasked || "카드번호 비공개" }}
                        </span>
                        <small>
                            {{ defaultBillingCard.autoPayEnabled ? "자동결제 사용 중" : "자동결제 미사용" }}
                            · 등록 {{ billingCards.length }}장
                        </small>
                    </template>
                    <template v-else>
                        <strong class="card-name">등록된 카드 없음</strong>
                        <small>자동결제를 사용하려면 카드를 등록하세요.</small>
                    </template>
                </section>

                <div class="summary-actions">
                    <RouterLink
                        class="action-button primary"
                        :to="{ name: 'customer-payment-checkout' }"
                    >
                        충전하기
                    </RouterLink>
                    <RouterLink
                        class="action-button secondary"
                        :to="{ name: 'customer-billing-cards' }"
                    >
                        {{ billingCards.length > 0 ? "카드 관리" : "카드 등록" }}
                    </RouterLink>
                </div>

                <p v-if="paymentStore.error" class="summary-error">
                    {{ paymentStore.error }}
                </p>
            </aside>

            <section class="charge-history panel-card" aria-labelledby="charge-history-title">
                <header class="panel-header">
                    <div>
                        <p class="panel-kicker">PREPAID</p>
                        <h2 id="charge-history-title">충전내역</h2>
                    </div>
                    <RouterLink
                        class="panel-link"
                        :to="{
                            name: 'customer-payments',
                            query: { purpose: 'CHARGE' }
                        }"
                    >
                        전체 충전내역
                    </RouterLink>
                </header>

                <p v-if="chargeHistoryLoading" class="panel-state">
                    충전내역을 불러오는 중입니다.
                </p>
                <p v-else-if="chargeHistoryError" class="panel-state error">
                    {{ chargeHistoryError }}
                </p>
                <p v-else-if="recentCharges.length === 0" class="panel-state">
                    표시할 충전내역이 없습니다.
                </p>

                <div v-else class="activity-scroll">
                    <ul class="activity-list charge-list">
                        <li
                            v-for="charge in recentCharges"
                            :key="`charge-${charge.paymentId}`"
                            class="activity-row charge-row"
                        >
                            <time>{{ formatDateTime(charge.approvedAt || charge.createdAt) }}</time>
                            <strong>{{ charge.orderName || "선불 충전" }}</strong>
                            <span>{{ displayPaymentMethod(charge.method) }}</span>
                            <span
                                class="status-badge"
                                :data-status="charge.status"
                            >
                                {{ displayPaymentStatus(charge.status) }}
                            </span>
                            <b>{{ formatAmount(charge.amount) }}</b>
                        </li>
                    </ul>
                </div>
            </section>
        </section>

        <section class="reservation-panel panel-card" aria-labelledby="reservation-title">
            <header class="panel-header">
                <div>
                    <p class="panel-kicker">RESERVATION</p>
                    <h2 id="reservation-title">나의 예약</h2>
                </div>
                <RouterLink
                    class="panel-link"
                    :to="{ name: 'customer-reservations' }"
                >
                    전체 예약 보기
                </RouterLink>
            </header>

            <p v-if="reservationLoading" class="panel-state reservation-state">
                예약을 불러오는 중입니다.
            </p>
            <p v-else-if="reservationError" class="panel-state reservation-state error">
                {{ reservationError }}
            </p>

            <article v-else-if="nextReservation" class="next-reservation-card">
                <div class="reservation-time">
                    <span>다음 예약</span>
                    <strong>{{ formatReservationDate(nextReservation.startTime) }}</strong>
                    <small>
                        {{ formatReservationTime(nextReservation.startTime) }}
                        - {{ formatReservationTime(nextReservation.endTime) }}
                    </small>
                </div>
                <dl class="reservation-details">
                    <div>
                        <dt>세차장</dt>
                        <dd>{{ nextReservation.siteName || `#${nextReservation.washSiteId}` }}</dd>
                    </div>
                    <div>
                        <dt>차량번호</dt>
                        <dd>{{ nextReservation.plateNumber || "-" }}</dd>
                    </div>
                    <div>
                        <dt>세차 코스</dt>
                        <dd>{{ nextReservation.courseName || "-" }}</dd>
                    </div>
                    <div>
                        <dt>예약 상태</dt>
                        <dd>
                            <span class="status-badge" data-status="CONFIRMED">
                                예약 확정
                            </span>
                        </dd>
                    </div>
                </dl>
                <RouterLink
                    class="action-button primary reservation-action"
                    :to="{ name: 'customer-reservations' }"
                >
                    예약 확인
                </RouterLink>
            </article>

            <div v-else class="reservation-empty">
                <div>
                    <strong>예정된 예약이 없습니다.</strong>
                    <p>원하는 세차장과 시간을 선택해 예약해 보세요.</p>
                </div>
                <RouterLink
                    class="action-button primary reservation-action"
                    :to="{ name: 'customer-reservation-create' }"
                >
                    예약하기
                </RouterLink>
            </div>
        </section>

        <section class="wash-history panel-card" aria-labelledby="wash-history-title">
            <header class="panel-header">
                <div>
                    <p class="panel-kicker">WASH HISTORY</p>
                    <h2 id="wash-history-title">세차 이용내역</h2>
                </div>
                <RouterLink
                    class="panel-link"
                    :to="{ name: 'customer-wash-history' }"
                >
                    전체 세차 이용내역
                </RouterLink>
            </header>

            <p v-if="washHistoryLoading" class="panel-state">
                세차 이용내역을 불러오는 중입니다.
            </p>
            <p v-else-if="washHistoryError" class="panel-state error">
                {{ washHistoryError }}
            </p>
            <p v-else-if="recentWashHistories.length === 0" class="panel-state">
                표시할 세차 이용내역이 없습니다.
            </p>

            <div v-else class="activity-scroll">
                <ul class="activity-list wash-list">
                    <li
                        v-for="history in recentWashHistories"
                        :key="history.washHistoryId"
                        class="activity-row wash-row"
                    >
                        <RouterLink
                            class="date-link"
                            :to="{
                                name: 'customer-wash-history-detail',
                                params: { washHistoryId: history.washHistoryId }
                            }"
                        >
                            {{ formatDateTime(history.entryTime) }}
                        </RouterLink>
                        <div class="wash-place">
                            <strong>{{ history.siteName || "세차장" }}</strong>
                            <span>{{ history.courseName || "세차 코스" }}</span>
                        </div>
                        <span class="plate-number">{{ history.plateNumber || "-" }}</span>
                        <span>{{ formatAmount(history.amount) }}</span>
                        <span
                            class="status-badge"
                            :data-status="history.washStatus"
                        >
                            {{ displayWashStatus(history.washStatus) }}
                        </span>
                    </li>
                </ul>
            </div>
        </section>

    </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue"
import { usePaymentStore } from "@/features/payment/stores/paymentStore"
import * as paymentApi from "@/features/payment/api/paymentApi"
import * as washHistoryApi from "@/features/washHistory/api/washHistoryApi"
import * as reservationApi from "@/features/reservation/api/reservationApi"
import * as benefitApi from "@/features/benefit/api/benefitApi"
import * as weatherApi from "@/features/weather/api/weatherApi"

const paymentStore = usePaymentStore()
const WEATHER_LOCATION_KEY = "customerWeatherLocation"
const weatherLocations = [
    { value: "seoul", label: "서울", nx: 60, ny: 127 },
    { value: "busan", label: "부산", nx: 98, ny: 76 },
    { value: "daegu", label: "대구", nx: 89, ny: 90 },
    { value: "incheon", label: "인천", nx: 55, ny: 124 },
    { value: "gwangju", label: "광주", nx: 58, ny: 74 },
    { value: "daejeon", label: "대전", nx: 67, ny: 100 },
    { value: "ulsan", label: "울산", nx: 102, ny: 84 },
    { value: "sejong", label: "세종", nx: 66, ny: 103 },
    { value: "gyeonggi", label: "경기(수원)", nx: 60, ny: 121 },
    { value: "gangwon", label: "강원(춘천)", nx: 73, ny: 134 },
    { value: "chungbuk", label: "충북(청주)", nx: 69, ny: 106 },
    { value: "chungnam", label: "충남(홍성)", nx: 55, ny: 106 },
    { value: "jeonbuk", label: "전북(전주)", nx: 63, ny: 89 },
    { value: "jeonnam", label: "전남(무안)", nx: 51, ny: 67 },
    { value: "gyeongbuk", label: "경북(안동)", nx: 91, ny: 106 },
    { value: "gyeongnam", label: "경남(창원)", nx: 91, ny: 77 },
    { value: "jeju", label: "제주", nx: 52, ny: 38 }
]
const savedWeatherLocation = localStorage.getItem(WEATHER_LOCATION_KEY)
const selectedWeatherLocation = ref(
    weatherLocations.some(location => location.value === savedWeatherLocation)
        ? savedWeatherLocation
        : "seoul"
)
const chargeHistories = ref([])
const recentWashHistories = ref([])
const chargeHistoryLoading = ref(false)
const washHistoryLoading = ref(false)
const chargeHistoryError = ref("")
const washHistoryError = ref("")
const nextReservation = ref(null)
const reservationLoading = ref(false)
const reservationError = ref("")
const benefitSummary = ref(null)
const weather = ref(null)
const weatherLoading = ref(false)
const weatherError = ref("")

const walletBalance = computed(() => paymentStore.wallet?.balance || 0)
const hasBenefits = computed(() =>
    Number(benefitSummary.value?.activeProgramCount || 0) > 0
    || Number(benefitSummary.value?.totalMileageBalance || 0) > 0
    || Number(benefitSummary.value?.totalAvailableCouponCount || 0) > 0
)
const totalMileageBalance = computed(() => Number(benefitSummary.value?.totalMileageBalance || 0))
const totalAvailableCouponCount = computed(() => Number(benefitSummary.value?.totalAvailableCouponCount || 0))
const billingCards = computed(() => paymentStore.billingCards || [])
const defaultBillingCard = computed(() => {
    return billingCards.value.find(card => card.defaultCard)
        || billingCards.value[0]
        || null
})
const recentCharges = computed(() => chargeHistories.value.slice(0, 4))
const weatherForecasts = computed(() => {
    return weather.value?.forecasts || []
})

onMounted(async () => {
    await Promise.all([
        paymentStore.fetchWallet({ silent: true }),
        paymentStore.fetchBillingCards({ silent: true }),
        loadRecentCharges(),
        loadRecentWashHistories(),
        loadNextReservation(),
        loadBenefitSummary(),
        loadWeather()

    ])
})

async function loadNextReservation() {
    reservationLoading.value = true
    reservationError.value = ""

    try {
        const response = await reservationApi.getCustomerReservations({
            page: 1,
            size: 100,
            status: "CONFIRMED",
            startDate: toDateKey(new Date())
        })
        const reservations = response?.content || []
        nextReservation.value = reservations
            .filter(reservation => new Date(reservation.startTime) >= new Date())
            .sort((left, right) => {
                return new Date(left.startTime) - new Date(right.startTime)
            })[0] || null
    } catch (error) {
        nextReservation.value = null
        reservationError.value = "예약을 불러오지 못했습니다."
    } finally {
        reservationLoading.value = false
    }
}

async function loadBenefitSummary() {
    try {
        benefitSummary.value = await benefitApi.getMyBenefitSummary()
    } catch (error) {
        benefitSummary.value = null
    }
}

async function loadRecentCharges() {
    chargeHistoryLoading.value = true
    chargeHistoryError.value = ""

    try {
        chargeHistories.value = await paymentApi.getPaymentList({
            purpose: "CHARGE"
        })
    } catch (error) {
        chargeHistories.value = []
        chargeHistoryError.value = "충전내역을 불러오지 못했습니다."
    } finally {
        chargeHistoryLoading.value = false
    }
}

async function loadRecentWashHistories() {
    washHistoryLoading.value = true
    washHistoryError.value = ""

    try {
        const histories = await washHistoryApi.getWashHistoryList("CUSTOMER")
        recentWashHistories.value = (histories || []).slice(0, 5)
    } catch (error) {
        recentWashHistories.value = []
        washHistoryError.value = "세차 이용내역을 불러오지 못했습니다."
    } finally {
        washHistoryLoading.value = false
    }
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString("ko-KR")}원`
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString("ko-KR")
}

function formatDateTime(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(2, 16).replaceAll("-", ".")
}

function formatReservationDate(value) {
    if (!value) {
        return "-"
    }

    return new Intl.DateTimeFormat("ko-KR", {
        month: "long",
        day: "numeric",
        weekday: "short"
    }).format(new Date(value))
}

function formatReservationTime(value) {
    if (!value) {
        return "-"
    }

    return new Intl.DateTimeFormat("ko-KR", {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
    }).format(new Date(value))
}

function toDateKey(date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")
    return `${year}-${month}-${day}`
}

function displayPaymentMethod(value) {
    return {
        CARD: "카드",
        PREPAID: "선불 지갑",
        PAYAPP: "PayApp",
        TEST: "테스트"
    }[value] || value || "-"
}

function displayPaymentStatus(value) {
    return {
        WAITING: "결제 대기",
        SUCCESS: "충전 완료",
        CANCELED: "충전 취소",
        FAILED: "충전 실패"
    }[value] || value || "-"
}

function displayWashStatus(value) {
    return {
        ENTERED: "입차",
        WAITING: "입차",
        WASHING: "세차중",
        WASH_DONE: "세차완료",
        EXITED: "출차"
    }[value] || value || "-"
}

function weatherDayLabel(value) {
    if (!value) {
        return "-"
    }

    const today = new Date()
    const tomorrow = new Date(today)
    const dayAfterTomorrow = new Date(today)

    tomorrow.setDate(today.getDate() + 1)
    dayAfterTomorrow.setDate(today.getDate() + 2)

    if (value === toDateKey(today)) {
        return "오늘"
    }

    if (value === toDateKey(tomorrow)) {
        return "내일"
    }

    if (value === toDateKey(dayAfterTomorrow)) {
        return "모레"
    }

    return "예보"
}

function formatWeatherDate(value) {
    if (!value) {
        return "-"
    }

    return new Intl.DateTimeFormat("ko-KR", {
        month: "short",
        day: "numeric",
        weekday: "short"
    }).format(new Date(`${value}T00:00:00`))
}

function weatherIcon(forecast) {
    const precipitationType = forecast?.precipitationType

    if (precipitationType === "비"
        || precipitationType === "소나기") {
        return "🌧️"
    }

    if (precipitationType === "눈") {
        return "❄️"
    }

    if (precipitationType === "비/눈") {
        return "🌨️"
    }

    return {
        맑음: "☀️",
        구름많음: "⛅",
        흐림: "☁️"
    }[forecast?.sky] || "🌤️"
}

async function loadWeather() {
    weatherLoading.value = true
    weatherError.value = ""

    try {
        const location = weatherLocations.find(
            item => item.value === selectedWeatherLocation.value
        ) || weatherLocations[0]

        weather.value = await weatherApi.getWeather({
            location: location.label,
            nx: location.nx,
            ny: location.ny
        })
    } catch (error) {
        weather.value = null
        weatherError.value = "날씨 정보를 불러오지 못했습니다."
    } finally {
        weatherLoading.value = false
    }
}

async function changeWeatherLocation() {
    localStorage.setItem(
        WEATHER_LOCATION_KEY,
        selectedWeatherLocation.value
    )
    await loadWeather()
}
</script>

<style scoped>
.customer-dashboard {
    display: grid;
    gap: 18px;
    width: min(100%, var(--app-content-width, 1280px));
    margin: 0 auto;
    color: #172033;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}

.dashboard-overview {
    display: grid;
    grid-template-columns: 330px minmax(0, 1fr);
    gap: 18px;
    align-items: stretch;
}

.panel-card {
    min-width: 0;
    border: 1px solid #d8e0ec;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.weather-panel {
    padding: 22px;
    overflow: hidden;
}

.weather-guide {
    color: #64748b;
    font-size: 13px;
    font-weight: 700;
}

.weather-header-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 14px;
}

.weather-location-field {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #475569;
    font-size: 12px;
    font-weight: 800;
}

.weather-location-field select {
    min-width: 138px;
    min-height: 38px;
    padding: 0 34px 0 12px;
    color: #172033;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    background: #ffffff;
    font: inherit;
    cursor: pointer;
}

.weather-location-field select:focus {
    border-color: #2563eb;
    outline: 3px solid rgba(37, 99, 235, 0.12);
}

.weather-location-field select:disabled {
    opacity: 0.6;
    cursor: wait;
}

.weather-state {
    min-height: 180px;
}

.weather-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
}

.weather-card {
    display: grid;
    gap: 14px;
    min-width: 0;
    padding: 18px;
    border: 1px solid #dbe5f0;
    border-radius: 8px;
    background: linear-gradient(145deg, #f8fbff 0%, #eef6ff 100%);
}

.weather-card > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.weather-card > header strong {
    color: #0b1f3a;
    font-size: 17px;
}

.weather-card time {
    color: #64748b;
    font-size: 12px;
    font-weight: 700;
}

.weather-summary {
    display: flex;
    align-items: center;
    gap: 14px;
}

.weather-icon {
    display: grid;
    place-items: center;
    width: 58px;
    height: 58px;
    flex: 0 0 58px;
    border: 1px solid rgba(158, 177, 201, 0.55);
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.78);
    font-size: 30px;
    line-height: 1;
}

.weather-sky {
    color: #17365d;
    font-size: 16px;
}

.weather-temperature {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin: 5px 0 0;
}

.weather-temperature span {
    color: #52749c;
    font-size: 16px;
    font-weight: 800;
}

.weather-temperature b {
    color: #b45309;
    font-size: 21px;
}

.weather-details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin: 0;
}

.weather-details div {
    min-width: 0;
    padding: 10px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.82);
}

.weather-details dt {
    color: #64748b;
    font-size: 11px;
    font-weight: 800;
}

.weather-details dd {
    margin: 4px 0 0;
    overflow: hidden;
    color: #172033;
    font-size: 14px;
    font-weight: 900;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.wash-condition {
    margin: 0;
    padding-top: 12px;
    border-top: 1px solid #dbe5f0;
    color: #166534;
    font-size: 13px;
    font-weight: 900;
}

.account-summary {
    display: grid;
    grid-template-rows: 1fr 1fr auto;
    overflow: hidden;
}

.summary-section {
    display: flex;
    align-items: flex-start;
    justify-content: center;
    flex-direction: column;
    min-height: 128px;
    padding: 20px 22px;
    border-bottom: 1px solid #e4eaf2;
}

.summary-label {
    color: #52657d;
    font-size: 13px;
    font-weight: 900;
}

.wallet-section > strong {
    margin-top: 6px;
    color: #0b1f3a;
    font-size: 30px;
    font-weight: 900;
    line-height: 1.2;
}

.summary-section small,
.card-number {
    margin-top: 6px;
    color: #718096;
    font-size: 12px;
    line-height: 1.45;
}

.card-name {
    margin-top: 7px;
    color: #0b1f3a;
    font-size: 18px;
    font-weight: 900;
}

.card-number {
    display: block;
    margin-top: 2px;
    color: #415875;
    font-weight: 800;
}

.summary-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    padding: 16px 18px;
}

.wallet-benefits {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
    margin-top: 14px;
}

.benefit-summary-item {
    display: grid;
    gap: 2px;
    min-width: 0;
    padding: 9px 10px;
    border: 1px solid #cfe2d5;
    border-radius: 6px;
    background: #f5faf7;
    color: inherit;
}

.wallet-benefits small {
    margin: 0;
    color: #52657d;
    font-size: 11px;
}

.wallet-benefits b {
    overflow-wrap: anywhere;
    color: #166534;
    font-size: 14px;
}

.benefit-summary-link {
    grid-column: 1 / -1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 34px;
    border: 1px solid #b9d7c2;
    border-radius: 6px;
    color: #166534;
    font-size: 12px;
    font-weight: 800;
    text-decoration: none;
}

.benefit-summary-link:hover,
.benefit-summary-link:focus-visible {
    border-color: #166534;
    background: #eef8f1;
    outline: 2px solid rgba(22, 101, 52, .18);
    outline-offset: 1px;
}

.action-button,
.panel-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 42px;
    padding: 0 14px;
    border: 1px solid #0b1f3a;
    border-radius: 6px;
    font: inherit;
    font-size: 13px;
    font-weight: 900;
    text-decoration: none;
    cursor: pointer;
    box-sizing: border-box;
}

.action-button.primary {
    background: #0b1f3a;
    color: #ffffff;
}

.action-button.secondary {
    background: #ffffff;
    color: #0b1f3a;
}

.action-button:hover,
.panel-link:hover {
    border-color: #174d82;
    background: #174d82;
    color: #ffffff;
}

.summary-error {
    margin: 0;
    padding: 0 18px 15px;
    color: #b42318;
    font-size: 12px;
    font-weight: 800;
}

.charge-history,
.reservation-panel,
.wash-history {
    padding: 22px;
}

.reservation-state {
    min-height: 130px;
}

.next-reservation-card {
    display: grid;
    grid-template-columns: 210px minmax(0, 1fr) auto;
    align-items: center;
    gap: 24px;
    padding: 20px;
    border: 1px solid #dbe5f0;
    border-radius: 8px;
    background: #f8fbff;
}

.reservation-time {
    display: grid;
    gap: 4px;
}

.reservation-time span,
.reservation-details dt {
    color: #64748b;
    font-size: 12px;
    font-weight: 800;
}

.reservation-time strong {
    color: #0b1f3a;
    font-size: 22px;
    font-weight: 900;
}

.reservation-time small {
    color: #174d82;
    font-size: 14px;
    font-weight: 900;
}

.reservation-details {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
    margin: 0;
}

.reservation-details div {
    min-width: 0;
}

.reservation-details dd {
    margin: 6px 0 0;
    overflow: hidden;
    color: #172033;
    font-size: 14px;
    font-weight: 900;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.reservation-action {
    min-width: 108px;
    white-space: nowrap;
}

.reservation-empty {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    min-height: 108px;
    padding: 20px;
    border: 1px dashed #c7d3e2;
    border-radius: 8px;
    background: #f8fafc;
}

.reservation-empty strong {
    color: #0b1f3a;
    font-size: 16px;
}

.reservation-empty p {
    margin: 6px 0 0;
    color: #64748b;
    font-size: 13px;
}

.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    margin-bottom: 16px;
}

.panel-kicker {
    margin: 0 0 4px;
    color: #52749c;
    font-size: 11px;
    font-weight: 900;
}

.panel-header h2 {
    margin: 0;
    color: #0b1f3a;
    font-size: 21px;
    font-weight: 900;
}

.panel-link {
    min-height: 36px;
    border-color: #9eb1c9;
    background: #ffffff;
    color: #17365d;
    white-space: nowrap;
}

.panel-state {
    margin: 0;
    min-height: 170px;
    display: grid;
    place-items: center;
    border-top: 1px solid #e7edf5;
    color: #718096;
    font-size: 13px;
    font-weight: 700;
    text-align: center;
}

.panel-state.error {
    color: #b42318;
}

.activity-scroll {
    max-width: 100%;
    overflow-x: auto;
    border-top: 1px solid #e7edf5;
}

.activity-list {
    min-width: 680px;
    margin: 0;
    padding: 0;
    list-style: none;
}

.activity-row {
    display: grid;
    align-items: center;
    gap: 12px;
    min-height: 52px;
    padding: 8px 4px;
    border-bottom: 1px solid #edf1f6;
    color: #415875;
    font-size: 13px;
}

.activity-row:last-child {
    border-bottom: 0;
}

.charge-row {
    grid-template-columns: 110px minmax(130px, 1fr) 85px 86px 100px;
}

.charge-row b {
    color: #0b1f3a;
    text-align: right;
}

.wash-row {
    grid-template-columns: 116px minmax(190px, 1fr) 100px 90px 80px;
}

.date-link {
    color: #174d82;
    font-weight: 900;
    text-decoration: none;
}

.date-link:hover {
    text-decoration: underline;
}

.wash-place {
    display: grid;
    gap: 2px;
}

.wash-place strong,
.plate-number {
    color: #0b1f3a;
    font-weight: 900;
}

.wash-place span {
    color: #718096;
    font-size: 12px;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 70px;
    min-height: 27px;
    padding: 0 8px;
    border-radius: 999px;
    background: #edf3fa;
    color: #31577f;
    font-size: 11px;
    font-weight: 900;
    box-sizing: border-box;
}

.status-badge[data-status="SUCCESS"],
.status-badge[data-status="DONE"],
.status-badge[data-status="EXITED"],
.status-badge[data-status="WASH_DONE"] {
    background: #e7f4ee;
    color: #047857;
}

.status-badge[data-status="WAITING"] {
    background: #fff7e6;
    color: #b45309;
}

.status-badge[data-status="FAILED"],
.status-badge[data-status="CANCELED"] {
    background: #fff1f2;
    color: #be123c;
}

@media (max-width: 920px) {
    .dashboard-overview {
        grid-template-columns: 1fr;
    }

    .account-summary {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        grid-template-rows: auto auto;
    }

    .summary-section {
        border-bottom: 1px solid #e4eaf2;
    }

    .wallet-section {
        border-right: 1px solid #e4eaf2;
    }

    .summary-actions {
        grid-column: 1 / -1;
    }

    .next-reservation-card {
        grid-template-columns: 180px minmax(0, 1fr);
    }

    .reservation-details {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .next-reservation-card .reservation-action {
        grid-column: 1 / -1;
        width: 100%;
    }
}

@media (max-width: 640px) {
    .customer-dashboard {
        gap: 14px;
    }

    .account-summary {
        grid-template-columns: 1fr;
        grid-template-rows: auto;
    }

    .wallet-section {
        border-right: 0;
    }

    .summary-actions {
        grid-column: auto;
    }

    .panel-header {
        align-items: flex-start;
        flex-direction: column;
    }

    .panel-link {
        width: 100%;
    }

    .weather-grid {
        grid-template-columns: 1fr;
    }

    .weather-guide {
        line-height: 1.5;
    }

    .weather-header-actions {
        width: 100%;
        align-items: flex-start;
        flex-direction: column;
        gap: 9px;
    }

    .weather-location-field,
    .weather-location-field select {
        width: 100%;
    }

    .next-reservation-card {
        grid-template-columns: 1fr;
    }

    .reservation-details {
        grid-template-columns: 1fr 1fr;
    }

    .reservation-empty {
        align-items: stretch;
        flex-direction: column;
    }

    .reservation-empty .reservation-action {
        width: 100%;
    }

}
</style>
