<template>
    <main class="wash-site-page">
        <header class="page-header">
            <div class="page-title">
                <p class="eyebrow"></p>
                <h2>세차장 관리</h2>
            </div>

            <RouterLink
                class="secondary-button benefit-link"
                :to="{ name: isAdmin ? 'admin-benefits' : 'owner-benefits' }"
            >
                마일리지·쿠폰 관리
            </RouterLink>

            <button
                class="secondary-button"
                type="button"
                @click="resetForm"
            >
                신규 등록
            </button>
        </header>

        <section
            v-if="isAdmin"
            class="owner-panel"
        >
            <label>
                사업주 선택
                <select
                    v-model="selectedOwnerId"
                    @change="handleOwnerChange"
                >
                    <option value="">전체 사업주</option>
                    <option
                        v-for="owner in store.owners"
                        :key="owner.ownerId"
                        :value="String(owner.ownerId)"
                    >
                        {{ displayOwnerOption(owner) }}
                    </option>
                </select>
            </label>

            <div class="panel-copy">
                <strong>{{ selectedOwnerTitle }}</strong>
                <span>{{ selectedOwnerDescription }}</span>
            </div>
        </section>

        <p
            v-if="store.loading"
            class="state-text"
        >
            불러오는 중입니다.
        </p>

        <p
            v-if="store.error"
            class="error-text"
        >
            {{ store.error }}
        </p>

        <section class="content-grid">
            <div class="list-section">
                <div class="section-title">
                    <h3>세차장 목록</h3>
                    <span>{{ store.washSites.length }}건</span>
                </div>

                <div class="site-list">
                    <button
                        v-for="site in store.washSites"
                        :key="site.washSiteId"
                        type="button"
                        :class="['site-item', { selected: form.washSiteId === site.washSiteId }]"
                        @click="selectWashSite(site)"
                    >
                        <span class="site-name">{{ displayValue(site.siteName) }}</span>
                        <span class="site-meta">
                            #{{ site.washSiteId }}
                            <template v-if="isAdmin"> · {{ displayOwner(site) }}</template>
                        </span>
                        <span class="site-contact">
                            <span class="site-address">{{ displayValue(site.address) }}</span>
                            <span class="site-phone">유선전화 {{ displaySitePhone(site.sitePhone) }}</span>
                        </span>
                        <span class="site-price">{{ formatAmount(site.washPrice) }}</span>
                        <span :class="['status-badge', getStatusClass(site.status)]">
                            {{ displayStatus(site.status) }}
                        </span>
                    </button>
                </div>

                <div
                    v-if="!store.loading && store.washSites.length === 0"
                    class="empty-state"
                >
                    선택한 조건에 해당하는 세차장이 없습니다.
                </div>
            </div>

            <form
                class="form-panel"
                @submit.prevent="saveWashSite"
            >
                <div class="section-title">
                    <h3>{{ formTitle }}</h3>
                    <div
                        v-if="form.washSiteId"
                        class="site-form-actions"
                    >
                        <RouterLink
                            v-if="!isAdmin"
                            class="business-hour-link"
                            :to="{
                                name: 'owner-business-hours',
                                params: {
                                    washSiteId: form.washSiteId
                                }
                            }"
                        >
                            영업시간 설정
                        </RouterLink>
                        <span>#{{ form.washSiteId }}</span>
                    </div>
                </div>

                <label v-if="isAdmin">
                    사업주
                    <select v-model="form.ownerId" @change="applyRegisteredAddress">
                        <option value="">선택</option>
                        <option
                            v-for="owner in store.owners"
                            :key="owner.ownerId"
                            :value="String(owner.ownerId)"
                        >
                            {{ displayOwnerOption(owner) }}
                        </option>
                    </select>
                </label>

                <label>
                    세차장명
                    <input
                        v-model.trim="form.siteName"
                        type="text"
                    >
                </label>

                <div class="address-field">
                    <label>
                        주소
                        <input
                            v-model.trim="form.address"
                            type="text"
                            placeholder="도로명 주소"
                            @input="clearGeocodeState"
                        >
                    </label>
                    <button
                        class="secondary-button address-check-button"
                        type="button"
                        :disabled="geocoding"
                        @click="checkAddressLocation"
                    >
                        {{ geocoding ? "확인 중" : "주소 위치 확인" }}
                    </button>
                </div>

                <p
                    v-if="geocodeMessage"
                    :class="['coordinate-message', { error: geocodeFailed }]"
                    role="status"
                >
                    {{ geocodeMessage }}
                </p>

                <div class="coordinate-grid">
                    <label>
                        위도 (자동 입력)
                        <input
                            v-model.number="form.latitude"
                            type="number"
                            readonly
                            min="33"
                            max="39.5"
                            step="0.000001"
                            :placeholder="geocodeFailed ? '주소 확인' : ''"
                        >
                    </label>
                    <label>
                        경도 (자동 입력)
                        <input
                            v-model.number="form.longitude"
                            type="number"
                            readonly
                            min="124"
                            max="132"
                            step="0.000001"
                            :placeholder="geocodeFailed ? '주소 확인' : ''"
                        >
                    </label>
                </div>

                <label>
                    유선전화번호
                    <span class="phone-input-group">
                        <input
                            :value="phoneParts.first"
                            type="text"
                            inputmode="numeric"
                            maxlength="3"
                            placeholder="051"
                            required
                            @input="updatePhonePart('first', $event, 3)"
                            @paste="handlePhonePaste"
                        >
                        <span aria-hidden="true">-</span>
                        <input
                            :value="phoneParts.middle"
                            type="text"
                            inputmode="numeric"
                            maxlength="4"
                            placeholder="1234"
                            required
                            @input="updatePhonePart('middle', $event, 4)"
                            @paste="handlePhonePaste"
                        >
                        <span aria-hidden="true">-</span>
                        <input
                            :value="phoneParts.last"
                            type="text"
                            inputmode="numeric"
                            maxlength="4"
                            placeholder="7777"
                            required
                            @input="updatePhonePart('last', $event, 4)"
                            @paste="handlePhonePaste"
                        >
                    </span>
                </label>

                <label>
                    기본 세차코스
                    <input
                        v-model.trim="form.courseName"
                        type="text"
                        placeholder="기본 세차"
                        required
                    >
                </label>

                <label>
                    세차가격
                    <input
                        v-model.number="form.washPrice"
                        type="number"
                        min="100"
                        step="100"
                        required
                    >
                </label>

                <label>
                    상태
                    <select v-model="form.status">
                        <option value="ACTIVE">운영중</option>
                        <option value="INACTIVE">중지</option>
                    </select>
                </label>

                <button
                    class="primary-button"
                    type="submit"
                >
                    저장
                </button>
            </form>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useWashSiteStore } from "../stores/washSiteStore"
