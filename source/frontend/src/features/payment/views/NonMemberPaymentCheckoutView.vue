<template>
    <main class="payment-page">
        <section class="payment-panel">
            <header class="payment-header">
                <div>
                    <p class="eyebrow">비회원 결제</p>
                    <h1>결제</h1>
                </div>

                <button
                    type="button"
                    class="secondary-button"
                    @click="goWashHistory"
                >
                    세차 내역
                </button>
            </header>

            <p class="payment-help">
                결제 정보를 확인하고 결제수단을 선택합니다.
            </p>

            <p v-if="loading" class="payment-message">
                결제 대상 정보를 불러오는 중입니다.
            </p>

            <p
                v-if="errorMessage"
                class="payment-message error"
            >
                {{ errorMessage }}
            </p>

            <p
                v-if="paymentNotice"
                class="payment-message notice"
            >
                {{ paymentNotice }}
            </p>

            <form
                v-if="checkoutInfo && !loading"
                @submit.prevent="requestPayment"
            >
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

                <label for="washHistoryId">
                    세차 이용 번호
                    <input
                        id="washHistoryId"
                        :value="checkoutInfo.washHistoryId"
                        readonly
                    >
                </label>

                <label for="orderName">
                    상품명
                    <input
                        id="orderName"
                        v-model="form.orderName"
                        readonly
                    >
                </label>

                <label for="amount">
                    금액
                    <input
                        id="amount"
                        v-model.number="form.amount"
                        type="number"
                        readonly
                    >
                </label>

                <label for="customerName">
                    구매자명
                    <input
                        id="customerName"
                        v-model.trim="form.customerName"
                        required
                    >
                </label>

                <label for="customerEmail">
                    이메일
                    <input
                        id="customerEmail"
                        v-model.trim="form.customerEmail"
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
                        :disabled="submitting || !form.paymentProvider"
                    >
                        {{ submitting ? "결제 요청 중..." : `${selectedProviderLabel}로 결제` }}
                    </button>
                </div>

                <RouterLink
                    :to="{
                        name: 'non-member-payment-list',
                        params: {
                            washHistoryId: String(checkoutInfo.washHistoryId)
                        },
                        query: plateQuery(checkoutInfo.customerId)
                    }"
                >
                    결제 정보 보기
                </RouterLink>
            </form>
        </section>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { usePhoneParts } from "@/shared/composables/usePhoneParts"
import * as paymentApi from "../api/paymentApi"
import {
    closePaymentPopup,
    navigatePaymentPopup,
    observePaymentPopupClose,
    openPaymentPopup
} from "../utils/paymentPopup"

const route = useRoute()
const router = useRouter()

const checkoutInfo = ref(null)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref("")
const paymentNotice = ref("")
const phoneFirstInput = ref(null)
const phoneMiddleInput = ref(null)
const phoneLastInput = ref(null)
const paymentProviders = ref([])
let stopPaymentPopupObserver = null

const form = reactive({
    orderName: "",
    amount: 0,
    customerName: "",
    customerEmail: "",
    recvPhone: "",
    paymentProvider: "PAYAPP"
})

onBeforeUnmount(() => {
    stopPaymentPopupObserver?.()
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

function getWashHistoryId() {
    const washHistoryId = Number(route.params.washHistoryId)

    if (!Number.isInteger(washHistoryId) || washHistoryId <= 0) {
        return null
    }

    return washHistoryId
}

function normalizePhone(value) {
    return String(value ?? "")
        .replaceAll(" ", "")
        .replaceAll("-", "")
        .trim()
}

async function loadCheckoutDefault() {
    const washHistoryId = getWashHistoryId()

    errorMessage.value = ""
    checkoutInfo.value = null

    if (washHistoryId === null) {
        errorMessage.value = "올바르지 않은 세차 이용 번호입니다."
        return
    }

    loading.value = true

    try {
        const result = await paymentApi.getNonMemberCheckoutDefault(
            washHistoryId
        )

        checkoutInfo.value = result

        form.orderName = result?.orderName || "세차 이용권"
        form.amount = Number(result?.amount || 0)
        form.customerName = result?.customerName || ""
        form.customerEmail = result?.customerEmail || ""
        setPhoneParts(result?.recvPhone || "")
    } catch (error) {
        console.error("비회원 결제 대상 조회 실패:", error)

        if (error.response?.status === 403) {
            errorMessage.value = "비회원 결제 조회 API 접근이 거부되었습니다."
        } else if (error.response?.status === 404) {
            errorMessage.value = "결제 가능한 세차 내역을 찾을 수 없습니다."
        } else {
            errorMessage.value =
                error.response?.data?.message
                || "결제 대상 정보를 불러오지 못했습니다."
        }
    } finally {
        loading.value = false
    }
}

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
        errorMessage.value = "결제수단 정보를 불러오지 못했습니다."
    }
}

