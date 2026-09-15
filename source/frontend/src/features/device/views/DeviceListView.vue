<template>
    <main class="device-page">
        <section class="page-hero">
            <div>
                <p class="eyebrow"></p>
                <h1>장치 목록</h1>
                <p>{{ selectedWashSite?.siteName || "관리할 세차장을 선택해 주세요." }}</p>
            </div>

            <RouterLink
                :to="{
                    name: 'owner-device-create',
                    query: {
                        washSiteId: selectedWashSiteId || undefined
                    }
                }"
                class="create-button"
            >
                장치 추가
            </RouterLink>
        </section>

        <section class="site-selector-panel">
            <label for="device-wash-site">세차장 선택</label>
            <select
                id="device-wash-site"
                v-model="selectedWashSiteId"
                :disabled="washSiteStore.loading || washSiteStore.washSites.length === 0"
            >
                <option
                    v-for="site in washSiteStore.washSites"
                    :key="site.washSiteId"
                    :value="Number(site.washSiteId)"
                >
                    {{ site.siteName }} (#{{ site.washSiteId }})
                </option>
            </select>
        </section>

        <p
            v-if="store.loading || washSiteStore.loading"
            class="status-message"
        >
            불러오는 중입니다.
        </p>

        <p
            v-if="store.error || washSiteStore.error"
            class="status-message error"
        >
            {{ store.error || washSiteStore.error }}
        </p>

        <p
            v-if="!washSiteStore.loading && washSiteStore.washSites.length === 0"
            class="status-message"
        >
            등록된 세차장이 없습니다. 세차장을 먼저 등록해 주세요.
        </p>

        <section
            v-if="selectedWashSiteId"
            class="table-card"
        >
            <header class="section-title">
                <h2>등록 장치</h2>
                <span>총 {{ filteredDevices.length }}개</span>
            </header>

            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>장치명</th>
                            <th>종류</th>
                            <th>상태</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="device in filteredDevices"
                            :key="device.deviceId"
                            class="detail-row"
                            tabindex="0"
                            role="button"
                            :aria-label="`${displayValue(device.deviceName)} 장비 상세보기`"
                            @click="openDetail(device.deviceId)"
                            @keydown.enter="openDetail(device.deviceId)"
                            @keydown.space.prevent="openDetail(device.deviceId)"
                        >
                            <td>{{ device.deviceId }}</td>
                            <td class="strong-cell">{{ displayValue(device.deviceName) }}</td>
                            <td>{{ typeLabel(device.deviceType) }}</td>
                            <td>
                                <span :class="['badge', device.status === 'ACTIVE' ? 'success' : 'muted']">
                                    {{ statusLabel(device.status) }}
                                </span>
                            </td>
                        </tr>

                        <tr v-if="!store.loading && filteredDevices.length === 0">
                            <td
                                class="empty-cell"
                                colspan="4"
                            >
                                등록된 장치가 없습니다.
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useDeviceStore } from "../stores/deviceStore"
import { useWashSiteStore } from "@/features/washSite/stores/washSiteStore"

const store = useDeviceStore()
const washSiteStore = useWashSiteStore()
const route = useRoute()
const router = useRouter()
const selectedWashSiteId = ref(null)

const selectedWashSite = computed(() =>
    washSiteStore.washSites.find(
        site => Number(site.washSiteId) === selectedWashSiteId.value
    ) || null
)

const filteredDevices = computed(() =>
    store.devices.filter(
        device => Number(device.washSiteId) === selectedWashSiteId.value
    )
)

onMounted(async () => {
    await Promise.all([
        store.fetchDevices(),
        washSiteStore.fetchWashSites("OWNER")
    ])

    if (washSiteStore.washSites.length > 0) {
        const queryWashSiteId = Number(route.query.washSiteId)
        const querySiteExists = washSiteStore.washSites.some(
            site => Number(site.washSiteId) === queryWashSiteId
        )

        selectedWashSiteId.value = querySiteExists
            ? queryWashSiteId
            : Number(washSiteStore.washSites[0].washSiteId)
    }
})

