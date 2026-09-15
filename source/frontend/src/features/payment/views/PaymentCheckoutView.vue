<template>
    <main class="payment-page">
        <section class="payment-panel">
            <header class="payment-header">
                <p class="eyebrow">PAYMENT</p>
                <h1>결제</h1>
                <p class="payment-help">
                    결제수단을 선택하고 충전을 진행합니다.
                </p>
            </header>

            <p
                v-if="paymentStore.error"
                class="payment-message error"
            >
                {{ paymentStore.error }}
            </p>

            <p
                v-if="paymentNotice"
                class="payment-message notice"
            >
                {{ paymentNotice }}
            </p>

            <form @submit.prevent="requestPayment">
                <fieldset class="provider-field">
                    <legend>결제수단</legend>
                    <div class="provider-options">
                        <label
                            v-for="provider in paymentProviders"
                            :key="provider.code"
                            class="provider-option"
                            :class="{
                                selected: form.paymentProvider === provider.code,
                                disabled: !provider.enabled
                            }"
                        >
                            <input
                                v-model="form.paymentProvider"
                                type="radio"
                                name="paymentProvider"
                                :value="provider.code"
                                :disabled="!provider.enabled"
                            >
                            <span>{{ provider.label }}</span>
                            <small v-if="provider.code !== 'PAYAPP'">테스트</small>
                        </label>
                    </div>
                </fieldset>

                <label for="orderName">
                    <span>상품명</span>
                    <input
                        id="orderName"
                        v-model="form.orderName"
                        required
                    >
                </label>

                <label for="amount">
                    <span>금액</span>
                    <input
                        id="amount"
                        v-model.number="form.amount"
                        type="number"
                        min="1000"
                        step="100"
                        required
                    >
                </label>

                <label for="customerName">
                    <span>구매자명</span>
                    <input
                        id="customerName"
                        v-model="form.customerName"
                    >
                </label>

                <label for="customerEmail">
                    <span>이메일</span>
                    <input
                        id="customerEmail"
                        v-model="form.customerEmail"
                        type="email"
                    >
                </label>

                <fieldset class="phone-field">
                    <legend>휴대전화번호</legend>

                    <div class="phone-number-inputs">
                        <input
                            id="recvPhone"
                            ref="phoneFirstInput"
                            :value="phoneParts.first"
                            type="tel"
                            inputmode="numeric"
                            pattern="[0-9]*"
                            maxlength="3"
                            placeholder="010"
                            aria-label="휴대전화번호 앞자리"
                            required
                            @input="handlePhonePartInput('first', 3, $event, phoneMiddleInput)"
                            @paste="handlePhonePaste"
                        >
                        <span aria-hidden="true">-</span>
                        <input
                            ref="phoneMiddleInput"
                            :value="phoneParts.middle"
                            type="tel"
                            inputmode="numeric"
                            pattern="[0-9]*"
                            maxlength="4"
                            placeholder="1234"
                            aria-label="휴대전화번호 가운데 자리"
                            required
                            @input="handlePhonePartInput('middle', 4, $event, phoneLastInput)"
                            @keydown.backspace="handlePhoneBackspace('middle', phoneFirstInput)"
                        >
                        <span aria-hidden="true">-</span>
                        <input
                            ref="phoneLastInput"
                            :value="phoneParts.last"
                            type="tel"
                            inputmode="numeric"
                            pattern="[0-9]*"
                            maxlength="4"
                            placeholder="5678"
                            aria-label="휴대전화번호 뒷자리"
                            required
                            @input="handlePhonePartInput('last', 4, $event)"
                            @keydown.backspace="handlePhoneBackspace('last', phoneMiddleInput)"
                        >
                    </div>
                </fieldset>

                <div class="payment-actions">
                    <button
                        type="submit"
                        :disabled="paymentStore.loading || !form.paymentProvider"
                    >
                        {{ paymentStore.loading ? "결제 요청 중..." : `${selectedProviderLabel}로 결제` }}
                    </button>
                </div>

                <RouterLink
                    class="history-link"
                    :to="{ name: 'customer-payments' }"
                >
                    결제 내역
                </RouterLink>
            </form>
        </section>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import {
    digitsOnly,
    usePhoneParts
} from "@/shared/composables/usePhoneParts"
import { usePaymentStore } from "../stores/paymentStore"
import * as paymentApi from "../api/paymentApi"
import {
    closePaymentPopup,
    navigatePaymentPopup,
    observePaymentPopupClose,
    openPaymentPopup
} from "../utils/paymentPopup"

