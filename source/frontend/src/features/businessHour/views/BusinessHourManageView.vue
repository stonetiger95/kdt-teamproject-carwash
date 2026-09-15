<template>
    <main class="business-hour-page">
        <header class="page-header">
            <div>
                <p class="eyebrow">BUSINESS HOURS</p>
                <h1>영업시간 설정</h1>
                <p>{{ washSite?.siteName || "관리할 세차장을 선택해 주세요." }}</p>
            </div>

            <RouterLink
                class="secondary-button"
                :to="{ name: 'owner-wash-sites' }"
            >
                세차장 관리로 돌아가기
            </RouterLink>
        </header>

        <section class="site-selector-panel">
            <label for="business-hour-site">세차장 선택</label>
            <select
                id="business-hour-site"
                v-model="selectedWashSiteId"
                :disabled="siteLoading || washSites.length === 0"
                @change="changeWashSite"
            >
                <option
                    v-for="site in washSites"
                    :key="site.washSiteId"
                    :value="Number(site.washSiteId)"
                >
                    {{ site.siteName }} (#{{ site.washSiteId }})
                </option>
            </select>
        </section>

        <p
            v-if="!siteLoading && washSites.length === 0"
            class="state-text"
        >
            등록된 세차장이 없습니다. 세차장을 먼저 등록해 주세요.
        </p>

        <p v-if="loading" class="state-text">
            영업시간을 불러오는 중입니다.
        </p>

        <p v-if="errorMessage" class="error-text">
            {{ errorMessage }}
        </p>

        <p v-if="successMessage" class="success-text">
            {{ successMessage }}
        </p>

        <section
            v-if="!loading && selectedWashSiteId"
            class="hour-panel"
        >
            <header class="panel-header">
                <div>
                    <h2>요일별 운영시간</h2>
                    <p>일반 영업, 24시간 영업 또는 휴무를 선택할 수 있습니다.</p>
                </div>

                <button
                    class="copy-button"
                    type="button"
                    @click="copyMondayToWeekdays"
                >
                    월요일 설정을 평일에 적용
                </button>
            </header>

            <div class="hour-list">
                <article
                    v-for="businessHour in businessHours"
                    :key="businessHour.dayOfWeek"
                    class="hour-row"
                >
                    <strong>{{ dayLabels[businessHour.dayOfWeek] }}</strong>

                    <label>
                        <span class="sr-only">
                            {{ dayLabels[businessHour.dayOfWeek] }} 영업 형태
                        </span>
                        <select
                            v-model="businessHour.mode"
                            @change="changeMode(businessHour)"
                        >
                            <option value="NORMAL">일반 영업</option>
                            <option value="TWENTY_FOUR_HOURS">24시간</option>
                            <option value="CLOSED">휴무</option>
                        </select>
                    </label>

                    <div
                        v-if="businessHour.mode === 'NORMAL'"
                        class="time-range"
                    >
                        <label>
                            <span class="sr-only">영업 시작</span>
                            <input
                                v-model="businessHour.openTime"
                                type="time"
                                step="1800"
                            >
                        </label>
                        <span>~</span>
                        <label>
                            <span class="sr-only">영업 종료</span>
                            <input
                                v-model="businessHour.closeTime"
                                type="time"
                                step="1800"
                            >
                        </label>
                    </div>

                    <p v-else class="mode-description">
                        {{ businessHour.mode === "CLOSED"
                            ? "예약을 받지 않습니다."
                            : "하루 24시간 예약을 받습니다." }}
                    </p>
                </article>
            </div>

            <footer class="panel-footer">
                <p>
                    변경된 영업시간은 고객의 예약 가능 시간 조회에 바로 반영됩니다.
                </p>
                <button
                    class="primary-button"
                    type="button"
                    :disabled="saving"
                    @click="saveBusinessHours"
                >
                    {{ saving ? "저장 중" : "전체 저장" }}
                </button>
            </footer>
        </section>
    </main>
</template>

<script setup>
import { onMounted, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import * as washSiteApi from "@/features/washSite/api/washSiteApi"
import * as businessHourApi from "../api/businessHourApi"

const route = useRoute()
const router = useRouter()
const washSites = ref([])
const selectedWashSiteId = ref(null)
const washSite = ref(null)
const businessHours = ref([])
const siteLoading = ref(false)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref("")
const successMessage = ref("")

const dayLabels = {
    1: "월요일",
    2: "화요일",
    3: "수요일",
    4: "목요일",
    5: "금요일",
    6: "토요일",
    7: "일요일"
}

onMounted(loadWashSites)

async function loadWashSites() {
    siteLoading.value = true
    errorMessage.value = ""

    try {
        const response = await washSiteApi.getWashSiteList("OWNER")
        washSites.value = Array.isArray(response) ? response : []

        if (washSites.value.length === 0) {
            return
        }

        const routeWashSiteId = Number(route.params.washSiteId)
        const routeSiteExists = washSites.value.some(
            site => Number(site.washSiteId) === routeWashSiteId
        )

        selectedWashSiteId.value = routeSiteExists
            ? routeWashSiteId
            : Number(washSites.value[0].washSiteId)

        await loadBusinessHours()
    } catch (error) {
        errorMessage.value = getErrorMessage(
            error,
            "세차장 목록을 불러오지 못했습니다."
        )
    } finally {
        siteLoading.value = false
    }
}

async function changeWashSite() {
    successMessage.value = ""

    await router.replace({
        name: "owner-business-hours",
        params: {
            washSiteId: selectedWashSiteId.value
        }
    })

    await loadBusinessHours()
}

async function loadBusinessHours() {
    if (!selectedWashSiteId.value) {
        return
    }

    loading.value = true
    errorMessage.value = ""

    try {
        const [washSiteResponse, businessHourResponse] =
            await Promise.all([
                washSiteApi.getWashSite(
                    "OWNER",
                    selectedWashSiteId.value
                ),
                businessHourApi.getBusinessHours(
                    selectedWashSiteId.value
                )
            ])

        washSite.value = washSiteResponse
        businessHours.value = normalizeBusinessHours(
            businessHourResponse
        )
    } catch (error) {
        errorMessage.value = getErrorMessage(
            error,
            "영업시간을 불러오지 못했습니다."
        )
    } finally {
        loading.value = false
    }
}

function normalizeBusinessHours(response) {
    const businessHourMap = new Map(
        (Array.isArray(response) ? response : [])
            .map(businessHour => [
                Number(businessHour.dayOfWeek),
                businessHour
            ])
    )

    return Array.from({ length: 7 }, (_, index) => {
        const dayOfWeek = index + 1
        const businessHour = businessHourMap.get(dayOfWeek) || {}

        return {
            dayOfWeek,
            openTime: normalizeTime(businessHour.openTime),
            closeTime: normalizeTime(businessHour.closeTime),
            mode: getMode(businessHour)
        }
    })
}

function getMode(businessHour) {
    if (businessHour.closed) {
        return "CLOSED"
    }

    if (businessHour.twentyFourHours) {
        return "TWENTY_FOUR_HOURS"
    }

    return "NORMAL"
}

function changeMode(businessHour) {
    if (businessHour.mode === "NORMAL") {
        businessHour.openTime ||= "09:00"
        businessHour.closeTime ||= "22:00"
        return
    }

    businessHour.openTime = ""
    businessHour.closeTime = ""
}

function copyMondayToWeekdays() {
    const monday = businessHours.value[0]

    for (let index = 1; index <= 4; index += 1) {
        businessHours.value[index].mode = monday.mode
        businessHours.value[index].openTime = monday.openTime
        businessHours.value[index].closeTime = monday.closeTime
    }
}

async function saveBusinessHours() {
    errorMessage.value = ""
    successMessage.value = ""

    const validationMessage = validateBusinessHours()
    if (validationMessage) {
        errorMessage.value = validationMessage
        return
    }

    saving.value = true

    try {
        await businessHourApi.updateBusinessHours(
            selectedWashSiteId.value,
            businessHours.value.map(toRequestDTO)
        )

        await loadBusinessHours()
        successMessage.value = "영업시간을 저장했습니다."
    } catch (error) {
        errorMessage.value = getErrorMessage(
            error,
            "영업시간을 저장하지 못했습니다."
        )
    } finally {
        saving.value = false
    }
}

function validateBusinessHours() {
    for (const businessHour of businessHours.value) {
        if (businessHour.mode !== "NORMAL") {
            continue
        }

        if (!businessHour.openTime || !businessHour.closeTime) {
            return `${dayLabels[businessHour.dayOfWeek]}의 시작 시간과 종료 시간을 입력해 주세요.`
        }

        if (businessHour.openTime >= businessHour.closeTime) {
            return `${dayLabels[businessHour.dayOfWeek]}의 시작 시간은 종료 시간보다 빨라야 합니다.`
        }
    }

    return ""
}

function toRequestDTO(businessHour) {
    const normal = businessHour.mode === "NORMAL"

    return {
        dayOfWeek: businessHour.dayOfWeek,
        openTime: normal ? businessHour.openTime : null,
        closeTime: normal ? businessHour.closeTime : null,
        closed: businessHour.mode === "CLOSED",
        twentyFourHours:
            businessHour.mode === "TWENTY_FOUR_HOURS"
    }
}

function normalizeTime(value) {
    return value ? String(value).slice(0, 5) : ""
}

function getErrorMessage(error, fallback) {
    return error?.response?.data?.message
        || error?.response?.data?.detail
        || fallback
}
</script>

<style scoped>
.business-hour-page {
    display: grid;
    gap: 18px;
    padding: 24px;
    color: #172033;
    text-align: left;
}

.page-header,
.site-selector-panel,
.hour-panel {
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.site-selector-panel {
    display: grid;
    grid-template-columns: 120px minmax(220px, 420px);
    gap: 14px;
    align-items: center;
    padding: 18px 22px;
}

.site-selector-panel label {
    color: #0b1f3a;
    font-size: 13px;
    font-weight: 900;
}

.page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding: 24px 26px;
}

.eyebrow {
    margin: 0 0 5px;
    color: #52749c;
    font-size: 11px;
    font-weight: 900;
}

.page-header h1,
.panel-header h2 {
    margin: 0;
    color: #0b1f3a;
    font-weight: 900;
}

.page-header h1 {
    font-size: 28px;
}

.page-header p:last-child,
.panel-header p,
.panel-footer p {
    margin-top: 6px;
    color: #667085;
    font-size: 13px;
}

.secondary-button,
.copy-button,
.primary-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 38px;
    padding: 0 14px;
    border-radius: 6px;
    font: inherit;
    font-size: 13px;
    font-weight: 800;
    text-decoration: none;
    cursor: pointer;
}

.secondary-button,
.copy-button {
    border: 1px solid #cbd5e1;
    color: #172033;
    background: #ffffff;
}

.primary-button {
    min-width: 100px;
    border: 0;
    color: #ffffff;
    background: #2454d6;
}

.primary-button:disabled {
    cursor: wait;
    opacity: 0.65;
}

.state-text,
.error-text,
.success-text {
    margin: 0;
    padding: 14px 16px;
    border-radius: 7px;
    font-size: 13px;
    font-weight: 800;
}

.state-text {
    color: #31577f;
    background: #edf3fa;
}

.error-text {
    color: #b42318;
    background: #fff1f1;
}

.success-text {
    color: #087944;
    background: #eaf8f1;
}

.hour-panel {
    overflow: hidden;
}

.panel-header,
.panel-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding: 20px 22px;
}

