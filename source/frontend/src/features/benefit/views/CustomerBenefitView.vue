<template>
    <main class="benefit-page">
        <header>
            <p class="eyebrow">MY BENEFITS</p>
            <h1>마일리지·쿠폰</h1>
            <p>자동 세차 결제에 사용할 혜택 방식을 선택합니다.</p>
        </header>

        <section v-if="!loading && !error && visitRewardProgress.length" class="reward-progress">
            <header class="section-title">
                <h2>무료 세차 이용 달성 현황</h2>
                <p>결제가 완료된 세차만 이용 횟수에 반영됩니다.</p>
            </header>
            <article
                v-for="progressItem in visitRewardProgress"
                :key="progressItem.couponCampaignId"
                class="progress-row"
            >
                <div>
                    <strong>{{ progressItem.campaignName }}</strong>
                    <span>{{ progressItem.siteName }}</span>
                </div>
                <div class="progress-copy">
                    <b>{{ progressItem.paidWashCount || 0 }} / {{ progressItem.requiredPaidWashes }}회</b>
                    <small>
                        {{ progressMessage(progressItem) }}
                    </small>
                </div>
                <progress
                    :value="progressItem.paidWashCount || 0"
                    :max="progressItem.requiredPaidWashes || 1"
                />
            </article>
        </section>

        <p v-if="success" class="state success">{{ success }}</p>
        <p v-if="loading" class="state">혜택 정보를 불러오는 중입니다.</p>
        <p v-else-if="error" class="state error">{{ error }}</p>
        <p v-else-if="benefits.length === 0" class="state">
            현재 이용 중인 세차장에서 운영하는 혜택이 없습니다.
        </p>

        <section v-else class="benefit-list">
            <article v-for="benefit in benefits" :key="benefit.washSiteId" class="benefit-row">
                <div class="site-copy">
                    <strong>{{ benefit.siteName }}</strong>
                    <span>{{ benefit.ownerName }}</span>
                </div>

                <dl>
                    <div v-if="benefit.mileageEnabled || Number(benefit.mileageBalance || 0) > 0">
                        <dt>마일리지</dt>
                        <dd>{{ formatAmount(benefit.mileageBalance) }}</dd>
                        <small v-if="!benefit.mileageEnabled">현재 적립·사용 중지</small>
                    </div>
                    <div v-if="benefit.mileageEnabled">
                        <dt>적립률</dt>
                        <dd>{{ formatRate(benefit.mileageEarnBasisPoints) }}</dd>
                    </div>
                    <div v-if="benefit.couponEnabled || Number(benefit.availableCouponCount || 0) > 0">
                        <dt>사용 가능 쿠폰</dt>
                        <dd>{{ benefit.availableCouponCount || 0 }}장</dd>
                    </div>
                </dl>

                <div class="preference-list">
                    <label v-if="benefit.mileageEnabled">
                        <input v-model="benefit.useAllMileage" type="checkbox">
                        결제할 때 사용 가능한 마일리지 전부 사용
                    </label>
                    <small v-if="benefit.mileageEnabled && benefit.useAllMileage">
                        마일리지를 사용한 세차 결제는 부분 환불할 수 없습니다.
                    </small>
                    <label v-if="benefit.couponEnabled">
                        <input v-model="benefit.autoUseCoupon" type="checkbox">
                        가장 유리한 사용 가능 쿠폰 자동 적용
                    </label>
                </div>

                <button type="button" :disabled="savingSiteId === benefit.washSiteId" @click="savePreference(benefit)">
                    {{ savingSiteId === benefit.washSiteId ? "저장 중" : "사용 설정 저장" }}
                </button>

                <div v-if="benefit.availableCoupons?.length" class="coupon-list">
                    <div class="coupon-list-title">
                        <strong>내 쿠폰</strong>
                        <span>이 세차장에서만 사용할 수 있습니다.</span>
                    </div>
                    <article v-for="coupon in benefit.availableCoupons" :key="coupon.customerCouponId" class="coupon-row">
                        <div>
                            <strong>{{ coupon.campaignName }}</strong>
                            <span>{{ couponDescription(coupon) }}</span>
                        </div>
                        <div>
                            <b>{{ couponBenefit(coupon) }}</b>
                            <small>{{ formatExpiry(coupon.expiresAt) }}까지</small>
                        </div>
                    </article>
                </div>
            </article>
        </section>
    </main>
