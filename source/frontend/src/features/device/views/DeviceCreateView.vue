<template>
    <main class="device-create-page">
        <section class="create-card">
            <header class="create-header">
                <p class="eyebrow"></p>
                <h1>장치 등록</h1>
                <p>사업장에 연결할 장치 정보를 입력해 주세요.</p>
            </header>

            <form
                class="create-form"
                @submit.prevent="save"
            >
                <div class="form-grid">
                    <label class="field">
                        <span>사업장</span>
                        <select v-model="form.washSiteId">
                            <option
                                disabled
                                value=""
                            >
                                사업장을 선택하세요
                            </option>

                            <option
                                v-for="site in washSiteStore.washSites"
                                :key="site.washSiteId"
                                :value="site.washSiteId"
                            >
                                {{ site.siteName }}
                            </option>
                        </select>
                    </label>

                    <label class="field">
                        <span>장치명</span>
                        <input
                            v-model="form.deviceName"
                            placeholder="예: 입구 번호판 인식 카메라"
                        >
                    </label>

                    <label class="field">
                        <span>장치 종류</span>
                        <select v-model="form.deviceType">
                            <option
                                disabled
                                value=""
                            >
                                장치 종류를 선택하세요
                            </option>

                            <option value="ENTRY">
                                입구 센서
                            </option>

                            <option value="ENTRY_CAMERA">
                                입구 카메라
                            </option>

                            <option value="EXIT">
                                출구 센서
                            </option>

                            <option value="EXIT_CAMERA">
                                출구 카메라
                            </option>

                            <option value="WASH">
                                세차 센서
                            </option>

                            <option value="WASH_CAMERA">
                                세차 카메라
                            </option>
                        </select>
                    </label>

                    <label class="field">
                        <span>상태</span>
                        <select v-model="form.status">
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
                        등록
                    </button>

                    <button
                        class="secondary-button"
                        type="button"
                        @click="router.back()"
                    >
                        취소
                    </button>
                </div>
            </form>
        </section>
    </main>
</template>

<script setup>
import {
    reactive,
    onMounted
} from "vue"

import {
    useRoute,
    useRouter
} from "vue-router"

import {
    useDeviceStore
} from "../stores/deviceStore"

import {
    useWashSiteStore
} from "@/features/washSite/stores/washSiteStore"

const router =
    useRouter()

const route =
    useRoute()

const deviceStore =
    useDeviceStore()

const washSiteStore =
    useWashSiteStore()

const form = reactive({

    washSiteId: "",

    deviceName: "",

    deviceType: "",

    status: "ACTIVE"

})

onMounted(async () => {

    await washSiteStore.fetchWashSites(
        "OWNER"
    )

    const queryWashSiteId = Number(
        route.query.washSiteId
    )

    if (washSiteStore.washSites.some(
        site => Number(site.washSiteId) === queryWashSiteId
    )) {
        form.washSiteId = queryWashSiteId
    }

})

async function save() {

    if (!form.washSiteId) {

        alert("사업장을 선택해 주세요.")

        return

    }

    if (!form.deviceName.trim()) {

        alert("장치명을 입력해 주세요.")

        return

    }

    if (!form.deviceType) {

        alert("장치 종류를 선택해 주세요.")

        return

    }

    try {

        await deviceStore.createDevice(form)

        alert("장치가 등록되었습니다.")

        router.push({
            name: "owner-device-list",
            query: {
                washSiteId: form.washSiteId
            }
        })

    } catch (e) {

        alert("등록하지 못했습니다.")

    }

}
</script>

<style scoped>
.device-create-page {
    display: flex;
    justify-content: center;
    padding: 52px 24px 80px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.create-card {
    width: min(760px, 100%);
    padding: 34px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 16px 36px rgba(7, 27, 58, 0.08);
}

.create-header {
    margin-bottom: 26px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
}

.create-header h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
    letter-spacing: 0;
}

.create-header p {
    margin: 12px 0 0;
    color: #52637d;
    font-size: 15px;
}

.create-form {
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

input::placeholder {
    color: #8a98ad;
}

input:focus,
select:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.actions {
    display: flex;
    justify-content: center;
    gap: 10px;
}

.primary-button,
.secondary-button {
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

.primary-button:hover,
.secondary-button:hover {
    transform: translateY(-1px);
}

.primary-button:hover {
    background: #0f2f5f;
}

.secondary-button:hover {
    background: #f5f8fc;
}

@media (max-width: 680px) {
    .device-create-page {
        padding: 32px 16px 56px;
    }

    .create-card {
        padding: 26px 20px;
    }

    .form-grid {
        grid-template-columns: 1fr;
    }

    .actions {
        flex-direction: column;
    }

    .primary-button,
    .secondary-button {
        width: 100%;
    }
}
</style>
