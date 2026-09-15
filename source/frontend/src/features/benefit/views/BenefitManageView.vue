<template>
    <main class="benefit-page">
        <header class="page-header">
            <div>
                <p class="eyebrow">BENEFIT PROGRAM</p>
                <h1>마일리지·쿠폰 관리</h1>
            </div>
            <RouterLink class="back-link" :to="washSiteRoute">세차장 관리</RouterLink>
        </header>

        <p v-if="error" class="action-toast error-toast" role="alert">{{ error }}</p>
        <p v-if="success" class="action-toast success-toast" role="status">{{ success }}</p>

        <section class="site-filter">
            <label for="benefit-site">세차장</label>
            <select id="benefit-site" v-model="selectedSiteId" @change="loadBenefit">
                <option value="">선택</option>
                <option v-for="site in washSiteStore.washSites" :key="site.washSiteId" :value="String(site.washSiteId)">
                    {{ site.siteName }} (#{{ site.washSiteId }})
                </option>
            </select>
        </section>

        <template v-if="selectedSiteId">
            <section class="panel">
                <header class="section-header">
                    <div>
                        <h2>운영 정책</h2>
                        <p>운영을 끄면 신규 적립·발급·사용이 중지되며 기존 잔액과 쿠폰 이력은 유지됩니다.</p>
                    </div>
                    <label class="check-row">
                        <input v-model="policy.enabled" type="checkbox">
                        {{ policy.enabled ? "운영 중" : "운영 안 함" }}
                    </label>
                </header>

                <div v-if="policy.enabled" class="policy-grid">
                    <label class="check-row"><input v-model="policy.mileageEnabled" type="checkbox">마일리지 운영</label>
                    <label>적립률(%)<input v-model.number="mileageRatePercent" type="number" min="0" max="50" step="0.1" :disabled="!policy.mileageEnabled"></label>
                    <label>유효기간(일)<input v-model.number="policy.mileageExpireDays" type="number" min="1" max="3650" :disabled="!policy.mileageEnabled"></label>
                    <label class="check-row"><input v-model="policy.couponEnabled" type="checkbox">쿠폰 운영</label>
                </div>
                <button class="primary" type="button" :disabled="saving" @click="savePolicy">정책 저장</button>
            </section>

            <section v-if="policy.enabled && policy.couponEnabled" class="panel">
                <header class="section-header">
                    <div>
                        <h2>쿠폰 캠페인</h2>
                        <p>직접 지급 할인권과 이용 횟수 달성 무료 세차권을 설정합니다.</p>
                    </div>
                </header>

                <form class="campaign-form" @submit.prevent="saveCampaign">
                    <label>캠페인명<input v-model.trim="campaignForm.campaignName" type="text" maxlength="100" required></label>
                    <label>쿠폰 종류
                        <select v-model="campaignForm.couponType" @change="normalizeCampaign">
                            <option value="FIXED_DISCOUNT">정액 할인</option>
                            <option value="PERCENT_DISCOUNT">정률 할인</option>
                            <option value="FREE_WASH">무료 세차</option>
                        </select>
                    </label>
                    <label>지급 방식
                        <select v-model="campaignForm.issueMode" @change="normalizeCampaign">
                            <option value="MANUAL">회원에게 직접 지급</option>
                            <option value="VISIT_REWARD">이용 횟수 달성</option>
                        </select>
                    </label>
                    <label v-if="campaignForm.couponType !== 'FREE_WASH'">
                        {{ campaignForm.couponType === "PERCENT_DISCOUNT" ? "할인율(%)" : "할인금액(원)" }}
                        <input v-model.number="campaignDisplayValue" type="number" :min="campaignForm.couponType === 'PERCENT_DISCOUNT' ? 0.1 : 100" :step="campaignForm.couponType === 'PERCENT_DISCOUNT' ? 0.1 : 100" required>
                    </label>
                    <label v-if="campaignForm.couponType === 'PERCENT_DISCOUNT'">최대 할인금액(원)<input v-model.number="campaignForm.maxDiscountAmount" type="number" min="1" step="100"></label>
                    <label v-if="campaignForm.couponType !== 'FREE_WASH'">최소 결제금액(원)<input v-model.number="campaignForm.minimumPaymentAmount" type="number" min="0" step="100"></label>
                    <label v-if="campaignForm.issueMode === 'VISIT_REWARD'">필요 이용 횟수<input v-model.number="campaignForm.requiredPaidWashes" type="number" min="1" max="100" required></label>
                    <label>발급 후 유효기간(일)<input v-model.number="campaignForm.couponValidDays" type="number" min="1" max="3650" required></label>
                    <label>캠페인 시작일<input v-model="campaignForm.startsAt" type="datetime-local"></label>
                    <label>캠페인 종료일<input v-model="campaignForm.endsAt" type="datetime-local"></label>
                    <label class="check-row"><input v-model="campaignForm.active" type="checkbox">캠페인 활성화</label>
                    <div class="form-actions">
                        <button class="primary" type="submit" :disabled="saving">{{ campaignForm.couponCampaignId ? "캠페인 수정" : "캠페인 추가" }}</button>
                        <button type="button" @click="resetCampaignForm">입력 초기화</button>
                    </div>
                </form>

                <div class="campaign-list">
                    <article v-for="campaign in campaigns" :key="campaign.couponCampaignId" class="campaign-row">
                        <div><strong>{{ campaign.campaignName }}</strong><span>{{ campaignDescription(campaign) }}</span></div>
                        <span class="status">{{ campaign.active ? "운영 중" : "중지" }}</span>
                        <div class="row-actions">
                            <button type="button" @click="editCampaign(campaign)">수정</button>
                            <button v-if="campaign.issueMode === 'MANUAL'" type="button" @click="openIssue(campaign.couponCampaignId)">회원 지급</button>
                        </div>
                    </article>
                    <p v-if="campaigns.length === 0" class="state">등록된 쿠폰 캠페인이 없습니다.</p>
                </div>
            </section>

            <section v-if="issueCampaignId" class="panel issue-panel">
                <header class="section-header">
                    <div>
                        <h2>쿠폰 지급 대상</h2>
                        <p>{{ issueCampaign?.campaignName }} 쿠폰을 받을 고객을 선택합니다.</p>
                    </div>
                </header>

                <div class="audience-filter">
                    <label>대상 기준
                        <select v-model="issueAudienceType" @change="audienceTypeChanged">
                            <option v-for="option in audienceOptions" :key="option.value" :value="option.value">
                                {{ option.label }}
                            </option>
                        </select>
                    </label>
                    <label v-if="issueAudienceType === 'RECENT' || issueAudienceType === 'NEW'">기준 기간(일)
                        <input v-model.number="audienceFilters.recentDays" type="number" min="1" max="3650">
                    </label>
                    <label v-if="issueAudienceType === 'LOYAL'">집계 기간(일)
                        <input v-model.number="audienceFilters.loyalDays" type="number" min="1" max="3650">
                    </label>
                    <label v-if="issueAudienceType === 'LOYAL'">최소 이용 횟수
                        <input v-model.number="audienceFilters.loyalMinWashes" type="number" min="2" max="100">
                    </label>
                    <label v-if="issueAudienceType === 'DORMANT'">미방문 기간(일)
                        <input v-model.number="audienceFilters.dormantDays" type="number" min="1" max="3650">
                    </label>
                    <button v-if="issueAudienceType !== 'DIRECT'" type="button" :disabled="audienceLoading" @click="loadCouponAudience">
                        {{ audienceLoading ? "조회 중" : "대상 조회" }}
                    </button>
                </div>

                <p class="audience-help">{{ selectedAudienceDescription }}</p>

                <label v-if="issueAudienceType === 'DIRECT'">회원 번호
                    <textarea v-model="issueCustomerIds" rows="4" placeholder="예: 1, 2, 3 또는 줄바꿈으로 입력"></textarea>
                </label>

                <div v-else class="audience-list">
                    <div class="audience-list-header">
                        <label class="check-row">
                            <input type="checkbox" :checked="allAudienceSelected" :disabled="couponAudience.length === 0" @change="toggleAllAudience">
                            조회된 고객 전체 선택
                        </label>
                        <span>{{ selectedAudienceIds.length }} / {{ audienceTotal }}명 선택</span>
                    </div>
                    <label v-for="customer in couponAudience" :key="customer.customerId" class="audience-row">
                        <input v-model="selectedAudienceIds" type="checkbox" :value="customer.customerId">
                        <span><strong>{{ customer.customerName }}</strong><small>회원 #{{ customer.customerId }} · {{ customer.plateNumbers || "등록 차량 없음" }}</small></span>
                        <span><b>{{ customer.paidWashCount }}회</b><small>최근 {{ formatDate(customer.lastPaidAt) }}</small></span>
                    </label>
                    <p v-if="!audienceLoading && couponAudience.length === 0" class="state">조건에 맞고 아직 이 쿠폰을 보유하지 않은 고객이 없습니다.</p>
                </div>

                <small>해당 세차장에서 결제를 완료한 회원만 조회되며, 같은 캠페인의 사용 가능 쿠폰을 이미 가진 회원은 제외됩니다.</small>
                <div class="form-actions">
                    <button class="primary" type="button" :disabled="saving || audienceLoading" @click="issueCoupon">선택 고객에게 지급</button>
                    <button type="button" @click="closeIssue">취소</button>
                </div>
            </section>
        </template>
        <p v-else class="state">혜택을 설정할 세차장을 선택하세요.</p>
    </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useWashSiteStore } from "@/features/washSite/stores/washSiteStore"
