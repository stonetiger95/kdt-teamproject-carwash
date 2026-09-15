<template>
    <main class="billing-card-page">
        <header class="page-heading">
            <div>
                <p class="page-kicker">AUTO PAYMENT</p>
                <h1>자동결제 카드</h1>
                <p>자동 충전에 사용할 카드를 등록하고 관리합니다.</p>
            </div>
            <RouterLink
                class="back-link"
                :to="{ name: 'customer-dashboard' }"
            >
                대시보드로
            </RouterLink>
        </header>

        <section class="billing-card-manager" aria-labelledby="billing-card-title">
            <header class="manager-header">
                <div>
                    <h2 id="billing-card-title">등록 카드</h2>
                    <p>지갑 잔액이 부족할 때 기본카드로 자동 충전됩니다.</p>
                </div>

                <div class="billing-mode-selector" role="group" aria-label="카드 등록 방식">
                    <button
                        type="button"
                        :class="{ selected: billingMode === 'REAL' }"
                        :aria-pressed="billingMode === 'REAL'"
                        :disabled="paymentStore.loading"
                        @click="billingMode = 'REAL'"
                    >
                        실제카드
                    </button>
                    <button
                        type="button"
                        :class="{ selected: billingMode === 'TEST' }"
                        :aria-pressed="billingMode === 'TEST'"
                        :disabled="paymentStore.loading"
                        @click="billingMode = 'TEST'"
                    >
                        테스트카드
                    </button>
                </div>
            </header>

            <p v-if="cardListLoading" class="page-state">
                등록된 카드 정보를 불러오는 중입니다.
            </p>

            <div v-else-if="billingCards.length > 0" class="billing-card-list">
                <article
                    v-for="card in billingCards"
                    :key="card.payAppCardId"
                    class="billing-card"
                >
                    <div class="billing-card-main">
                        <strong>{{ card.cardName || "등록카드" }}</strong>
                        <span>{{ card.cardNoMasked || "카드번호 비공개" }}</span>
                    </div>
                    <div class="billing-card-meta">
                        <span v-if="card.defaultCard">기본카드</span>
                        <span>{{ card.paymentProvider }}</span>
                        <span v-if="card.autoPayEnabled">자동결제 사용</span>
                        <span v-if="card.maxAutoPayAmount">
                            한도 {{ formatAmount(card.maxAutoPayAmount) }}
                        </span>
                    </div>
                    <button
                        class="delete-button"
                        type="button"
                        :disabled="paymentStore.loading"
                        @click="removeBillingCard(card.payAppCardId)"
                    >
                        삭제
                    </button>
                </article>
            </div>

            <p v-else class="empty-text">등록된 자동결제 카드가 없습니다.</p>

            <div class="form-divider" aria-hidden="true" />

            <header class="form-heading">
                <h2>{{ billingMode === "REAL" ? "카드 등록" : "테스트카드 등록" }}</h2>
                <p>
                    {{ billingMode === "REAL"
                        ? "카드 정보를 입력해 PayApp 자동결제 카드를 등록합니다."
                        : "개발 환경에서 실제 결제 없이 자동결제를 확인합니다." }}
                </p>
            </header>

            <form class="billing-form" @submit.prevent="submitBillingCard">
                <template v-if="billingMode === 'REAL'">
                    <label>
                        <span>카드번호</span>
                        <input
                            v-model.trim="billingForm.cardNo"
                            inputmode="numeric"
                            autocomplete="cc-number"
                            type="text"
                            placeholder="카드번호를 입력하세요"
                            required
                        >
                    </label>
                    <label>
                        <span>카드 명의자 이름</span>
                        <input
                            v-model.trim="billingForm.buyerName"
                            autocomplete="cc-name"
                            type="text"
                            placeholder="카드 명의자 이름"
                            required
                        >
                    </label>
                    <label>
                        <span>생년월일 또는 사업자번호</span>
                        <input
                            v-model.trim="billingForm.buyerAuthNo"
                            inputmode="numeric"
                            type="text"
                            placeholder="생년월일 6자리 또는 사업자번호 10자리"
                            required
                        >
                    </label>
                    <label>
                        <span>유효기간 월</span>
                        <input
                            v-model.trim="billingForm.expMonth"
                            inputmode="numeric"
                            autocomplete="cc-exp-month"
                            maxlength="2"
                            type="text"
                            placeholder="MM"
                            required
                        >
                    </label>
                    <label>
                        <span>유효기간 년</span>
                        <input
                            v-model.trim="billingForm.expYear"
                            inputmode="numeric"
                            autocomplete="cc-exp-year"
                            maxlength="2"
                            type="text"
                            placeholder="YY"
                            required
                        >
                    </label>
                    <label>
                        <span>카드 비밀번호 앞 2자리</span>
                        <input
                            v-model.trim="billingForm.cardPw"
                            inputmode="numeric"
                            autocomplete="off"
                            maxlength="2"
                            type="password"
                            placeholder="앞 2자리"
                            required
                        >
                    </label>
                    <label>
                        <span>결제 안내 휴대폰번호</span>
                        <input
                            v-model.trim="billingForm.recvPhone"
                            inputmode="tel"
                            autocomplete="tel"
                            type="tel"
                            placeholder="01012345678"
                        >
                    </label>
                </template>

                <template v-else>
                    <label>
                        <span>테스트카드 이름</span>
                        <input
                            v-model.trim="billingForm.testCardName"
                            type="text"
                            maxlength="50"
                            placeholder="테스트카드"
                        >
                    </label>
                    <label>
                        <span>표시용 끝 4자리</span>
                        <input
                            v-model.trim="billingForm.testCardLast4"
                            inputmode="numeric"
                            type="text"
                            maxlength="4"
                            pattern="[0-9]{4}"
                            placeholder="0000"
                            required
                        >
                    </label>
                </template>

                <label>
                    <span>자동결제 한도</span>
                    <input
                        v-model.number="billingForm.maxAutoPayAmount"
                        min="1000"
                        step="1000"
                        type="number"
                    >
                </label>

                <div class="billing-options">
                    <label>
                        <input v-model="billingForm.defaultCard" type="checkbox">
                        기본카드
                    </label>
                    <label>
                        <input v-model="billingForm.autoPayEnabled" type="checkbox">
                        자동결제 사용
                    </label>
                </div>

                <button
                    class="submit-button"
                    type="submit"
                    :disabled="paymentStore.loading"
                >
                    {{ billingMode === "REAL" ? "PayApp 카드 등록" : "테스트카드 등록" }}
                </button>
            </form>

            <p v-if="billingMessage" class="form-message">{{ billingMessage }}</p>
            <p v-if="paymentStore.error" class="form-message error">
                {{ paymentStore.error }}
            </p>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue"