import { geocodeWithNaver } from "../utils/naverMapLoader"
import { businessPhoneDigits, isValidBusinessPhone } from "@/shared/utils/businessPhone"
import * as ownerApi from "@/features/owner/api/ownerApi"

const props = defineProps({
    viewerRole: {
        type: String,
        required: true
    },
    ownerId: {
        type: [String, Number],
        default: ""
    }
})

const store = useWashSiteStore()
const selectedOwnerId = ref("")
const geocoding = ref(false)
const geocodeMessage = ref("")
const geocodeFailed = ref(false)
const ownerBusinessAddress = ref("")

const form = reactive({
    washSiteId: null,
    ownerId: "",
    siteName: "",
    address: "",
    latitude: null,
    longitude: null,
    sitePhone: "",
    status: "ACTIVE",
    courseName: "기본 세차",
    washPrice: null
})

const phoneParts = reactive({
    first: "",
    middle: "",
    last: ""
})

const isAdmin = computed(() => props.viewerRole === "ADMIN")

const roleLabel = computed(() => {
    return isAdmin.value ? "관리자" : "사업주"
})

const formTitle = computed(() => {
    return form.washSiteId ? "세차장 수정" : "세차장 등록"
})

const selectedOwner = computed(() => {
    return store.owners.find(owner => String(owner.ownerId) === selectedOwnerId.value)
})

const selectedOwnerTitle = computed(() => {
    if (!selectedOwnerId.value) {
        return "전체 사업주 세차장"
    }

    return `${selectedOwner.value?.ownerName || "선택한 사업주"} 세차장`
})