import * as benefitApi from "../api/benefitApi"

const props = defineProps({ viewerRole: { type: String, required: true } })
const washSiteStore = useWashSiteStore()
const selectedSiteId = ref("")
const campaigns = ref([])
const saving = ref(false)
const error = ref("")
const success = ref("")
const issueCampaignId = ref(null)
const issueCustomerIds = ref("")
const issueAudienceType = ref("ALL")
const couponAudience = ref([])
const selectedAudienceIds = ref([])
const audienceLoading = ref(false)
const audienceFilters = reactive({ recentDays: 30, loyalDays: 90, loyalMinWashes: 3, dormantDays: 60 })
const policy = reactive({ enabled: false, mileageEnabled: false, mileageEarnBasisPoints: 0, mileageExpireDays: 365, couponEnabled: false })
const campaignForm = reactive(defaultCampaign())

const audienceOptions = [
    { value: "ALL", label: "해당 세차장 이용 고객 전체", description: "결제를 완료한 모든 고객" },
    { value: "RECENT", label: "최근 이용 고객", description: "기준 기간 안에 이용한 고객" },
    { value: "NEW", label: "신규 고객", description: "기준 기간 안에 처음 이용한 고객" },
    { value: "LOYAL", label: "단골 고객", description: "집계 기간 안에 최소 이용 횟수를 달성한 고객" },
    { value: "DORMANT", label: "장기 미방문 고객", description: "미방문 기간보다 오래 전에 마지막으로 이용한 고객" },
    { value: "DIRECT", label: "특정 회원 직접 선택", description: "알고 있는 회원 번호를 직접 입력" }
]

