<template>
    <main class="inquiry-form-page">
        <section class="form-card">
            <header>
                <p class="eyebrow">CUSTOMER SUPPORT</p>
                <h1>{{ isEdit ? "문의 수정" : "문의 작성" }}</h1>
            </header>

            <p v-if="inquiryStore.error" class="error-text">
                {{ inquiryStore.error }}
            </p>

            <div v-if="editLocked" class="locked-state">
                <strong>답변이 등록된 문의는 수정할 수 없습니다.</strong>
                <RouterLink
                    :to="{ name: actualDetailRouteName, params: { inquiryId } }"
                >
                    문의 상세로 이동
                </RouterLink>
            </div>

            <form v-else @submit.prevent="submitInquiry">
                <label v-if="isCustomer" for="inquiryType">
                    문의 유형
                    <select
                        id="inquiryType"
                        v-model="form.inquiryType"
                        :disabled="isEdit && form.inquiryType === 'REFUND'"
                        @change="handleTypeChange"
                    >
                        <option value="WASH_SITE">세차장 문의</option>
                        <option value="REFUND">환불 문의</option>
                        <option value="SERVICE">기능 이용 문의</option>
                        <option value="ETC">기타 문의</option>
                    </select>
                </label>

                <section v-if="isRefundInquiry" class="payment-summary">
                    <div>
                        <span>환불 대상</span>
                        <strong>{{ linkedPayment?.orderName || route.query.orderName || "결제 내역" }}</strong>
                    </div>
                    <div>
                        <span>결제 번호</span>
                        <strong>{{ form.paymentHistoryId || "선택되지 않음" }}</strong>
                    </div>
                    <div>
                        <span>결제 금액</span>
                        <strong>{{ formatAmount(linkedPayment?.amount || route.query.amount) }}</strong>
                    </div>

                    <RouterLink
                        v-if="!form.paymentHistoryId"
                        class="payment-select-link"
                        :to="{ name: 'customer-payments' }"
                    >
                        결제 내역에서 환불 건 선택
                    </RouterLink>
                </section>

                <p v-if="refundPolicy" class="refund-policy-notice">
                    {{ refundPolicy.partialRefundAllowed
                        ? "쿠폰 사용 결제는 전액 환불 완료 시 유효한 쿠폰이 복원됩니다."
                        : "마일리지를 사용한 결제는 부분 환불할 수 없으며, 전액 환불 완료 시 사용 마일리지가 복원됩니다." }}
                </p>

                <label v-if="showWashSiteSelect" for="washSiteId">
                    문의 세차장
                    <select
                        id="washSiteId"
                        v-model="form.washSiteId"
                        required
                        :disabled="inquiryStore.siteLoading || !inquiryStore.usedWashSites.length"
                    >
                        <option disabled value="">세차장을 선택하세요</option>
                        <option
                            v-for="site in inquiryStore.usedWashSites"
                            :key="site.washSiteId"
                            :value="site.washSiteId"
                        >
                            {{ washSiteLabel(site) }}
                        </option>
                    </select>
                </label>

                <p
                    v-if="showWashSiteSelect && !inquiryStore.siteLoading && !inquiryStore.usedWashSites.length"
                    class="site-empty-text"
                >
                    문의할 수 있는 세차 이용 내역이 없습니다.
                </p>

                <label for="title">
                    제목
                    <input id="title" v-model.trim="form.title" required maxlength="200">
                </label>

                <label for="writerName">
                    작성자
                    <input id="writerName" v-model.trim="form.writerName" :disabled="isEdit">
                </label>

                <label for="content">
                    내용
                    <textarea
                        id="content"
                        v-model.trim="form.content"
                        required
                        rows="10"
                        placeholder="문의 내용을 입력하세요. 환불 문의라면 환불 사유도 함께 작성해 주세요."
                    />
                </label>

                <div class="action-row">
                    <button type="submit" :disabled="submitDisabled">
                        저장
                    </button>
                    <RouterLink :to="{ name: actualListRouteName }">
                        취소
                    </RouterLink>
                </div>
            </form>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import {
    canEditInquiry as checkCanEditInquiry,
    getInquiryRole,
    getInquiryRouteNames
} from "@/shared/auth/authRole"
import { getPayment } from "@/features/payment/api/paymentApi"
import { getPaymentBenefitRefundPolicy } from "@/features/benefit/api/benefitApi"
import { useInquiryStore } from "../stores/inquiryStore"