const selectedOwnerDescription = computed(() => {
    if (!selectedOwnerId.value) {
        return "관리자는 전체 사업주의 세차장을 보거나 특정 사업주를 선택해 세차장을 관리할 수 있습니다."
    }

    return "선택한 사업주가 가진 세차장만 목록에 표시됩니다."
})

onMounted(async () => {
    selectedOwnerId.value = props.ownerId ? String(props.ownerId) : ""

    if (isAdmin.value) {
        await store.fetchOwners()
    } else {
        try {
            const owner = await ownerApi.getMyPage()
            ownerBusinessAddress.value = String(owner?.businessAddress || "").trim()
        } catch {
            ownerBusinessAddress.value = ""
        }
    }

    applyRegisteredAddress()
    await loadWashSites()
})

async function handleOwnerChange() {
    resetForm()
    await loadWashSites()
}

async function loadWashSites() {
    await store.fetchWashSites(props.viewerRole, selectedOwnerId.value)
}

function selectWashSite(site) {
    form.washSiteId = site.washSiteId
    form.ownerId = site.ownerId ? String(site.ownerId) : ""
    form.siteName = site.siteName || ""
    form.address = site.address || ""
    form.latitude = site.latitude ?? null
    form.longitude = site.longitude ?? null
    geocodeMessage.value = form.latitude !== null && form.longitude !== null
        ? "저장된 지도 좌표를 불러왔습니다."
        : ""
    geocodeFailed.value = false
    setPhoneParts(site.sitePhone)
    form.status = site.status || "ACTIVE"
    form.courseName = site.courseName || "기본 세차"
    form.washPrice = site.washPrice ?? null
}

function resetForm() {
    form.washSiteId = null
    form.ownerId = selectedOwnerId.value || ""
    form.siteName = ""
    form.address = registeredAddressForForm()
    form.latitude = null
    form.longitude = null
    geocodeMessage.value = ""
    geocodeFailed.value = false
    setPhoneParts("")
    form.status = "ACTIVE"
    form.courseName = "기본 세차"
    form.washPrice = null
}

function registeredAddressForForm() {
    if (!isAdmin.value) {
        return ownerBusinessAddress.value
    }

    const ownerId = form.ownerId || selectedOwnerId.value
    const owner = store.owners.find(item => String(item.ownerId) === String(ownerId))
    return String(owner?.businessAddress || "").trim()
}

function applyRegisteredAddress() {
    if (form.washSiteId) return
    form.address = registeredAddressForForm()
    form.latitude = null
    form.longitude = null
    geocodeMessage.value = ""
    geocodeFailed.value = false
}

async function saveWashSite() {
    if (!form.siteName) {
        store.error = "세차장명을 입력해야 합니다."
        return
    }

    if (isAdmin.value && !form.ownerId) {
        store.error = "사업주를 선택해야 합니다."
        return
    }

    if (!form.address) {
        store.error = "세차장 주소를 입력해야 합니다."
        return
    }

    if (form.latitude === null && form.longitude === null) {
        await checkAddressLocation()
    }

    if ((form.latitude === null) !== (form.longitude === null)) {
        store.error = "위도와 경도는 함께 입력해야 합니다."
        return
    }

    if (!isValidBusinessPhone(form.sitePhone)) {
        store.error = "유선전화번호를 올바르게 입력해 주세요."
        return
    }

    const washPrice = Number(form.washPrice)
    if (!Number.isInteger(washPrice) || washPrice < 100 || washPrice % 100 !== 0) {
        store.error = "세차가격은 100원 이상이며 100원 단위로 입력해야 합니다."
        return
    }

    const payload = {
        siteName: form.siteName,
        address: form.address,
        latitude: form.latitude === null ? null : Number(form.latitude),
        longitude: form.longitude === null ? null : Number(form.longitude),
        sitePhone: form.sitePhone,
        status: form.status,
        courseName: form.courseName || "기본 세차",
        washPrice
    }

    if (isAdmin.value) {
        payload.ownerId = Number(form.ownerId)
    }

    const success = await store.saveWashSite(
        props.viewerRole,
        payload,
        form.washSiteId
    )

    if (success) {
        resetForm()
        await loadWashSites()
    }
}

