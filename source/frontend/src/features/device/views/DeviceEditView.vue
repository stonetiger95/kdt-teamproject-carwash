<template>
    <main class="device-detail-page">
        <section class="detail-card">
            <header class="detail-header">
                <p class="eyebrow"></p>
                <h1>장치 상세</h1>
                <p>등록된 장치 정보를 확인하고 상태를 관리합니다.</p>
            </header>

            <p
                v-if="loading"
                class="status-message"
            >
                불러오는 중입니다.
            </p>

            <p
                v-if="error"
                class="status-message error"
            >
                {{ error }}
            </p>

            <form
                v-if="!loading"
                class="detail-form"
                @submit.prevent="save"
            >
                <div class="form-grid">
                    <label class="field">
                        <span>장치 ID</span>
                        <input
                            :value="deviceId"
                            readonly
                        >
                    </label>

                    <label class="field">
                        <span>장치 종류</span>
                        <input
                            :value="typeLabel(device.deviceType)"
                            readonly
                        >
                    </label>

                    <label class="field">
                        <span>장치명</span>
                        <input v-model="device.deviceName">
                    </label>

                    <label class="field">
                        <span>상태</span>
                        <select v-model="device.status">
                            <option value="ACTIVE">
                                활성
                            </option>

                            <option value="INACTIVE">
                                비활성
                            </option>
                        </select>
                    </label>
                </div>

                <div class="actions">
                    <button
                        class="primary-button"
                        type="submit"
                    >
                        저장
                    </button>

                    <button
                        class="secondary-button"
                        type="button"
                        @click="router.back()"
                    >
                        목록으로
                    </button>

                    <button
                        class="danger-button"
                        type="button"
                        :disabled="loading"
                        @click="remove"
                    >
                        삭제
                    </button>
                </div>
            </form>
        </section>
    </main>
</template>

<script setup>
import { reactive, ref, onMounted } from "vue"
import { useRoute, useRouter } from "vue-router"

import * as deviceApi
    from "../api/deviceApi"

const route = useRoute()
const router = useRouter()

const deviceId = route.params.deviceId

const loading = ref(false)
const error = ref("")

const device = reactive({
    deviceName: "",
    deviceType: "",
    status: ""
})

onMounted(load)

async function load() {

    loading.value = true

    try {

        const res =
            await deviceApi.getDevice(deviceId)

        device.deviceName = res.deviceName
        device.deviceType = res.deviceType
        device.status = res.status

    } catch {

        error.value =
            "장치 정보를 불러오지 못했습니다."

    } finally {

        loading.value = false

    }

}

async function save() {

    loading.value = true

    try {

        await deviceApi.updateDevice(
            deviceId,
            {
                deviceName: device.deviceName,
                status: device.status
            }
        )

        alert("저장되었습니다.")

        router.push({
            name: "owner-device-list"
        })

    } catch {

        error.value =
            "저장하지 못했습니다."

    } finally {

        loading.value = false

    }

}

async function remove() {
    if (!window.confirm("이 장치를 삭제할까요?")) {
        return
    }

    loading.value = true
    error.value = ""

    try {
        await deviceApi.removeDevice(deviceId)
        alert("장치가 삭제되었습니다.")
        await router.push({
            name: "owner-device-list"
        })
    } catch {
        error.value = "장치를 삭제하지 못했습니다."
    } finally {
        loading.value = false
    }
}

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

    return labels[type] || type || "-"
}
</script>

<style scoped>
.device-detail-page {
    display: flex;
    justify-content: center;
    padding: 52px 24px 80px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.detail-card {
    width: min(760px, 100%);
    padding: 34px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 16px 36px rgba(7, 27, 58, 0.08);
}

.detail-header {
    margin-bottom: 26px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

.detail-header h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
    letter-spacing: 0;
}

.detail-header p {
    margin: 12px 0 0;
    color: #52637d;
    font-size: 15px;
}

.detail-form {
    display: grid;
    gap: 24px;
}

.form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 42px;
    row-gap: 20px;
}

.field {
    display: grid;
    gap: 8px;
}

.field span {
    color: #071b3a;
    font-size: 14px;
    font-weight: 900;
}

input,
select {
    width: 100%;
    min-height: 46px;
    padding: 0 14px;
    border: 1px solid #cbd8ea;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    font-size: 14px;
    outline: none;
    transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

input:focus,
select:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

input[readonly] {
    color: #52637d;
    background: #f5f8fc;
}

.actions {
    display: flex;
    justify-content: center;
    gap: 10px;
}

.primary-button,
.secondary-button,
.danger-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 132px;
    min-height: 44px;
    padding: 0 18px;
    border-radius: 8px;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
    transition: transform 0.18s ease, background 0.18s ease;
}

.primary-button {
    color: #ffffff;
    border: 1px solid #071b3a;
    background: #071b3a;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
}

.secondary-button {
    color: #071b3a;
    border: 1px solid #cbd8ea;
    background: #ffffff;
}

.danger-button {
    color: #b42318;
    border: 1px solid #efc7c2;
    background: #ffffff;
}

.primary-button:hover,
.secondary-button:hover,
.danger-button:hover {
    transform: translateY(-1px);
}

.primary-button:hover {
    background: #0f2f5f;
}

.secondary-button:hover {
    background: #f5f8fc;
}

.danger-button:hover {
    color: #ffffff;
    background: #b42318;
}

.danger-button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
    transform: none;
}

.status-message {
    margin: 0 0 18px;
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

@media (max-width: 680px) {
    .device-detail-page {
        padding: 32px 16px 56px;
    }

    .detail-card {
        padding: 26px 20px;
    }

    .form-grid {
        grid-template-columns: 1fr;
    }

    .actions {
        flex-direction: column;
    }

    .primary-button,
    .secondary-button,
    .danger-button {
        width: 100%;
    }
}
</style>
