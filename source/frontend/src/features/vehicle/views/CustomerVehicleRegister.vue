<template>
    <main class="vehicle-register-page">
        <section class="register-card">
            <header class="register-header">
                <p class="eyebrow">VEHICLE REGISTER</p>
                <h1>차량 등록</h1>
                <p>차량번호와 차량종류를 입력해 내 차량을 등록하세요.</p>
            </header>

            <form
                class="register-form"
                @submit.prevent="submitVehicle"
            >
                <label class="form-field">
                    <span>차량번호</span>
                    <input
                        v-model="vehicleStore.plateNumber"
                        placeholder="예: 127가3456"
                    >
                </label>

                <label class="form-field">
                    <span>차량종류</span>
                    <select v-model="vehicleStore.vehicleType">
                        <option value="">차량종류 선택</option>
                        <option
                            v-for="option in vehicleStore.vehicleTypeOptions"
                            :key="option.value"
                            :value="option.value"
                        >
                            {{ option.label }}
                        </option>
                    </select>
                </label>

                <p
                    v-if="vehicleStore.errorMessage"
                    class="error-message"
                >
                    {{ vehicleStore.errorMessage }}
                </p>

                <div class="form-actions">
                    <button
                        class="submit-button"
                        type="submit"
                        :disabled="vehicleStore.loading"
                    >
                        {{ vehicleStore.loading ? "등록 중..." : "차량 등록" }}
                    </button>

                    <button
                        class="secondary-button"
                        type="button"
                        :disabled="vehicleStore.loading"
                        @click="goToDashboard"
                    >
                        고객 페이지로
                    </button>
                </div>
            </form>
        </section>
    </main>
</template>

<script setup>
import { useRouter } from "vue-router"
import { useVehicleStore } from "../stores/vehicleStore"

const router = useRouter()
const vehicleStore = useVehicleStore()

async function submitVehicle() {
    const success = await vehicleStore.vehicleRegister()

    if (success) {
        await router.replace({
            name: "customer-vehicles"
        })
    }
}

async function goToDashboard() {
    await router.replace({
        name: "customer-dashboard"
    })
}
</script>

<style scoped>
.vehicle-register-page {
    min-height: 72vh;
    display: grid;
    place-items: start center;
    padding: 44px 20px 24px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.register-card {
    width: min(560px, 100%);
    display: grid;
    gap: 24px;
    padding: 38px 34px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
    box-sizing: border-box;
}

.register-header {
    display: grid;
    gap: 8px;
    text-align: center;
}

.eyebrow {
    margin: 0;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0;
}

.register-header h1 {
    margin: 0;
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
    line-height: 1.2;
    letter-spacing: 0;
}

.register-header p {
    margin: 0;
    color: #52637d;
    font-size: 14px;
    word-break: keep-all;
}

.register-form {
    display: grid;
    gap: 15px;
}

.form-field {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

input,
select {
    width: 100%;
    min-height: 46px;
    padding: 0 13px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
    outline: none;
}

input:focus,
select:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.error-message {
    margin: 0;
    padding: 12px 14px;
    border-radius: 8px;
    color: #b42318;
    background: #fff1f1;
    font-size: 14px;
    font-weight: 800;
    text-align: center;
}

.form-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}

.submit-button,
.secondary-button {
    min-height: 46px;
    border-radius: 8px;
    font: inherit;
    font-size: 15px;
    font-weight: 900;
    cursor: pointer;
    transition: transform 0.18s ease, background 0.18s ease;
}

.submit-button {
    border: 0;
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
}

.submit-button:hover {
    background: #0f2f5f;
    transform: translateY(-1px);
}

.secondary-button {
    color: #071b3a;
    background: #ffffff;
    border: 1px solid #cbd6e6;
}

.secondary-button:hover {
    background: #f5f8fc;
}

.submit-button:disabled,
.secondary-button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
    transform: none;
}

@media (max-width: 560px) {
    .vehicle-register-page {
        padding-top: 28px;
    }

    .register-card {
        padding: 28px 20px;
    }

    .register-header h1 {
        font-size: 32px;
    }

    .form-actions {
        grid-template-columns: 1fr;
    }
}
</style>