async function requestPayment() {
    if (submitting.value || !checkoutInfo.value) {
        return
    }

    const recvPhone = normalizePhone(form.recvPhone)

    if (!form.customerName) {
        errorMessage.value = "구매자명을 입력해주세요."
        return
    }

    if (!/^01[016789]\d{7,8}$/.test(recvPhone)) {
        errorMessage.value = "휴대전화번호를 확인해주세요."
        return
    }

    errorMessage.value = ""
    paymentNotice.value = ""
    submitting.value = true

    const paymentPopup = openPaymentPopup()
    if (!paymentPopup) {
        errorMessage.value = "결제창을 열 수 없습니다. 브라우저의 팝업 차단을 해제해주세요."
        submitting.value = false
        return
    }

    try {
        const result = await paymentApi.waitingNonMemberPayment({
            washHistoryId: checkoutInfo.value.washHistoryId,
            orderName: form.orderName,
            amount: Number(form.amount),
            customerName: form.customerName,
            customerEmail: form.customerEmail,
            recvPhone,
            paymentProvider: form.paymentProvider
        })

        if (!result?.payUrl) {
            throw new Error("결제창 주소를 받지 못했습니다.")
        }

        if (!navigatePaymentPopup(paymentPopup, result.payUrl)) {
            throw new Error("결제창이 닫혔습니다.")
        }

        stopPaymentPopupObserver?.()
        stopPaymentPopupObserver = observePaymentPopupClose(
            paymentPopup,
            () => handlePaymentPopupClosed(result.paymentId)
        )
    } catch (error) {
        closePaymentPopup(paymentPopup)
        console.error("비회원 결제 요청 실패:", error)
        errorMessage.value =
            error.response?.data?.message
            || error.message
            || "결제 요청에 실패했습니다."
    } finally {
        submitting.value = false
    }
}

async function handlePaymentPopupClosed(paymentId) {
    paymentNotice.value = "결제창 종료를 확인하고 있습니다."
    errorMessage.value = ""

    try {
        const payment = await paymentApi.abandonNonMemberPayment(
            paymentId,
            checkoutInfo.value?.washHistoryId
        )
        const status = String(payment?.status || "").toUpperCase()

        if (status === "SUCCESS" || status === "DONE") {
            await router.push({
                name: "non-member-payment-list",
                params: {
                    washHistoryId: String(checkoutInfo.value.washHistoryId)
                },
                query: {
                    ...plateQuery(checkoutInfo.value.customerId),
                    paymentId,
                    result: "success"
                }
            })
            return
        }
        if (status === "CANCELED") {
            paymentNotice.value = "결제창이 닫혀 결제 요청이 취소되었습니다."
            return
        }
        if (status === "FAILED") {
            errorMessage.value = "결제 요청이 실패했습니다. 다시 시도해주세요."
            paymentNotice.value = ""
            return
        }

        errorMessage.value = "결제창은 닫혔지만 결제사 확인이 끝나지 않았습니다. 잠시 후 다시 확인해주세요."
        paymentNotice.value = ""
    } catch (error) {
        errorMessage.value = error?.response?.data?.message
            || "결제창 종료 상태를 확인하지 못했습니다."
        paymentNotice.value = ""
    }
}

function goWashHistory() {
    const query = plateQuery(
        checkoutInfo.value?.customerId
    )

    if (!query.plateLast4) {
        errorMessage.value =
            "세차 내역을 조회할 차량번호가 없습니다."
        return
    }

    router.push({
        name: "non-member-wash-history",
        query
    })
}

function plateQuery(value) {
    const plateNumber = String(value || "")
    const plateLast4 =
        plateNumber.match(/(\d{4})$/)?.[1]
        || ""

    return {
        plateLast4,
        plateNumber
    }
}

onMounted(async () => {
    await loadPaymentProviders()
    await loadCheckoutDefault()
})
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
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.payment-header h1,
.eyebrow,
.payment-help,
.payment-message {
    margin: 0;
}

.payment-header h1 {
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
    line-height: 1.2;
    letter-spacing: 0;
}

.eyebrow {
    margin-bottom: 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0;
}

.payment-help,
.payment-message {
    font-size: 14px;
    color: #52637d;
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
    gap: 14px;
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
    width: 100%;
    box-sizing: border-box;
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

input[readonly] {
    color: #52637d;
    background: #f7f9fc;
    opacity: 1;
    -webkit-text-fill-color: #52637d;
}

input::placeholder {
    color: #8b98aa;
    opacity: 1;
}

input:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.payment-actions {
    display: grid;
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

.secondary-button {
    padding: 0 16px;
    color: #071b3a;
    background: #ffffff;
    border: 1px solid #cbd6e6;
    box-shadow: none;
}

.secondary-button:hover {
    background: #f4f7fb;
}

button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
}

form > a {
    justify-self: start;
    color: #071b3a;
    font-size: 15px;
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 4px;
}

form > a:hover {
    color: #0f2f5f;
}

@media (max-width: 640px) {
    .payment-page {
        padding: 20px 16px 16px;
    }

    .payment-panel {
        padding: 24px 20px;
    }

    .payment-header {
        align-items: flex-start;
    }

    .payment-header h1 {
        font-size: 32px;
    }

    .provider-options {
        grid-template-columns: 1fr;
    }
}
</style>
