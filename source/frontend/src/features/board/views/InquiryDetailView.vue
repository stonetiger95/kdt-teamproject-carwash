<template>
    <main class="inquiry-detail-page">
        <section class="detail-card">
            <header class="detail-header">
                <p class="eyebrow"></p>
                <h1>문의 상세</h1>
            </header>

            <p
                v-if="inquiryStore.loading"
                class="state-text"
            >
                불러오는 중입니다.
            </p>

            <p
                v-if="inquiryStore.error"
                class="error-text"
            >
                {{ inquiryStore.error }}
            </p>

            <section
                v-if="!hasInquiryId"
                class="empty-state"
            >
                <strong>문의 상세를 선택해주세요.</strong>
                <p>문의 목록에서 제목을 선택하면 상세 내용을 확인할 수 있습니다.</p>

                <RouterLink
                    class="secondary-button"
                    :to="{ name: actualListRouteName }"
                >
                    목록
                </RouterLink>
            </section>

            <section
                v-else-if="inquiryStore.inquiry"
                class="detail-content"
            >
                <div class="title-block">
                    <h2>{{ inquiryStore.inquiry.title || "제목 없음" }}</h2>
                    <span class="status-badge">
                        {{ displayStatus(inquiryStore.inquiry.status) }}
                    </span>
                </div>

                <dl class="meta-grid">
                    <div>
                        <dt>작성자</dt>
                        <dd>{{ inquiryStore.inquiry.writerName || "-" }}</dd>
                    </div>
                    <div>
                        <dt>작성자 역할</dt>
                        <dd>{{ displayRole(inquiryStore.inquiry.writerRole) }}</dd>
                    </div>
                    <div>
                        <dt>상태</dt>
                        <dd>{{ displayStatus(inquiryStore.inquiry.status) }}</dd>
                    </div>
                    <div>
                        <dt>작성일</dt>
                        <dd>{{ formatDate(inquiryStore.inquiry.createdAt) }}</dd>
                    </div>
                </dl>

                <article class="content-box">
                    <h3>문의 내용</h3>
                    <p>{{ inquiryContent }}</p>
                </article>

                <section
                    v-if="inquiryStore.inquiry.washSiteId"
                    class="site-contact-section"
                >
                    <div class="section-heading">
                        <h3>문의 세차장</h3>
                    </div>

                    <div class="site-contact">
                        <div class="site-summary">
                            <strong>{{ inquiryStore.inquiry.siteName || `세차장 #${inquiryStore.inquiry.washSiteId}` }}</strong>
                            <span v-if="inquiryStore.inquiry.siteAddress">
                                {{ inquiryStore.inquiry.siteAddress }}
                            </span>
                        </div>
                        <div class="site-phone">
                            <span>유선전화</span>
                            <strong>{{ inquiryStore.inquiry.sitePhone || "미등록" }}</strong>
                        </div>
                    </div>
                </section>

                <section
                    v-if="isRefundInquiry"
                    class="refund-section"
                >
                    <div class="section-heading">
                        <h3>환불 요청 정보</h3>
                        <span>{{ displayPurpose(inquiryStore.inquiry.paymentPurpose) }}</span>
                    </div>

                    <dl class="refund-summary">
                        <div>
                            <dt>결제 번호</dt>
                            <dd>{{ inquiryStore.inquiry.paymentHistoryId }}</dd>
                        </div>
                        <div>
                            <dt>원 결제금액</dt>
                            <dd>{{ formatAmount(inquiryStore.inquiry.paymentAmount) }}</dd>
                        </div>
                        <div>
                            <dt>환불 완료액</dt>
                            <dd>{{ formatAmount(inquiryStore.inquiry.refundedAmount) }}</dd>
                        </div>
                        <div>
                            <dt>현재 환불 가능액</dt>
                            <dd>{{ formatAmount(inquiryStore.inquiry.refundableAmount) }}</dd>
                        </div>
                    </dl>

                    <p v-if="refundPolicy" class="refund-policy-notice">
                        {{ refundPolicy.partialRefundAllowed
                            ? "부분 환불은 가능하지만 사용한 쿠폰은 전액 환불 완료 시에만 복원됩니다."
                            : "마일리지가 사용된 결제입니다. 전액 환불만 가능하며 완료 후 사용 마일리지가 복원됩니다." }}
                    </p>

                    <form
                        v-if="canProcessRefund"
                        class="refund-form"
                        @submit.prevent="submitRefund"
                    >
                        <label for="refundAmount">
                            환불 금액
                            <input
                                id="refundAmount"
                                v-model.number="refundForm.refundAmount"
                                type="number"
                                :min="benefitOnlyRefund ? 0 : 1"
                                :max="refundPolicy?.payableAmount ?? inquiryStore.inquiry.refundableAmount"
                                :readonly="refundPolicy?.partialRefundAllowed === false"
                                required
                            >
                        </label>
                        <label for="refundReason">
                            처리 사유
                            <input
                                id="refundReason"
                                v-model.trim="refundForm.reason"
                                maxlength="500"
                                required
                            >
                        </label>
                        <button
                            class="refund-button"
                            type="submit"
                            :disabled="refundProcessing"
                        >
                            {{ refundProcessing ? "처리 중" : "환불 실행" }}
                        </button>
                    </form>

                    <p
                        v-else-if="Number(inquiryStore.inquiry.refundableAmount || 0) === 0"
                        class="refund-complete"
                    >
                        환불 가능한 잔액이 없습니다.
                    </p>
                </section>

                <section
                    v-if="inquiryAnswers.length"
                    class="answer-thread"
                >
                    <div class="section-heading">
                        <h3>답변 내역</h3>
                        <span>{{ inquiryAnswers.length }}개의 답변</span>
                    </div>

                    <ol class="answer-list">
                        <li
                            v-for="(answer, index) in inquiryAnswers"
                            :key="answer.inquiryAnswerId || index"
                            class="answer-item"
                        >
                            <div class="answer-meta">
                                <strong>{{ displayAnswerer(answer) }}</strong>
                                <time>{{ formatDate(answer.createdAt) }}</time>
                            </div>
                            <p>{{ answer.answer }}</p>
                        </li>
                    </ol>
                </section>

                <form
                    v-if="canAnswerInquiry"
                    class="answer-form"
                    @submit.prevent="submitAnswer"
                >
                    <h3>새 답변 작성</h3>

                    <textarea
                        v-model="answerForm.answer"
                        required
                        rows="6"
                        placeholder="답변 내용을 입력하세요."
                    />

                    <button
                        class="primary-button"
                        type="submit"
                    >
                        답변 추가
                    </button>
                </form>

                <div class="action-row">
                    <RouterLink
                        v-if="canEditInquiry"
                        class="secondary-button"
                        :to="{
                            name: actualEditRouteName,
                            params: { inquiryId }
                        }"
                    >
                        수정
                    </RouterLink>

                    <button
                        v-if="canEditInquiry"
                        class="danger-button"
                        type="button"
                        @click="removeInquiry"
                    >
                        삭제
                    </button>

                    <RouterLink
                        class="secondary-button"
                        :to="{ name: actualListRouteName }"
                    >
                        목록
                    </RouterLink>
                </div>
            </section>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import {
    canAnswerInquiry as checkCanAnswerInquiry,
    canEditInquiry as checkCanEditInquiry,
    getInquiryRole,
    getInquiryRouteNames
} from "@/shared/auth/authRole"
import { useInquiryStore } from "../stores/inquiryStore"
import { getPaymentBenefitRefundPolicy } from "@/features/benefit/api/benefitApi"