const washSiteRoute = computed(() => ({ name: props.viewerRole === "ADMIN" ? "admin-wash-sites" : "owner-wash-sites" }))
const mileageRatePercent = computed({
    get: () => Number(policy.mileageEarnBasisPoints || 0) / 100,
    set: value => { policy.mileageEarnBasisPoints = Math.round(Number(value || 0) * 100) }
})
const campaignDisplayValue = computed({
    get: () => campaignForm.couponType === "PERCENT_DISCOUNT" ? Number(campaignForm.discountValue || 0) / 100 : Number(campaignForm.discountValue || 0),
    set: value => { campaignForm.discountValue = campaignForm.couponType === "PERCENT_DISCOUNT" ? Math.round(Number(value || 0) * 100) : Math.round(Number(value || 0)) }
})
const issueCampaign = computed(() => campaigns.value.find(campaign => Number(campaign.couponCampaignId) === Number(issueCampaignId.value)))
const audienceTotal = computed(() => Number(couponAudience.value[0]?.totalMatches || couponAudience.value.length))
const allAudienceSelected = computed(() => couponAudience.value.length > 0 && selectedAudienceIds.value.length === couponAudience.value.length)
const selectedAudienceDescription = computed(() => audienceOptions.find(option => option.value === issueAudienceType.value)?.description || "")

onMounted(async () => {
    await washSiteStore.fetchWashSites(props.viewerRole)
    if (washSiteStore.washSites.length) {
        selectedSiteId.value = String(washSiteStore.washSites[0].washSiteId)
        await loadBenefit()
    }
})