.panel-header {
    border-bottom: 1px solid #e7edf5;
}

.panel-header h2 {
    font-size: 19px;
}

.hour-list {
    display: grid;
}

.hour-row {
    display: grid;
    grid-template-columns: 110px 170px minmax(0, 1fr);
    gap: 16px;
    align-items: center;
    min-height: 68px;
    padding: 10px 22px;
    border-bottom: 1px solid #edf2f7;
}

.hour-row > strong {
    color: #0b1f3a;
    font-size: 14px;
    font-weight: 900;
}

select,
input {
    width: 100%;
    height: 39px;
    padding: 0 10px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    color: #172033;
    background: #ffffff;
    box-sizing: border-box;
}

.time-range {
    display: grid;
    grid-template-columns: minmax(120px, 170px) auto minmax(120px, 170px);
    gap: 10px;
    align-items: center;
}

.mode-description {
    color: #667085;
    font-size: 13px;
}

.panel-footer {
    background: #f8fafc;
}

.panel-footer p {
    margin: 0;
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

@media (max-width: 720px) {
    .business-hour-page {
        padding: 18px 14px;
    }

    .page-header,
    .panel-header,
    .panel-footer {
        align-items: stretch;
        flex-direction: column;
    }

    .site-selector-panel {
        grid-template-columns: 1fr;
    }

    .hour-row {
        grid-template-columns: 1fr;
        gap: 9px;
        padding: 16px;
    }

    .time-range {
        grid-template-columns: 1fr auto 1fr;
    }
}
</style>