const route = useRoute()
const router = useRouter()
const inquiryStore = useInquiryStore()

const inquiryId = computed(() => route.params.inquiryId)
const hasInquiryId = computed(() => Boolean(inquiryId.value))

const routeNames = computed(() => getInquiryRouteNames())
const actualListRouteName = computed(() => routeNames.value.listRouteName)
const actualEditRouteName = computed(() => routeNames.value.editRouteName)

const answerForm = reactive({
    answer: ""
})

const refundForm = reactive({
    refundAmount: 0,
    reason: "고객 환불 문의 승인"
})
const refundProcessing = ref(false)
const refundPolicy = ref(null)

const inquiryContent = computed(() => {
    return inquiryStore.inquiry?.content || inquiryStore.inquiry?.inquiryContent || "-"
})

const inquiryAnswers = computed(() => {
    const answers = inquiryStore.inquiry?.answers

    if (Array.isArray(answers) && answers.length > 0) {
        return answers
    }

    if (inquiryStore.inquiry?.answer) {
        return [{
            inquiryAnswerId: "legacy",
            answer: inquiryStore.inquiry.answer,
            answererId: "legacy",
            answererRole: "ADMIN",
            createdAt: inquiryStore.inquiry.answeredAt
        }]
    }

    return []
})

const canAnswerInquiry = computed(() => {
    return checkCanAnswerInquiry(inquiryStore.inquiry)
})