const route = useRoute()
const router = useRouter()
const inquiryStore = useInquiryStore()

const inquiryId = computed(() => route.params.inquiryId)
const isEdit = computed(() => Boolean(inquiryId.value))
const isCustomer = computed(() => getInquiryRole() === "CUSTOMER")
const routeNames = computed(() => getInquiryRouteNames())
const actualListRouteName = computed(() => routeNames.value.listRouteName)
const actualDetailRouteName = computed(() => routeNames.value.detailRouteName)
const editLocked = ref(false)
const linkedPayment = ref(null)
const refundPolicy = ref(null)

const form = reactive({
    title: "",
    content: "",
    writerName: localStorage.getItem("userName") || "",
    inquiryType: "WASH_SITE",
    paymentHistoryId: "",
    washSiteId: ""
})

const isRefundInquiry = computed(() =>
    isCustomer.value && form.inquiryType === "REFUND"
)
const showWashSiteSelect = computed(() =>
    isCustomer.value && form.inquiryType === "WASH_SITE"
)
const submitDisabled = computed(() => {
    if (isRefundInquiry.value && !form.paymentHistoryId) {
        return true
    }

    if (showWashSiteSelect.value) {
        return inquiryStore.siteLoading
            || !inquiryStore.usedWashSites.length
            || !form.washSiteId
    }

    return false
})

onMounted(async () => {
    if (isCustomer.value) {
        await inquiryStore.fetchUsedWashSites()
    }

    if (isEdit.value) {
        await loadInquiryForEdit()
        return
    }

    const requestedType = String(route.query.type || "").toUpperCase()
    if (["REFUND", "WASH_SITE", "SERVICE", "ETC"].includes(requestedType)) {
        form.inquiryType = requestedType
    }

    if (form.inquiryType === "REFUND") {
        form.paymentHistoryId = route.query.paymentHistoryId || ""
        form.title = route.query.orderName
            ? `[환불 문의] ${route.query.orderName}`
            : "[환불 문의] 결제 취소 요청"
        await loadLinkedPayment()
    } else if (inquiryStore.usedWashSites.length === 1) {
        form.washSiteId = inquiryStore.usedWashSites[0].washSiteId
    }
})

async function loadInquiryForEdit() {
    await inquiryStore.fetchInquiry(inquiryId.value)

    if (!inquiryStore.inquiry) {
        return
    }

    if (!checkCanEditInquiry(inquiryStore.inquiry)) {
        editLocked.value = true
        return
    }

    form.title = inquiryStore.inquiry.title || ""
    form.content = inquiryStore.inquiry.content || inquiryStore.inquiry.inquiryContent || ""
    form.writerName = inquiryStore.inquiry.writerName || ""
    form.inquiryType = inquiryStore.inquiry.inquiryType || "WASH_SITE"
    form.paymentHistoryId = inquiryStore.inquiry.paymentHistoryId || ""
    form.washSiteId = inquiryStore.inquiry.washSiteId || ""

    if (form.inquiryType === "REFUND") {
        await loadLinkedPayment()
    }
}

async function loadLinkedPayment() {
    if (!form.paymentHistoryId) {
        linkedPayment.value = null
        refundPolicy.value = null
        return
    }

    try {
        linkedPayment.value = await getPayment(form.paymentHistoryId)
        refundPolicy.value = await getPaymentBenefitRefundPolicy(
            "CUSTOMER",
            form.paymentHistoryId
        )
    } catch (error) {
        linkedPayment.value = null
        refundPolicy.value = null
    }
}

function handleTypeChange() {
    if (form.inquiryType !== "REFUND") {
        form.paymentHistoryId = ""
        linkedPayment.value = null
        refundPolicy.value = null
    }

    if (form.inquiryType !== "WASH_SITE") {
        form.washSiteId = ""
    } else if (inquiryStore.usedWashSites.length === 1) {
        form.washSiteId = inquiryStore.usedWashSites[0].washSiteId
    }
}