function typeLabel(type) {
    const labels = {
        ENTRY: "입구",
        ENTRY_CAMERA: "입구 카메라",
        EXIT: "출구",
        EXIT_CAMERA: "출구 카메라",
        WASH: "세차",
        WASH_CAMERA: "세차 카메라",
        CAMERA: "카메라"
    }

    return labels[type] || displayValue(type)
}

function statusLabel(status) {
    const labels = {
        ACTIVE: "활성",
        INACTIVE: "비활성",
        REMOVED: "삭제됨"
    }

    return labels[status] || displayValue(status)
}

function displayValue(value) {
    return value || "-"
}

function openDetail(deviceId) {
    router.push({
        name: "owner-device-edit",
        params: { deviceId }
    })
}

</script>

<style scoped>
.device-page {
    display: grid;
    gap: 20px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.page-hero,
.site-selector-panel,
.table-card {
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
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

.site-selector-panel select {
    width: 100%;
    height: 39px;
    padding: 0 10px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    color: #172033;
    background: #ffffff;
}

.page-hero {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 24px;
    padding: 30px 34px;
}

.page-hero > div {
    width: 100%;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

.page-hero h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
    letter-spacing: 0;
}

.page-hero p {
    margin: 10px 0 0;
    color: #52637d;
    font-size: 15px;
}

.create-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 8px;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    text-decoration: none;
    cursor: pointer;
    transition: transform 0.18s ease, background 0.18s ease;
}

.create-button {
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 8px 16px rgba(7, 27, 58, 0.14);
}

.create-button {
    position: absolute;
    right: 34px;
    top: 50%;
    transform: translateY(-50%);
}

tbody tr.detail-row {
    cursor: pointer;
    transition: background-color 120ms ease, box-shadow 120ms ease;
}

tbody tr.detail-row:hover,
tbody tr.detail-row:focus-visible {
    background: #eaf2ff;
    outline: none;
    box-shadow: inset 3px 0 0 #2563eb;
}

tbody tr.detail-row:focus-visible {
    box-shadow: inset 3px 0 0 #0b1f3a, inset 0 0 0 1px #0b1f3a;
}

.create-button:hover {
    background: #0f2f5f;
    transform: translateY(-50%);
}

.status-message {
    margin: 0;
    padding: 14px 16px;
    border-radius: 8px;
    color: #1c3d70;
    background: #e8eef8;
    font-weight: 800;
    text-align: center;
}

.status-message.error {
    color: #b42318;
    background: #fff1f1;
}

.table-card {
    overflow: hidden;
}

.section-title {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    padding: 20px 24px;
    border-bottom: 1px solid #e3eaf4;
    text-align: center;
}

.section-title h2 {
    margin: 0;
    color: #071b3a;
    font-size: 22px;
    font-weight: 900;
}

.section-title span {
    position: absolute;
    right: 24px;
    color: #52637d;
    font-size: 14px;
    font-weight: 800;
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    min-width: 720px;
    border-collapse: collapse;
}

th,
td {
    padding: 14px 12px;
    border-bottom: 1px solid #d8e2ef;
    color: #334766;
    font-size: 14px;
    text-align: center;
    white-space: nowrap;
}

th {
    color: #071b3a;
    background: #f5f8fc;
    font-weight: 900;
}

.strong-cell {
    color: #071b3a;
    font-weight: 900;
}

.badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 26px;
    padding: 0 10px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 900;
}

.badge.success {
    color: #1c6b4f;
    background: #e7f7ef;
}

.badge.muted {
    color: #52637d;
    background: #edf2f8;
}

.empty-cell {
    padding: 34px 12px;
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

@media (max-width: 760px) {
    .site-selector-panel {
        grid-template-columns: 1fr;
    }

    .page-hero {
        align-items: stretch;
        flex-direction: column;
        padding: 24px 20px;
    }

    .create-button {
        position: static;
        transform: none;
        width: 100%;
    }

    .create-button:hover {
        transform: none;
    }

    .section-title {
        flex-direction: column;
    }

    .section-title span {
        position: static;
    }
}
</style>