const canEditInquiry = computed(() => {
    return Boolean(actualEditRouteName.value) && checkCanEditInquiry(inquiryStore.inquiry)
})

const isRefundInquiry = computed(() =>
    inquiryStore.inquiry?.inquiryType === "REFUND"
)

const canProcessRefund = computed(() =>
    isRefundInquiry.value
    && canAnswerInquiry.value
    && (
        Number(inquiryStore.inquiry?.refundableAmount || 0) > 0
        || benefitOnlyRefund.value
    )
)

const benefitOnlyRefund = computed(() =>
    refundPolicy.value?.payableAmount === 0
    && (
        Number(refundPolicy.value?.couponDiscountAmount || 0) > 0
        || Number(refundPolicy.value?.mileageUsedAmount || 0) > 0
    )
)

onMounted(async () => {
    if (!hasInquiryId.value) {
        return
    }

    await inquiryStore.fetchInquiry(inquiryId.value)
    await loadRefundPolicy()
    setDefaultRefundAmount()
})

async function loadRefundPolicy() {
    const paymentHistoryId = inquiryStore.inquiry?.paymentHistoryId
    if (!paymentHistoryId) {
        refundPolicy.value = null
        return
    }
    try {
        refundPolicy.value = await getPaymentBenefitRefundPolicy(
            getInquiryRole(),
            paymentHistoryId
        )
    } catch (error) {
        refundPolicy.value = null
    }
}

function setDefaultRefundAmount() {
    refundForm.refundAmount = Number(
        refundPolicy.value?.payableAmount
        ?? inquiryStore.inquiry?.refundableAmount
        ?? 0
    )
}

async function submitRefund() {
    if (refundProcessing.value) {
        return
    }

    const maxAmount = Number(inquiryStore.inquiry?.refundableAmount || 0)
    const refundAmount = Number(refundForm.refundAmount)

    if (!Number.isInteger(refundAmount)
        || refundAmount < 0
        || (!benefitOnlyRefund.value && refundAmount > maxAmount)) {
        return
    }

    refundProcessing.value = true
    const success = await inquiryStore.refundInquiry(inquiryId.value, {
        refundAmount,
        reason: refundForm.reason
    })
    refundProcessing.value = false

    if (success) {
        await inquiryStore.fetchInquiry(inquiryId.value)
        await loadRefundPolicy()
        setDefaultRefundAmount()
    }
}

async function submitAnswer() {
    const success = await inquiryStore.answerInquiry(
        inquiryId.value,
        answerForm
    )

    if (success) {
        answerForm.answer = ""
        await inquiryStore.fetchInquiry(inquiryId.value)
    }
}

async function removeInquiry() {
    const success = await inquiryStore.deleteInquiry(inquiryId.value)

    if (success) {
        router.push({ name: actualListRouteName.value })
    }
}

function displayStatus(value) {
    const labels = {
        WAITING: "대기",
        ANSWERED: "답변 완료",
        CLOSED: "종료"
    }

    return labels[value] || value || "-"
}