async function loadBenefit() {
    if (!selectedSiteId.value) return
    error.value = ""
    try {
        const [loadedPolicy, loadedCampaigns] = await Promise.all([
            benefitApi.getBenefitPolicy(props.viewerRole, selectedSiteId.value),
            benefitApi.getBenefitCampaigns(props.viewerRole, selectedSiteId.value)
        ])
        Object.assign(policy, loadedPolicy)
        campaigns.value = loadedCampaigns || []
        resetCampaignForm()
    } catch (e) {
        error.value = e?.response?.data?.message || "혜택 정보를 불러오지 못했습니다."
    }
}

async function savePolicy() {
    saving.value = true
    error.value = ""
    success.value = ""
    try {
        Object.assign(policy, await benefitApi.saveBenefitPolicy(props.viewerRole, selectedSiteId.value, {
            enabled: policy.enabled,
            mileageEnabled: policy.enabled && policy.mileageEnabled,
            mileageEarnBasisPoints: policy.mileageEarnBasisPoints,
            mileageExpireDays: policy.mileageExpireDays,
            couponEnabled: policy.enabled && policy.couponEnabled
        }))
        success.value = "혜택 운영 정책이 저장되었습니다."
    } catch (e) {
        error.value = e?.response?.data?.message || "혜택 정책을 저장하지 못했습니다."
    } finally { saving.value = false }
}

async function saveCampaign() {
    normalizeCampaign()
    if (campaignForm.startsAt && campaignForm.endsAt && campaignForm.startsAt >= campaignForm.endsAt) {
        error.value = "캠페인 종료일은 시작일 이후여야 합니다."
        return
    }
    saving.value = true
    error.value = ""
    success.value = ""
    try {
        const isUpdate = Boolean(campaignForm.couponCampaignId)
        const payload = {
            ...campaignForm,
            washSiteId: Number(selectedSiteId.value),
            startsAt: campaignForm.startsAt || null,
            endsAt: campaignForm.endsAt || null
        }
        if (campaignForm.couponCampaignId) await benefitApi.updateBenefitCampaign(props.viewerRole, campaignForm.couponCampaignId, payload)
        else await benefitApi.createBenefitCampaign(props.viewerRole, payload)
        campaigns.value = await benefitApi.getBenefitCampaigns(props.viewerRole, selectedSiteId.value)
        resetCampaignForm()
        success.value = isUpdate ? "쿠폰 캠페인이 수정되었습니다." : "쿠폰 캠페인이 추가되었습니다."
    } catch (e) {
        error.value = e?.response?.data?.message || "쿠폰 캠페인을 저장하지 못했습니다."
    } finally { saving.value = false }
}

function normalizeCampaign() {
    if (campaignForm.couponType === "FREE_WASH") {
        campaignForm.discountValue = 0
        campaignForm.maxDiscountAmount = null
        campaignForm.minimumPaymentAmount = 0
    }
    if (campaignForm.couponType !== "PERCENT_DISCOUNT") campaignForm.maxDiscountAmount = null
    if (campaignForm.issueMode === "MANUAL") campaignForm.requiredPaidWashes = null
    if (campaignForm.issueMode === "VISIT_REWARD") {
        campaignForm.couponType = "FREE_WASH"
        campaignForm.discountValue = 0
        campaignForm.requiredPaidWashes ||= 5
    }
}
function defaultCampaign() { return { couponCampaignId: null, campaignName: "", couponType: "FIXED_DISCOUNT", issueMode: "MANUAL", discountValue: 1000, maxDiscountAmount: null, minimumPaymentAmount: 0, requiredPaidWashes: null, couponValidDays: 30, startsAt: null, endsAt: null, active: true } }
function resetCampaignForm() { Object.assign(campaignForm, defaultCampaign()) }
function editCampaign(campaign) {
    Object.assign(campaignForm, defaultCampaign(), campaign, {
        startsAt: toDateTimeInput(campaign.startsAt),
        endsAt: toDateTimeInput(campaign.endsAt)
    })
}
function openIssue(campaignId) {
    issueCampaignId.value = campaignId
    issueCustomerIds.value = ""
    issueAudienceType.value = "ALL"
    couponAudience.value = []
    selectedAudienceIds.value = []
    loadCouponAudience()
}
function closeIssue() {
    issueCampaignId.value = null
    issueCustomerIds.value = ""
    couponAudience.value = []
    selectedAudienceIds.value = []
}