const route = useRoute()
const router = useRouter()
const paymentStore = usePaymentStore()
const phoneFirstInput = ref(null)
const phoneMiddleInput = ref(null)
const phoneLastInput = ref(null)
const paymentProviders = ref([])
const paymentNotice = ref("")
let stopPaymentPopupObserver = null

const form = reactive({
    orderName: String(route.query.orderName || "세차 이용권"),
    amount: Number(route.query.amount || 1000),
    washHistoryId: Number(route.query.washHistoryId || 1),
    customerName: localStorage.getItem("userName") || "",
    customerEmail: "",
    recvPhone: String(route.query.recvPhone || ""),
    paymentProvider: "PAYAPP"
})

const selectedProviderLabel = computed(() =>
    paymentProviders.value.find(
        provider => provider.code === form.paymentProvider
    )?.label || "선택한 결제수단"
)

const {
    phoneParts,
    setPhoneParts,
    handlePhonePartInput,
    handlePhoneBackspace,
    handlePhonePaste
} = usePhoneParts(
    value => {
        form.recvPhone = value
    },
    phoneLastInput
)

onMounted(async () => {
    setPhoneParts(form.recvPhone)
    await loadPaymentProviders()
    await loadCheckoutDefault()
})

onBeforeUnmount(() => {
    stopPaymentPopupObserver?.()
})

async function loadPaymentProviders() {
    try {
        paymentProviders.value = await paymentApi.getPaymentProviders()
        const current = paymentProviders.value.find(
            provider => provider.code === form.paymentProvider
                && provider.enabled
        )
        if (!current) {
            form.paymentProvider = paymentProviders.value.find(
                provider => provider.enabled
            )?.code || ""
        }
    } catch (error) {
        paymentStore.error = "결제수단 정보를 불러오지 못했습니다."
    }
}

async function loadCheckoutDefault() {
    if (route.query.washHistoryId) {
        return
    }

    const checkoutDefault = await paymentStore.fetchCheckoutDefault()

    if (!checkoutDefault) {
        return
    }

    form.orderName = checkoutDefault.orderName || form.orderName
    form.amount = Number(checkoutDefault.amount || form.amount)
    form.washHistoryId = Number(checkoutDefault.washHistoryId || form.washHistoryId)
    form.customerName = checkoutDefault.customerName || form.customerName
    form.customerEmail = checkoutDefault.customerEmail || form.customerEmail
    setPhoneParts(checkoutDefault.recvPhone || form.recvPhone)
}

async function requestPayment() {
    if (paymentStore.loading) {
        return
    }

    const recvPhone = digitsOnly(form.recvPhone)

    if (!/^01[016789]\d{7,8}$/.test(recvPhone)) {
        paymentStore.error = "휴대전화번호를 확인해주세요."
        return
    }

    form.recvPhone = recvPhone
    paymentNotice.value = ""

    const paymentPopup = openPaymentPopup()
    if (!paymentPopup) {
        paymentStore.error = "결제창을 열 수 없습니다. 브라우저의 팝업 차단을 해제해주세요."
        return
    }

    const waitingInfo = await paymentStore.waitingPayment(form)

    if (!waitingInfo?.payUrl) {
        closePaymentPopup(paymentPopup)
        if (!paymentStore.error) {
            paymentStore.error = "결제창 주소를 받지 못했습니다."
        }
        return
    }

    if (!navigatePaymentPopup(paymentPopup, waitingInfo.payUrl)) {
        closePaymentPopup(paymentPopup)
        await handlePaymentPopupClosed(waitingInfo.paymentId)
        return
    }

    stopPaymentPopupObserver?.()
    stopPaymentPopupObserver = observePaymentPopupClose(
        paymentPopup,
        () => handlePaymentPopupClosed(waitingInfo.paymentId)
    )
}