function displayRole(value) {
    const labels = {
        ADMIN: "관리자",
        OWNER: "사업자",
        CUSTOMER: "고객"
    }

    return labels[value] || value || "-"
}

function displayPurpose(value) {
    const labels = {
        CHARGE: "선불 충전",
        WASH_PAYMENT: "세차 결제"
    }

    return labels[value] || value || "결제"
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString()}원`
}

function displayAnswerer(answer) {
    const role = displayRole(answer.answererRole)

    if (!answer.answererId || answer.answererId === "legacy") {
        return role
    }

    return `${role} · ${answer.answererId}`
}

function formatDate(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(0, 16)
}
</script>

<style scoped>
.inquiry-detail-page {
    min-height: calc(100vh - 86px);
    padding: 52px 20px;
    background: #f4f7fb;
    color: #172033;
}

.detail-card {
    width: min(100%, 860px);
    margin: 0 auto;
    padding: 34px;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

.detail-header {
    margin-bottom: 24px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #5b728f;
    font-size: 12px;
    font-weight: 900;
    letter-spacing: 0;
}

h1,
h2,
h3 {
    margin: 0;
    color: #0b1f3a;
    font-weight: 900;
    letter-spacing: 0;
}

h1 {
    font-size: 32px;
}

h2 {
    font-size: 24px;
    line-height: 1.35;
}

h3 {
    font-size: 17px;
}

.state-text,
.error-text {
    margin: 0 0 16px;
    padding: 13px 15px;
    border-radius: 8px;
    background: #f8fafc;
    color: #475467;
    font-size: 14px;
}

.error-text {
    background: #fff1f2;
    color: #be123c;
}

.empty-state {
    display: grid;
    justify-items: center;
    gap: 12px;
    padding: 44px 20px;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #f8fafc;
    text-align: center;
}

.empty-state strong {
    color: #0b1f3a;
    font-size: 18px;
}

.empty-state p {
    margin: 0;
    color: #64748b;
    font-size: 14px;
}

.detail-content {
    display: grid;
    gap: 20px;
}

.title-block {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding-bottom: 18px;
    border-bottom: 1px solid #edf2f7;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 82px;
    min-height: 28px;
    padding: 4px 11px;
    border-radius: 999px;
    background: #eef4fb;
    color: #0b1f3a;
    font-size: 12px;
    font-weight: 900;
    white-space: nowrap;
}

.meta-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1px;
    overflow: hidden;
    margin: 0;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #e4e9f1;
}

.meta-grid div {
    display: grid;
    gap: 6px;
    padding: 14px;
    background: #ffffff;
}

dt,
dd {
    margin: 0;
}

dt {
    color: #64748b;
    font-size: 12px;
    font-weight: 900;
}

dd {
    color: #172033;
    font-size: 14px;
    font-weight: 800;
}

.content-box,
.refund-section,
.answer-thread,
.answer-form {
    display: grid;
    gap: 12px;
    padding: 20px;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #ffffff;
}

.refund-section {
    border-color: #bbd7c2;
    background: #f7fcf8;
}

.refund-summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1px;
    overflow: hidden;
    margin: 0;
    border: 1px solid #cfe3d4;
    border-radius: 8px;
    background: #cfe3d4;
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

.refund-summary div {
    display: grid;
    gap: 6px;
    padding: 13px;
    background: #ffffff;
}

.refund-form {
    display: grid;
    grid-template-columns: minmax(130px, 0.7fr) minmax(220px, 1.4fr) auto;
    align-items: end;
    gap: 12px;
}

.refund-form label {
    display: grid;
    gap: 6px;
    color: #344054;
    font-size: 13px;
    font-weight: 800;
}

.refund-form input {
    width: 100%;
    min-height: 40px;
    padding: 8px 10px;
    border: 1px solid #b7cbbd;
    border-radius: 6px;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
}

.refund-button {
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid #166534;
    border-radius: 8px;
    background: #166534;
    color: #ffffff;
    font: inherit;
    font-weight: 900;
    cursor: pointer;
}

.refund-button:disabled {
    opacity: 0.6;
    cursor: wait;
}

.refund-complete {
    margin: 0;
    color: #166534;
    font-weight: 800;
}

.site-contact-section {
    display: grid;
    gap: 12px;
    padding: 18px 0;
    border-top: 1px solid #e4e9f1;
    border-bottom: 1px solid #e4e9f1;
}

.site-contact {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(160px, auto);
    gap: 20px;
    padding: 14px 16px;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #f8fafc;
}

.site-summary,
.site-phone {
    display: grid;
    gap: 5px;
}

.site-summary strong,
.site-phone strong {
    color: #172033;
}

.site-summary span,
.site-phone span {
    color: #64748b;
    font-size: 13px;
}

.site-phone {
    text-align: right;
}

.content-box {
    background: #fbfdff;
    text-align: left;
}

.content-box p {
    min-height: 88px;
    margin: 0;
    color: #344054;
    font-size: 15px;
    line-height: 1.7;
    overflow-wrap: anywhere;
    word-break: keep-all;
    white-space: pre-wrap;
}

.answer-thread {
    background: #f6fbf9;
    border-color: #cfe8dc;
}

.answer-list {
    display: grid;
    gap: 0;
    margin: 0;
    padding: 0;
    list-style: none;
}

.answer-item {
    display: grid;
    gap: 9px;
    padding: 16px 4px 16px 16px;
    border-left: 3px solid #2d7a67;
}

.answer-item + .answer-item {
    border-top: 1px solid #d9ebe4;
}

.answer-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.answer-meta strong {
    color: #245f52;
    font-size: 14px;
}

.answer-meta time {
    color: #64748b;
    font-size: 12px;
}

.answer-item p {
    margin: 0;
    color: #344054;
    font-size: 15px;
    line-height: 1.7;
    overflow-wrap: anywhere;
    text-align: left;
    word-break: keep-all;
    white-space: pre-wrap;
}

.section-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.section-heading span {
    color: #64748b;
    font-size: 13px;
    font-weight: 700;
}

.answer-form {
    background: #f8fafc;
}

textarea {
    width: 100%;
    min-height: 150px;
    padding: 12px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    background: #ffffff;
    color: #111827;
    font-size: 14px;
    line-height: 1.6;
    text-align: left;
    resize: vertical;
    outline: none;
    box-sizing: border-box;
}

textarea:focus {
    border-color: #0b1f3a;
    box-shadow: 0 0 0 3px rgba(11, 31, 58, 0.12);
}

.action-row {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 4px;
}

.primary-button,
.secondary-button,
.danger-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 76px;
    height: 38px;
    padding: 0 16px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 900;
    text-decoration: none;
    box-sizing: border-box;
}

.primary-button {
    justify-self: end;
    border: 1px solid #0b1f3a;
    background: #0b1f3a;
    color: #ffffff;
    cursor: pointer;
}

.secondary-button {
    border: 1px solid #d7e0eb;
    background: #eef3f8;
    color: #0b1f3a;
}

.danger-button {
    border: 1px solid #fee2e2;
    background: #fff1f2;
    color: #be123c;
    cursor: pointer;
}

.primary-button:hover {
    background: #163b68;
}

.secondary-button:hover {
    background: #e2eaf3;
}

.danger-button:hover {
    background: #ffe4e6;
}

@media (max-width: 760px) {
    .inquiry-detail-page {
        padding: 36px 16px;
    }

    .detail-card {
        padding: 24px;
    }

    .title-block,
    .section-heading,
    .action-row {
        align-items: stretch;
        flex-direction: column;
    }

    .meta-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .refund-summary,
    .refund-form {
        grid-template-columns: 1fr;
    }

    .site-contact {
        grid-template-columns: 1fr;
        gap: 12px;
    }

    .site-phone {
        text-align: left;
    }

    h1 {
        font-size: 28px;
    }

    h2 {
        font-size: 21px;
    }
}

@media (max-width: 480px) {
    .meta-grid {
        grid-template-columns: 1fr;
    }
}
</style>