import { useRoute } from "vue-router"
import { usePaymentStore } from "@/features/payment/stores/paymentStore"

const paymentStore = usePaymentStore()
const route = useRoute()
const billingMode = ref("REAL")
const billingMessage = ref("")
const cardListLoading = ref(false)
const billingForm = ref(createInitialBillingForm())
const billingCards = computed(() => paymentStore.billingCards || [])

onMounted(async () => {
    cardListLoading.value = true
    await paymentStore.fetchBillingCards({ silent: true })
    cardListLoading.value = false

    if (route.query.billingCard === "success") {
        billingMessage.value = "PayApp 카드가 등록되었습니다."
    }

    if (route.query.billingCard === "fail") {
        billingMessage.value = "PayApp 카드 등록에 실패했습니다."
    }
})

async function submitBillingCard() {
    billingMessage.value = ""

    if (billingMode.value === "TEST") {
        await registerTestBillingCard()
        return
    }

    const authRequest = await paymentStore.createPayAppBillingAuthRequest({
        cardNo: billingForm.value.cardNo,
        expMonth: billingForm.value.expMonth,
        expYear: billingForm.value.expYear,
        buyerAuthNo: billingForm.value.buyerAuthNo,
        cardPw: billingForm.value.cardPw,
        buyerName: billingForm.value.buyerName,
        recvPhone: billingForm.value.recvPhone,
        maxAutoPayAmount: toPositiveAmount(billingForm.value.maxAutoPayAmount),
        defaultCard: billingForm.value.defaultCard,
        autoPayEnabled: billingForm.value.autoPayEnabled
    })

    if (authRequest?.status === "SUCCESS") {
        resetBillingForm()
        await paymentStore.fetchBillingCards({ silent: true })
        billingMessage.value = authRequest.message || "카드가 등록되었습니다."
    }
}

async function registerTestBillingCard() {
    const billingCard = await paymentStore.registerTestBillingCard({
        cardName: billingForm.value.testCardName,
        cardLast4: billingForm.value.testCardLast4,
        maxAutoPayAmount: toPositiveAmount(billingForm.value.maxAutoPayAmount),
        defaultCard: billingForm.value.defaultCard,
        autoPayEnabled: billingForm.value.autoPayEnabled
    })

    if (billingCard) {
        resetBillingForm()
        billingMessage.value = "테스트카드가 등록되었습니다. 실제 결제는 진행되지 않습니다."
    }
}

async function removeBillingCard(payAppCardId) {
    if (!window.confirm("등록된 카드를 삭제할까요?")) {
        return
    }

    const deleted = await paymentStore.deleteBillingCard(payAppCardId)
    if (deleted) {
        billingMessage.value = "카드가 삭제되었습니다."
    }
}

function createInitialBillingForm() {
    return {
        cardNo: "",
        expMonth: "",
        expYear: "",
        buyerAuthNo: "",
        cardPw: "",
        buyerName: "",
        recvPhone: "",
        testCardName: "테스트카드",
        testCardLast4: "0000",
        maxAutoPayAmount: 50000,
        defaultCard: true,
        autoPayEnabled: true
    }
}