async function handlePaymentPopupClosed(paymentId) {
    paymentNotice.value = "결제창 종료를 확인하고 있습니다."
    paymentStore.error = ""

    try {
        const payment = await paymentApi.abandonPrepaidCharge(paymentId)
        const status = String(payment?.status || "").toUpperCase()

        if (status === "SUCCESS" || status === "DONE") {
            await router.push({
                name: "customer-payment-success",
                query: { paymentId }
            })
            return
        }
        if (status === "CANCELED") {
            paymentNotice.value = "결제창이 닫혀 결제 요청이 취소되었습니다."
            return
        }
        if (status === "FAILED") {
            paymentStore.error = "결제 요청이 실패했습니다. 다시 시도해주세요."
            paymentNotice.value = ""
            return
        }

        paymentStore.error = "결제창은 닫혔지만 결제사 확인이 끝나지 않았습니다. 잠시 후 다시 확인해주세요."
        paymentNotice.value = ""
    } catch (error) {
        paymentStore.error = error?.response?.data?.message
            || "결제창 종료 상태를 확인하지 못했습니다."
        paymentNotice.value = ""
    }
}
</script>

<style scoped>
.payment-page {
    min-height: 72vh;
    display: grid;
    place-items: start center;
    padding: 40px 0 16px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.payment-panel {
    width: min(560px, 100%);
    display: grid;
    gap: 18px;
    padding: 34px;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
    box-sizing: border-box;
    text-align: left;
}

.payment-header {
    display: grid;
    gap: 8px;
}

.eyebrow {
    margin: 0;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0;
}

.payment-panel h1 {
    margin: 0;
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
    line-height: 1.2;
    letter-spacing: 0;
}

.payment-help,
.payment-message {
    margin: 0;
    color: #52637d;
    font-size: 14px;
    word-break: keep-all;
}

.payment-message {
    padding: 12px 14px;
    border-radius: 8px;
    font-weight: 800;
}

.payment-message.error {
    color: #b42318;
    background: #fff1f1;
}

.payment-message.notice {
    color: #175c3a;
    background: #edf9f2;
}

form {
    display: grid;
    gap: 15px;
}

label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

.phone-field {
    min-width: 0;
    display: grid;
    gap: 7px;
    margin: 0;
    padding: 0;
    border: 0;
}

.provider-field {
    min-width: 0;
    margin: 0;
    padding: 0;
    border: 0;
}

.provider-field legend {
    margin-bottom: 7px;
    padding: 0;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

.provider-options {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
}

.provider-option {
    min-width: 0;
    min-height: 62px;
    display: grid;
    place-items: center;
    gap: 2px;
    padding: 8px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    background: #ffffff;
    cursor: pointer;
    box-sizing: border-box;
    text-align: center;
}

.provider-option.selected {
    border-color: #071b3a;
    box-shadow: 0 0 0 2px rgba(7, 27, 58, 0.12);
}

.provider-option.disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.provider-option input {
    position: absolute;
    width: 1px;
    height: 1px;
    opacity: 0;
}

.provider-option span {
    overflow-wrap: anywhere;
}

.provider-option small {
    color: #667085;
    font-size: 11px;
}

.phone-field legend {
    margin-bottom: 7px;
    padding: 0;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

.phone-number-inputs {
    display: grid;
    grid-template-columns:
        minmax(0, 3fr) 12px
        minmax(0, 4fr) 12px
        minmax(0, 4fr);
    gap: 6px;
    align-items: center;
}

.phone-number-inputs input {
    color: #071b3a;
    font-size: 15px;
    font-weight: 800;
    text-align: center;
}

.phone-number-inputs span {
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

input {
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

input:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.payment-actions {
    display: grid;
    margin-top: 2px;
}

button {
    min-height: 46px;
    border: 0;
    border-radius: 8px;
    color: #ffffff;
    background: #071b3a;
    font: inherit;
    font-size: 15px;
    font-weight: 900;
    cursor: pointer;
    box-shadow: 0 10px 18px rgba(7, 27, 58, 0.16);
    transition: transform 0.18s ease, background 0.18s ease;
}

button:hover {
    background: #0f2f5f;
    transform: translateY(-1px);
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
    transform: none;
}

.history-link {
    justify-self: start;
    color: #071b3a;
    font-size: 15px;
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 4px;
}

.history-link:hover {
    color: #0f2f5f;
}

@media (max-width: 640px) {
    .payment-page {
        padding-top: 20px;
    }

    .payment-panel {
        padding: 24px 20px;
    }

    .payment-panel h1 {
        font-size: 32px;
    }

    .provider-options {
        grid-template-columns: 1fr;
    }
}
</style>