</template>

<script setup>
import { onMounted, ref } from "vue"
import * as benefitApi from "../api/benefitApi"

const benefits = ref([])
const visitRewardProgress = ref([])
const loading = ref(false)
const error = ref("")
const success = ref("")
const savingSiteId = ref(null)

onMounted(loadBenefits)

async function loadBenefits() {
    loading.value = true
    error.value = ""
    success.value = ""
    try {
        const [loadedBenefits, loadedProgress] = await Promise.all([
            benefitApi.getMyBenefits(),
            benefitApi.getMyVisitRewardProgress()
        ])
        const benefitRows = loadedBenefits || []
        benefits.value = await Promise.all(benefitRows.map(async benefit => {
            if (Number(benefit.availableCouponCount || 0) <= 0) {
                return { ...benefit, availableCoupons: [] }
            }
            try {
                const coupons = await benefitApi.getMyAvailableCoupons(benefit.washSiteId)
                return { ...benefit, availableCoupons: coupons || [] }
            } catch {
                return { ...benefit, availableCoupons: [] }
            }
        }))
        visitRewardProgress.value = loadedProgress || []
    } catch (e) {
        error.value = e?.response?.data?.message || "혜택 정보를 불러오지 못했습니다."
    } finally {
        loading.value = false
    }
}