async function submitInquiry() {
    if (editLocked.value || submitDisabled.value) {
        return
    }

    const inquiryInfo = {
        title: form.title,
        content: form.content,
        writerName: form.writerName,
        inquiryType: isCustomer.value ? form.inquiryType : "ETC",
        paymentHistoryId: isRefundInquiry.value
            ? Number(form.paymentHistoryId)
            : null,
        washSiteId: showWashSiteSelect.value
            ? Number(form.washSiteId)
            : null
    }

    const result = isEdit.value
        ? await inquiryStore.updateInquiry(inquiryId.value, inquiryInfo)
        : await inquiryStore.createInquiry(inquiryInfo)

    if (!result) {
        return
    }

    if (isEdit.value) {
        await router.push({
            name: actualDetailRouteName.value,
            params: { inquiryId: inquiryId.value }
        })
        return
    }

    await router.push({ name: actualListRouteName.value })
}

function washSiteLabel(site) {
    return site.address
        ? `${site.siteName} · ${site.address}`
        : site.siteName
}

function formatAmount(value) {
    if (!value) {
        return "-"
    }
    return `${Number(value).toLocaleString()}원`
}
</script>

<style scoped>
.inquiry-form-page {
    min-height: calc(100vh - 86px);
    padding: 52px 20px;
    background: #f4f7fb;
}

.form-card {
    width: min(100%, 600px);
    margin: 0 auto;
    padding: 32px;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

header {
    margin-bottom: 24px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #64748b;
    font-size: 12px;
    font-weight: 900;
}

h1 {
    margin: 0;
    color: #0b1f3a;
    font-size: 30px;
    font-weight: 900;
}

form {
    display: grid;
    gap: 16px;
}

label {
    display: grid;
    gap: 7px;
    color: #344054;
    font-size: 14px;
    font-weight: 800;
}

input,
select,
textarea {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    background: #ffffff;
    color: #111827;
    font: inherit;
    text-align: left;
    box-sizing: border-box;
}

input,
select {
    min-height: 42px;
}

textarea {
    min-height: 170px;
    resize: vertical;
}

.payment-summary {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    padding: 16px;
    border: 1px solid #bbd7c2;
    border-radius: 8px;
    background: #f0fdf4;
}

.refund-policy-notice {
    margin: 0;
    padding: 12px 14px;
    border-left: 3px solid #166534;
    background: #f0fdf4;
    color: #166534;
    font-size: 13px;
    line-height: 1.6;
}

.payment-summary div {
    display: grid;
    gap: 5px;
    min-width: 0;
}

.payment-summary span {
    color: #4b6352;
    font-size: 12px;
}

.payment-summary strong {
    overflow-wrap: anywhere;
    color: #14532d;
    font-size: 14px;
}

.payment-select-link {
    grid-column: 1 / -1;
    justify-self: start;
}

.error-text,
.site-empty-text {
    color: #be123c;
}

.error-text,
.locked-state {
    margin-bottom: 16px;
    padding: 14px;
    border-radius: 8px;
    background: #fff1f2;
}

.locked-state {
    display: grid;
    gap: 10px;
    color: #7f1d2d;
}

.site-empty-text {
    margin: -6px 0 0;
    font-size: 13px;
}

.action-row {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
}

button,
.action-row a,
.payment-select-link,
.locked-state a {
    display: inline-flex;
    min-height: 38px;
    align-items: center;
    justify-content: center;
    padding: 0 16px;
    border: 1px solid #0b1f3a;
    border-radius: 8px;
    background: #0b1f3a;
    color: #ffffff;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    text-decoration: none;
    cursor: pointer;
}

.action-row a,
.payment-select-link,
.locked-state a {
    border-color: #d7e0ea;
    background: #eef3f8;
    color: #0b1f3a;
}

button:disabled {
    border-color: #cbd5e1;
    background: #e2e8f0;
    color: #64748b;
    cursor: not-allowed;
}

@media (max-width: 620px) {
    .inquiry-form-page {
        padding: 32px 14px;
    }

    .form-card {
        padding: 22px;
    }

    .payment-summary {
        grid-template-columns: 1fr;
    }
}
</style>