function resetBillingForm() {
    billingForm.value = createInitialBillingForm()
}

function toPositiveAmount(value) {
    const amount = Number(value)
    return amount > 0 ? amount : null
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString("ko-KR")}원`
}
</script>

<style scoped>
.billing-card-page {
    display: grid;
    gap: 18px;
    width: min(100%, 1000px);
    margin: 0 auto;
    color: #172033;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.page-heading,
.manager-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
}

.page-heading {
    padding: 6px 2px;
}

.page-kicker {
    margin: 0 0 4px;
    color: #52749c;
    font-size: 11px;
    font-weight: 900;
}

.page-heading h1,
.manager-header h2,
.form-heading h2 {
    margin: 0;
    color: #0b1f3a;
    font-weight: 900;
}

.page-heading h1 {
    font-size: 28px;
}

.page-heading p:last-child,
.manager-header p,
.form-heading p {
    margin: 7px 0 0;
    color: #64748b;
    font-size: 13px;
}

.back-link,
.submit-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 42px;
    padding: 0 16px;
    border: 1px solid #0b1f3a;
    border-radius: 6px;
    background: #ffffff;
    color: #0b1f3a;
    font: inherit;
    font-size: 13px;
    font-weight: 900;
    text-decoration: none;
    cursor: pointer;
    box-sizing: border-box;
}

.back-link:hover,
.submit-button:hover {
    border-color: #174d82;
    background: #174d82;
    color: #ffffff;
}

.billing-card-manager {
    display: grid;
    gap: 20px;
    padding: 24px;
    border: 1px solid #d8e0ec;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.manager-header h2,
.form-heading h2 {
    font-size: 20px;
}

.billing-mode-selector {
    display: inline-flex;
    overflow: hidden;
    border: 1px solid #b8c5d6;
    border-radius: 6px;
}

.billing-mode-selector button {
    min-width: 86px;
    height: 36px;
    padding: 0 12px;
    border: 0;
    border-right: 1px solid #b8c5d6;
    background: #ffffff;
    color: #52657d;
    font-weight: 900;
    cursor: pointer;
}

.billing-mode-selector button:last-child {
    border-right: 0;
}

.billing-mode-selector button.selected {
    background: #0b1f3a;
    color: #ffffff;
}

.billing-mode-selector button:disabled,
.delete-button:disabled,
.submit-button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
}

.page-state,
.empty-text {
    margin: 0;
    padding: 18px;
    border: 1px dashed #cbd5e1;
    border-radius: 6px;
    color: #64748b;
    text-align: center;
}

.billing-card-list {
    display: grid;
    gap: 10px;
}

.billing-card {
    display: grid;
    grid-template-columns: minmax(180px, 1fr) auto;
    gap: 10px 16px;
    align-items: center;
    padding: 14px 16px;
    border: 1px solid #e1e8f1;
    border-radius: 6px;
    background: #f8fafc;
}

.billing-card-main {
    display: grid;
    gap: 3px;
}

.billing-card-main strong {
    color: #0b1f3a;
}

.billing-card-main span {
    color: #64748b;
    font-size: 13px;
}

.billing-card-meta {
    display: flex;
    flex-wrap: wrap;
    grid-column: 1 / -1;
    gap: 7px;
}

.billing-card-meta span {
    padding: 4px 8px;
    border-radius: 999px;
    background: #e8eef8;
    color: #1c3d70;
    font-size: 11px;
    font-weight: 800;
}

.delete-button {
    border: 0;
    background: transparent;
    color: #b42318;
    font: inherit;
    font-size: 13px;
    font-weight: 900;
    cursor: pointer;
}

.form-divider {
    height: 1px;
    background: #e4eaf2;
}

.billing-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(180px, 1fr));
    gap: 14px 12px;
}

.billing-form label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 13px;
    font-weight: 800;
}

.billing-form input[type="text"],
.billing-form input[type="tel"],
.billing-form input[type="password"],
.billing-form input[type="number"] {
    width: 100%;
    min-height: 42px;
    padding: 0 13px;
    border: 1px solid #cbd6e6;
    border-radius: 6px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
    outline: none;
}

.billing-form input:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.billing-options {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 16px;
}

.billing-options label {
    display: flex;
    align-items: center;
    gap: 7px;
}

.submit-button {
    width: 100%;
    background: #0b1f3a;
    color: #ffffff;
}

.form-message {
    margin: 0;
    color: #047857;
    font-size: 13px;
    font-weight: 900;
    text-align: center;
}

.form-message.error {
    color: #b42318;
}

@media (max-width: 640px) {
    .page-heading,
    .manager-header {
        align-items: flex-start;
        flex-direction: column;
    }

    .back-link,
    .billing-mode-selector {
        width: 100%;
    }

    .billing-mode-selector button {
        flex: 1;
    }

    .billing-card-manager {
        padding: 18px;
    }

    .billing-form,
    .billing-card {
        grid-template-columns: 1fr;
    }
}
</style>