async function savePreference(benefit) {
    savingSiteId.value = benefit.washSiteId
    error.value = ""
    success.value = ""
    try {
        await benefitApi.saveMyBenefitPreference(benefit.washSiteId, {
            useAllMileage: Boolean(
                benefit.mileageEnabled && benefit.useAllMileage
            ),
            autoUseCoupon: Boolean(
                benefit.couponEnabled && benefit.autoUseCoupon
            )
        })
        benefit.useAllMileage = Boolean(
            benefit.mileageEnabled && benefit.useAllMileage
        )
        benefit.autoUseCoupon = Boolean(
            benefit.couponEnabled && benefit.autoUseCoupon
        )
        success.value = `${benefit.siteName} 혜택 사용 설정이 저장되었습니다.`
    } catch (e) {
        error.value = e?.response?.data?.message || "혜택 사용 설정을 저장하지 못했습니다."
    } finally {
        savingSiteId.value = null
    }
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString("ko-KR")}P`
}

function formatRate(value) {
    return `${Number(value || 0) / 100}%`
}

function progressMessage(progressItem) {
    const remaining = Number(progressItem.remainingPaidWashes || 0)
    return remaining > 0
        ? `${remaining}회 더 이용하면 무료 세차 쿠폰이 발급됩니다.`
        : "무료 세차 쿠폰 발급 조건을 달성했습니다."
}
function couponBenefit(coupon) {
    if (coupon.couponType === "FREE_WASH") return "무료 세차"
    if (coupon.couponType === "PERCENT_DISCOUNT") return `${Number(coupon.discountValue || 0) / 100}% 할인`
    return `${Number(coupon.discountValue || 0).toLocaleString("ko-KR")}원 할인`
}
function couponDescription(coupon) {
    if (coupon.couponType === "FREE_WASH") return "세차 결제금액 전액 할인"
    const minimum = Number(coupon.minimumPaymentAmount || 0)
    const maximum = Number(coupon.maxDiscountAmount || 0)
    const parts = []
    if (minimum > 0) parts.push(`${minimum.toLocaleString("ko-KR")}원 이상 결제 시`)
    if (maximum > 0 && coupon.couponType === "PERCENT_DISCOUNT") parts.push(`최대 ${maximum.toLocaleString("ko-KR")}원`)
    return parts.length ? parts.join(" · ") : "결제 시 적용 가능"
}
function formatExpiry(value) { return value ? String(value).replace("T", " ").slice(0, 16) : "만료일 없음" }
</script>

<style scoped>
.benefit-page { display: grid; gap: 20px; width: min(100%, var(--app-content-width, 1080px)); margin: 0 auto; padding: 32px 24px 40px; box-sizing: border-box; color: #172033; font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif; letter-spacing: 0; text-align: left; }
header { text-align: center; }
.eyebrow { margin: 0 0 8px; color: #1c3d70; font-size: 13px; font-weight: 900; }
h1 { margin: 0; color: #0b1f3a; font-size: 34px; line-height: 1.25; font-weight: 900; letter-spacing: 0; }
header > p:last-child { margin: 10px 0 0; color: #667085; }
.reward-progress { display: grid; gap: 12px; padding: 20px; border: 1px solid #cfe2d5; border-radius: 8px; background: #f7fbf8; }
.section-title { text-align: left; }
.section-title h2 { margin: 0; font-size: 19px; }
.section-title p { margin: 5px 0 0; color: #667085; font-size: 13px; }
.progress-row { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(220px, 1fr) minmax(200px, 1.2fr); gap: 18px; align-items: center; padding: 14px 0; border-top: 1px solid #dce9e0; }
.progress-row > div { display: grid; gap: 4px; }
.progress-row span, .progress-row small { color: #667085; font-size: 12px; }
.progress-copy b { color: #166534; }
progress { width: 100%; height: 12px; accent-color: #16803c; }
.benefit-list { display: grid; gap: 12px; }
.benefit-row { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(260px, 1.2fr) minmax(300px, 1.5fr) auto; gap: 18px; align-items: center; min-height: 112px; padding: 20px; border: 1px solid #dce5f1; border-radius: 8px; background: #fff; box-shadow: 0 8px 22px rgba(15, 42, 70, .05); }
.site-copy { display: grid; gap: 5px; }
.site-copy strong { font-size: 17px; }
.site-copy span, small { color: #667085; font-size: 12px; }
dl { display: flex; gap: 22px; margin: 0; }
dl > div { display: grid; gap: 4px; }
dt { color: #667085; font-size: 12px; }
dd { margin: 0; font-weight: 900; }
.preference-list { display: grid; gap: 8px; }
label { display: flex; align-items: center; gap: 9px; font-size: 13px; font-weight: 700; }
input { width: 18px; height: 18px; }
button { min-width: 112px; min-height: 40px; border: 1px solid #2454d6; border-radius: 6px; background: #2454d6; color: #fff; font: inherit; font-size: 13px; font-weight: 900; cursor: pointer; }
button:hover:not(:disabled) { border-color: #174d82; background: #174d82; }
button:disabled { opacity: .55; cursor: wait; }
.state { margin: 0; padding: 28px; border: 1px solid #dce5f1; border-radius: 8px; background: #fff; color: #667085; text-align: center; }
.state.error { color: #b42318; }
.state.success { padding: 12px 16px; border-color: #a8d5b4; background: #f2faf4; color: #166534; }
.coupon-list { grid-column: 1 / -1; display: grid; gap: 0; padding-top: 14px; border-top: 1px solid #e3e9f1; }
.coupon-list-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 8px; }
.coupon-list-title span { color: #667085; font-size: 12px; }
.coupon-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 54px; padding: 9px 0; border-top: 1px solid #edf1f5; }
.coupon-row > div { display: grid; gap: 3px; }
.coupon-row > div:last-child { flex: 0 0 auto; text-align: right; }
.coupon-row span, .coupon-row small { color: #667085; font-size: 12px; }
.coupon-row b { color: #166534; }
@media (max-width: 1080px) { .benefit-row { grid-template-columns: 1fr 1fr; } }
@media (max-width: 680px) { .benefit-page { padding: 22px 16px 32px; } .benefit-row, .progress-row { grid-template-columns: 1fr; } dl { flex-wrap: wrap; } button { width: 100%; } .coupon-row, .coupon-list-title { align-items: flex-start; flex-direction: column; } .coupon-row > div:last-child { text-align: left; } }
</style>