function audienceTypeChanged() {
    couponAudience.value = []
    selectedAudienceIds.value = []
    if (issueAudienceType.value !== "DIRECT") loadCouponAudience()
}

async function loadCouponAudience() {
    if (!issueCampaignId.value || issueAudienceType.value === "DIRECT") return
    audienceLoading.value = true
    error.value = ""
    try {
        const customers = await benefitApi.getBenefitCouponAudience(
            props.viewerRole,
            issueCampaignId.value,
            { audienceType: issueAudienceType.value, ...audienceFilters, limit: 500 }
        )
        couponAudience.value = customers || []
        selectedAudienceIds.value = couponAudience.value.map(customer => customer.customerId)
    } catch (e) {
        couponAudience.value = []
        selectedAudienceIds.value = []
        error.value = e?.response?.data?.message || "쿠폰 지급 대상을 불러오지 못했습니다."
    } finally {
        audienceLoading.value = false
    }
}

function toggleAllAudience(event) {
    selectedAudienceIds.value = event.target.checked
        ? couponAudience.value.map(customer => customer.customerId)
        : []
}

async function issueCoupon() {
    const customerIds = issueAudienceType.value === "DIRECT"
        ? [...new Set(issueCustomerIds.value.split(/[\s,]+/).filter(Boolean).map(Number))]
        : [...new Set(selectedAudienceIds.value.map(Number))]
    if (!customerIds.length || customerIds.some(id => !Number.isInteger(id) || id <= 0)) {
        error.value = "유효한 회원 번호를 쉼표 또는 줄바꿈으로 입력하세요."
        return
    }
    if (customerIds.length > 500) {
        error.value = "쿠폰은 한 번에 최대 500명에게 지급할 수 있습니다."
        return
    }
    error.value = ""
    success.value = ""
    try {
        const result = await benefitApi.issueBenefitCoupons(props.viewerRole, issueCampaignId.value, customerIds)
        closeIssue()
        success.value = `${result.issuedCount || customerIds.length}명에게 쿠폰이 발급되었습니다.`
    } catch (e) { error.value = e?.response?.data?.message || "쿠폰을 지급하지 못했습니다." }
}

function campaignDescription(campaign) {
    const benefit = campaign.couponType === "FREE_WASH" ? "무료 세차" : campaign.couponType === "PERCENT_DISCOUNT" ? `${Number(campaign.discountValue || 0) / 100}% 할인` : `${Number(campaign.discountValue || 0).toLocaleString()}원 할인`
    const issue = campaign.issueMode === "VISIT_REWARD" ? `${campaign.requiredPaidWashes}회 결제 시 지급` : "직접 지급"
    return `${benefit} · ${issue} · 발급 후 ${campaign.couponValidDays}일`
}
function formatDate(value) { return value ? String(value).replace("T", " ").slice(0, 10) : "이용 기록 없음" }
function toDateTimeInput(value) { return value ? String(value).slice(0, 16) : null }
</script>

