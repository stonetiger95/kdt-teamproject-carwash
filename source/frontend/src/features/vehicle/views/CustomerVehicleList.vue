<template>
    <main class="vehicle-list-page">
        <section class="vehicle-header">
            <div>
                <p class="eyebrow">MY VEHICLES</p>
                <h1>내 차량 목록</h1>
                <p class="page-copy">
                    등록된 차량 정보를 확인하고 관리할 수 있습니다.
                </p>
            </div>

            <RouterLink
                class="primary-link"
                to="/customer/vehicles/registration"
            >
                차량 등록
            </RouterLink>
        </section>

        <p
            v-if="vehicleStore.loading"
            class="status-message"
        >
            차량 정보를 불러오는 중입니다.
        </p>

        <p
            v-if="vehicleStore.errorMessage"
            class="status-message error"
        >
            {{ vehicleStore.errorMessage }}
        </p>

        <section
            v-if="vehicleStore.vehicles.length > 0"
            class="vehicle-grid"
        >
            <article
                v-for="vehicle in vehicleStore.vehicles"
                :key="vehicle.vehicleId || vehicle.plateNumber"
                class="vehicle-card"
            >
                <div class="vehicle-icon">
                    {{ getVehicleInitial(vehicle.vehicleType) }}
                </div>

                <div class="vehicle-info">
                    <span class="info-label">차량번호</span>
                    <strong>{{ displayValue(vehicle.plateNumber) }}</strong>
                </div>

                <dl>
                    <div>
                        <dt>차량종류</dt>
                        <dd>{{ displayVehicleType(vehicle.vehicleType) }}</dd>
                    </div>
                    <div>
                        <dt>이용 상태</dt>
                        <dd :class="getUsageStatusClass(vehicle.usageStatus)">
                            {{ displayUsageStatus(vehicle.usageStatus) }}
                        </dd>
                    </div>
                    <div>
                        <dt>최근 세차일</dt>
                        <dd>{{ formatLastWashAt(vehicle.lastWashAt) }}</dd>
                    </div>
                </dl>

                <button
                    class="delete-button"
                    type="button"
                    :disabled="
                        vehicleStore.loading
                        || vehicle.usageStatus !== 'ACTIVE'
                    "
                    :title="getDeleteButtonTitle(vehicle.usageStatus)"
                    @click="deleteVehicle(vehicle)"
                >
                    {{ vehicle.usageStatus === "ACTIVE" ? "삭제" : "삭제 불가" }}
                </button>
            </article>
        </section>

        <section
            v-else-if="!vehicleStore.loading"
            class="empty-state"
        >
            <strong>등록된 차량이 없습니다.</strong>
            <p>차량을 등록하면 이곳에서 차량번호와 차종을 확인할 수 있습니다.</p>
            <RouterLink
                class="primary-link"
                to="/customer/vehicles/registration"
            >
                차량 등록하기
            </RouterLink>
        </section>
    </main>
</template>

<script setup>
import { onMounted } from "vue"
import { useVehicleStore } from "../stores/vehicleStore"

const vehicleStore = useVehicleStore()

const vehicleTypeLabels = {
    SEDAN: "세단",
    SUV: "SUV",
    TRUCK: "트럭",
    HATCHBACK: "해치백",
    VAN: "승합차"
}

const usageStatusLabels = {
    ACTIVE: "이용 가능",
    SUSPENDED: "계정 이용 정지",
    RESTRICTED: "차량 이용 제한"
}

onMounted(() => {
    vehicleStore.vehicleList()
})

async function deleteVehicle(vehicle) {
    if (vehicle.usageStatus !== "ACTIVE") {
        window.alert("이용 제한 차량은 제한 해제 후 삭제할 수 있습니다.")
        return
    }

    if (!window.confirm(`${displayValue(vehicle.plateNumber)} 차량을 삭제할까요?`)) {
        return
    }

    await vehicleStore.vehicleDelete(vehicle.vehicleId)
}

function displayVehicleType(value) {
    return vehicleTypeLabels[value] || displayValue(value)
}

function displayUsageStatus(value) {
    return usageStatusLabels[value] || "상태 확인 필요"
}

function getUsageStatusClass(value) {
    return {
        "usage-status": true,
        "usage-status-active": value === "ACTIVE",
        "usage-status-restricted": value !== "ACTIVE"
    }
}

function getDeleteButtonTitle(value) {
    return value === "ACTIVE"
        ? "차량 삭제"
        : "이용 제한 해제 후 삭제할 수 있습니다."
}