async function checkAddressLocation() {
    if (!form.address) {
        geocodeFailed.value = true
        geocodeMessage.value = ""
        return false
    }

    geocoding.value = true
    geocodeFailed.value = false
    geocodeMessage.value = ""

    try {
        const coordinates = await geocodeWithNaver(form.address)
        form.latitude = Number(coordinates.latitude.toFixed(6))
        form.longitude = Number(coordinates.longitude.toFixed(6))
        geocodeMessage.value = "네이버 지도에서 주소 위치를 확인했습니다."
        return true
    } catch {
        form.latitude = null
        form.longitude = null
        geocodeFailed.value = true
        geocodeMessage.value = ""
        return false
    } finally {
        geocoding.value = false
    }
}

function clearGeocodeState() {
    form.latitude = null
    form.longitude = null
    geocodeMessage.value = ""
    geocodeFailed.value = false
}

function displayOwnerOption(owner) {
    const businessNumber = owner.businessNumber || "-"
    return `${owner.ownerName || "이름 없음"} / ${businessNumber}`
}

function displayOwner(site) {
    if (!site.ownerName) {
        return "-"
    }

    return `${site.ownerName} / ${site.businessNumber || "-"}`
}

function displayValue(value) {
    return value || "-"
}

function displaySitePhone(value) {
    return value || "미등록"
}

function digitsOnly(value) {
    return String(value || "").replace(/\D/g, "")
}

function syncSitePhone() {
    const values = [
        phoneParts.first,
        phoneParts.middle,
        phoneParts.last
    ]

    form.sitePhone = values.every(value => !value)
        ? ""
        : values.join("-")
}

function setPhoneParts(value) {
    const digits = businessPhoneDigits(value)
    const firstLength = digits.startsWith("02") ? 2 : 3
    const middleLength = digits.length - firstLength > 7 ? 4 : 3

    phoneParts.first = digits.slice(0, firstLength)
    phoneParts.middle = digits.slice(firstLength, firstLength + middleLength)
    phoneParts.last = digits.slice(firstLength + middleLength)
    syncSitePhone()
}

function updatePhonePart(part, event, maxLength) {
    const value = digitsOnly(event.target.value).slice(0, maxLength)

    phoneParts[part] = value
    event.target.value = value
    syncSitePhone()
}

function handlePhonePaste(event) {
    const digits = digitsOnly(event.clipboardData?.getData("text"))

    if (digits.length < 9) {
        return
    }

    event.preventDefault()
    setPhoneParts(digits)
}

function formatAmount(value) {
    if (value === null || value === undefined) {
        return "가격 미설정"
    }

    return `${Number(value).toLocaleString()}원`
}

function displayStatus(value) {
    const labels = {
        ACTIVE: "운영중",
        INACTIVE: "중지"
    }

    return labels[value] || displayValue(value)
}

function getStatusClass(value) {
    if (value === "ACTIVE") {
        return "status-active"
    }

    return "status-inactive"
}
</script>

<style scoped>
.wash-site-page {
    padding: 24px;
    color: #172033;
}

.page-header,
.section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.site-form-actions {
    display: flex;
    align-items: center;
    gap: 10px;
}

.business-hour-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 32px;
    padding: 0 11px;
    border: 1px solid #9eb1c9;
    border-radius: 6px;
    color: #17365d;
    background: #ffffff;
    font-size: 12px;
    font-weight: 800;
    text-decoration: none;
}

.business-hour-link:hover {
    border-color: #2454d6;
    color: #2454d6;
    background: #f8fbff;
}

.page-header {
    margin-bottom: 18px;
}

.page-title {
    flex: 1;
    text-align: center;
}

.eyebrow {
    margin: 0 0 6px;
    color: #667085;
    font-size: 13px;
}

h2,
h3 {
    margin: 0;
}

h2 {
    font-size: 30px;
    font-family: "Pretendard", sans-serif;
}

h3 {
    font-size: 17px;
}

.owner-panel,
.form-panel {
    display: grid;
    gap: 14px;
    padding: 18px;
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #ffffff;
}