<style scoped>
.benefit-page { display: grid; gap: 18px; width: min(100%, var(--app-content-width, 1080px)); margin: 0 auto; padding: 28px 24px 42px; box-sizing: border-box; color: #172033; font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif; letter-spacing: 0; text-align: left; }
.page-header, .section-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.page-header { min-height: 64px; padding-bottom: 4px; }
.eyebrow { margin: 0 0 6px; color: #52749c; font-size: 11px; font-weight: 900; }
h1, h2 { margin: 0; color: #0b1f3a; letter-spacing: 0; } h1 { font-size: 30px; line-height: 1.25; font-weight: 900; } h2 { font-size: 18px; line-height: 1.35; font-weight: 900; }
.section-header p { margin: 5px 0 0; color: #667085; font-size: 13px; }
.back-link { display: inline-flex; align-items: center; min-height: 38px; padding: 0 13px; border: 1px solid #aebed2; border-radius: 6px; color: #174d82; font-size: 13px; font-weight: 900; text-decoration: none; }
.back-link:hover { border-color: #174d82; background: #f2f7fc; }
.site-filter, .panel { display: grid; gap: 16px; padding: 20px; border: 1px solid #d9e2f1; border-radius: 8px; background: #fff; box-shadow: 0 8px 22px rgba(15, 42, 70, .05); }
.site-filter { grid-template-columns: 100px minmax(220px, 420px); align-items: center; }
.policy-grid, .campaign-form { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
label { display: grid; gap: 7px; min-width: 0; color: #344054; font-size: 13px; font-weight: 800; }
input, select, textarea { width: 100%; box-sizing: border-box; border: 1px solid #bfcbd9; border-radius: 6px; padding: 0 11px; background: #fff; color: #172033; font: inherit; font-size: 14px; }
input, select { height: 42px; }
textarea { min-height: 96px; padding-block: 10px; resize: vertical; }
input:focus, select:focus, textarea:focus { border-color: #3973aa; outline: 3px solid rgba(57, 115, 170, .13); }
input[type="checkbox"] { width: 18px; height: 18px; }
.check-row { display: flex; align-items: center; gap: 9px; }
.campaign-list { display: grid; gap: 8px; }
.campaign-row { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 14px; min-height: 58px; padding: 13px 0; border-top: 1px solid #e5eaf1; }
.campaign-row > div:first-child { display: grid; gap: 4px; } .campaign-row span { color: #667085; font-size: 13px; }
.status { color: #166534 !important; font-weight: 800; }
.row-actions, .form-actions { display: flex; gap: 8px; align-items: center; } .form-actions { grid-column: 1 / -1; }
.audience-filter { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)) auto; gap: 12px; align-items: end; }
.audience-help { margin: -4px 0 0; color: #52657d; font-size: 13px; }
.audience-list { overflow: auto; max-height: 360px; border: 1px solid #d9e2f1; border-radius: 6px; }
.audience-list-header { position: sticky; top: 0; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 13px; border-bottom: 1px solid #d9e2f1; background: #f7f9fc; color: #52657d; font-size: 12px; }
.audience-row { grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; min-height: 58px; padding: 10px 13px; border-bottom: 1px solid #edf1f6; cursor: pointer; }
.audience-row:last-child { border-bottom: 0; }
.audience-row > span { display: grid; gap: 3px; }
.audience-row > span:last-child { text-align: right; }
.audience-row small { color: #7b8494; font-size: 11px; font-weight: 500; }
button { min-height: 40px; border: 1px solid #bfcbd9; border-radius: 6px; padding: 0 14px; background: #fff; color: #243b55; font: inherit; font-size: 13px; font-weight: 900; cursor: pointer; }
button:hover:not(:disabled) { border-color: #3973aa; background: #f2f7fc; }
button:disabled { opacity: .6; cursor: wait; }
button.primary { justify-self: start; border-color: #2454d6; background: #2454d6; color: #fff; }
button.primary:hover:not(:disabled) { border-color: #174d82; background: #174d82; }
.state { margin: 0; padding: 18px; color: #667085; text-align: center; } .state.error { color: #b42318; background: #fff1f1; border-radius: 6px; }
.action-toast { position: fixed; top: 84px; right: 24px; z-index: 1300; width: max-content; max-width: min(380px, calc(100vw - 32px)); margin: 0; padding: 13px 16px; border-radius: 6px; font-size: 14px; font-weight: 800; line-height: 1.45; box-shadow: 0 12px 30px rgba(28, 46, 39, .18); box-sizing: border-box; overflow-wrap: anywhere; }
.success-toast { border: 1px solid #9dc9aa; background: #f0faf3; color: #166534; }
.error-toast { border: 1px solid #e1a3a3; background: #fff1f1; color: #b42318; }
@media (max-width: 1060px) { .action-toast { top: auto; right: 16px; bottom: max(16px, env(safe-area-inset-bottom)); left: 16px; width: auto; max-width: none; } }
@media (max-width: 900px) { .policy-grid, .campaign-form, .audience-filter { grid-template-columns: 1fr; } .form-actions { grid-column: auto; } .campaign-row { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .benefit-page { padding: 18px 16px 32px; } .page-header, .section-header { align-items: flex-start; flex-direction: column; } .site-filter { grid-template-columns: 1fr; } }
</style>