function formatLastWashAt(value) {
    if (!value) {
        return "이용 내역 없음"
    }

    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
        return "이용 내역 없음"
    }

    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date)
}

function getVehicleInitial(value) {
    const label = displayVehicleType(value)
    return label === "-" ? "차" : label.slice(0, 1)
}

function displayValue(value) {
    return value || "-"
}
</script>

<style scoped>
.vehicle-list-page {
    display: grid;
    gap: 20px;
    padding: 32px 0 8px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.vehicle-header,
.vehicle-card,
.empty-state {
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
}

.vehicle-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    position: relative;
    padding: 30px 34px;
}

.vehicle-header > div {
    width: 100%;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0;
}

.vehicle-header h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
    line-height: 1.2;
    letter-spacing: 0;
}

.page-copy {
    margin-top: 10px;
    color: #52637d;
    font-size: 15px;
}

.primary-link,
.delete-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 42px;
    padding: 0 18px;
    border: 1px solid transparent;
    border-radius: 8px;
    color: #ffffff;
    background: #071b3a;
    font: inherit;
    font-size: 15px;
    font-weight: 900;
    text-decoration: none;
    cursor: pointer;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
    transition: transform 0.18s ease, background 0.18s ease;
}

.vehicle-header > .primary-link {
    position: absolute;
    right: 34px;
    top: 50%;
    transform: translateY(-50%);
}

.vehicle-header > .primary-link:hover {
    transform: translateY(-50%);
}

.primary-link:hover,
.delete-button:hover {
    background: #0f2f5f;
    transform: translateY(-1px);
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

.vehicle-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

.vehicle-card {
    display: grid;
    grid-template-columns: auto 1fr auto;
    gap: 16px;
    align-items: center;
    padding: 22px;
}

.vehicle-icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    border-radius: 8px;
    color: #ffffff;
    background: #071b3a;
    font-size: 22px;
    font-weight: 900;
    box-shadow: 0 10px 20px rgba(7, 27, 58, 0.18);
}

.vehicle-info {
    display: grid;
    gap: 4px;
}

.info-label {
    color: #52637d;
    font-size: 13px;
    font-weight: 800;
}

.vehicle-info strong {
    color: #071b3a;
    font-size: 24px;
    font-weight: 900;
}

dl {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin: 4px 0 0;
}

dl div {
    padding: 12px;
    border-radius: 8px;
    background: #f5f8fc;
}

dt,
dd {
    margin: 0;
}

.usage-status {
    display: inline-flex;
    align-items: center;
    width: fit-content;
    padding: 4px 9px;
    border-radius: 999px;
    font-size: 13px;
}

.usage-status-active {
    color: #087443;
    background: #e8f7ef;
}

.usage-status-restricted {
    color: #b42318;
    background: #fff1f1;
}

dt {
    color: #52637d;
    font-size: 13px;
    font-weight: 800;
}

dd {
    margin-top: 4px;
    color: #071b3a;
    font-weight: 900;
}

.delete-button {
    grid-column: 3;
    grid-row: 1;
    min-height: 38px;
    padding: 0 14px;
    color: #b42318;
    background: #ffffff;
    border-color: #f0c8c4;
    box-shadow: none;
}

.delete-button:hover {
    color: #ffffff;
    background: #b42318;
}

.delete-button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
    transform: none;
}

.empty-state {
    display: grid;
    justify-items: center;
    gap: 12px;
    padding: 46px 24px;
    text-align: center;
}

.empty-state strong {
    color: #071b3a;
    font-size: 22px;
    font-weight: 900;
}

.empty-state p {
    color: #52637d;
    font-size: 15px;
}

@media (max-width: 820px) {
    .vehicle-header {
        align-items: stretch;
        flex-direction: column;
        padding: 24px 20px;
    }

    .vehicle-header > .primary-link {
        position: static;
        transform: none;
    }

    .vehicle-header > .primary-link:hover {
        transform: none;
    }

    .primary-link {
        width: 100%;
    }

    .vehicle-grid {
        grid-template-columns: 1fr;
    }
}

@media (max-width: 560px) {
    .vehicle-card {
        grid-template-columns: 1fr;
    }

    .vehicle-icon {
        width: 48px;
        height: 48px;
    }

    .delete-button {
        grid-column: auto;
        grid-row: auto;
        width: 100%;
    }

    dl {
        grid-template-columns: 1fr;
    }
}
</style>