.owner-panel {
    grid-template-columns: minmax(240px, 360px) minmax(0, 1fr);
    align-items: end;
    margin-bottom: 18px;
}

.panel-copy {
    display: grid;
    gap: 6px;
    color: #667085;
    font-size: 14px;
}

.panel-copy strong {
    color: #172033;
}

.content-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(320px, 360px);
    gap: 18px;
    align-items: start;
}

.list-section {
    display: grid;
    gap: 12px;
    min-width: 0;
}

.site-list {
    display: grid;
    gap: 10px;
}

.site-item {
    display: grid;
    grid-template-columns: minmax(0, 1.1fr) max-content minmax(0, 1.6fr) max-content max-content;
    gap: 12px;
    align-items: center;
    width: 100%;
    padding: 14px;
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #ffffff;
    color: #172033;
    text-align: left;
    cursor: pointer;
    min-width: 0;
}

.site-item > * {
    min-width: 0;
}

.site-item:hover,
.site-item.selected {
    border-color: #2454d6;
    background: #f8fbff;
}

.site-name {
    font-weight: 800;
}

.site-meta,
.site-contact,
.site-price {
    color: #667085;
    font-size: 13px;
}

.site-contact {
    display: grid;
    gap: 4px;
    min-width: 0;
}

.site-name,
.site-meta,
.site-address,
.site-phone,
.site-price {
    overflow-wrap: anywhere;
}

.site-phone {
    color: #344054;
    font-weight: 700;
}

.site-price {
    color: #172033;
    font-weight: 800;
}

label {
    display: grid;
    gap: 6px;
    color: #344054;
    font-size: 13px;
    font-weight: 700;
}

input,
select {
    width: 100%;
    height: 38px;
    box-sizing: border-box;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    padding: 0 10px;
    color: #172033;
    background: #ffffff;
}

.address-field {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    align-items: end;
}

.address-check-button {
    min-width: 124px;
}

.address-check-button:disabled {
    cursor: wait;
    opacity: 0.62;
}

.coordinate-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}

.coordinate-message {
    margin: -4px 0 0;
    color: #0f766e;
    font-size: 12px;
    line-height: 1.45;
}

.coordinate-message.error {
    color: #c03434;
}

.phone-input-group {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto minmax(0, 1.2fr);
    gap: 8px;
    align-items: center;
}

.phone-input-group input {
    min-width: 0;
    text-align: center;
}

.phone-input-group > span {
    color: #667085;
    font-weight: 800;
}

.primary-button,
.secondary-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 92px;
    height: 38px;
    padding: 0 14px;
    border-radius: 6px;
    box-sizing: border-box;
    font-family: inherit;
    font-size: 13px;
    font-weight: 700;
    line-height: 1;
    text-align: center;
    text-decoration: none;
    white-space: nowrap;
    cursor: pointer;
}

.primary-button {
    border: 0;
    background: #2454d6;
    color: #ffffff;
}

.primary-button:hover {
    background: #1d45b3;
}

.secondary-button {
    border: 1px solid #cbd5e1;
    background: #ffffff;
    color: #172033;
}

.secondary-button:hover {
    background: #f8fafc;
}

.benefit-link {
    min-width: 142px;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 24px;
    padding: 3px 9px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 700;
    white-space: nowrap;
    justify-self: end;
}

.status-active {
    background: #e9f7ef;
    color: #166534;
}

.status-inactive {
    background: #eef2f7;
    color: #475467;
}

.state-text,
.error-text {
    margin: 16px 0;
}

.error-text {
    color: #c03434;
}

.empty-state {
    padding: 22px;
    border: 1px solid #d9e2f1;
    border-radius: 8px;
    background: #f9fbff;
    color: #475467;
}

@media (max-width: 1280px) {
    .owner-panel,
    .content-grid {
        grid-template-columns: 1fr;
    }

    .form-panel {
        width: 100%;
        box-sizing: border-box;
    }
}

@media (max-width: 820px) {
    .wash-site-page {
        padding: 18px 16px;
    }

    .site-item {
        grid-template-columns: minmax(0, 1fr) auto;
    }

    .site-contact {
        grid-column: 1 / -1;
    }

    .address-field,
    .coordinate-grid {
        grid-template-columns: 1fr;
    }
}
</style>
