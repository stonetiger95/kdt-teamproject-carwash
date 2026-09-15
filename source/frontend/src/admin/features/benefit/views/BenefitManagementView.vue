<script setup>
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import { computed, onMounted, reactive, ref } from 'vue'
import * as benefitApi from '@/admin/features/benefit/api/benefitApi'

const owners = ref([])
const ALL_FILTER = 'ALL'
const washSites = ref([])
const campaigns = ref([])
const statuses = ref([])
const selectedOwnerId = ref(ALL_FILTER)
const selectedSiteId = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const campaignDialogVisible = ref(false)
const issueDialogVisible = ref(false)
const issueCampaign = ref(null)
const issueCustomerIds = ref('')
const issueAudienceType = ref('ALL')
const couponAudience = ref([])
const selectedAudienceIds = ref([])
const audienceLoading = ref(false)
const audienceFilters = reactive({ recentDays: 30, loyalDays: 90, loyalMinWashes: 3, dormantDays: 60 })
const policy = reactive(defaultPolicy())
const campaignForm = reactive(defaultCampaign())

const ownerOptions = computed(() => [{ ownerId: ALL_FILTER, ownerName: '전체 사업주' }, ...owners.value])
const siteOptions = computed(() => [{ washSiteId: '', siteName: '세차장 선택' }, ...washSites.value])
const selectedSite = computed(() => washSites.value.find((site) => Number(site.washSiteId) === Number(selectedSiteId.value)))
const mileageRate = computed({ get: () => Number(policy.mileageEarnBasisPoints || 0) / 100, set: (value) => { policy.mileageEarnBasisPoints = Math.round(Number(value || 0) * 100) } })
const campaignDiscountValue = computed({
  get: () => campaignForm.couponType === 'PERCENT_DISCOUNT' ? Number(campaignForm.discountValue || 0) / 100 : Number(campaignForm.discountValue || 0),
  set: (value) => { campaignForm.discountValue = campaignForm.couponType === 'PERCENT_DISCOUNT' ? Math.round(Number(value || 0) * 100) : Math.round(Number(value || 0)) },
})
const stats = computed(() => statuses.value.reduce((result, item) => {
  result.issued += Number(item.issuedCount || 0)
  result.available += Number(item.availableCount || 0)
  result.used += Number(item.usedCount || 0)
  result.expired += Number(item.expiredCount || 0)
  return result
}, { issued: 0, available: 0, used: 0, expired: 0 }))
const campaignTypes = [{ label: '정액 할인', value: 'FIXED_DISCOUNT' }, { label: '정률 할인', value: 'PERCENT_DISCOUNT' }, { label: '무료 세차', value: 'FREE_WASH' }]
const issueModes = [{ label: '관리자 직접 지급', value: 'MANUAL' }, { label: '방문 횟수 달성', value: 'VISIT_REWARD' }]
const audienceOptions = [
  { label: '해당 세차장 이용 고객 전체', value: 'ALL' },
  { label: '최근 이용 고객', value: 'RECENT' },
  { label: '신규 고객', value: 'NEW' },
  { label: '단골 고객', value: 'LOYAL' },
  { label: '장기 미방문 고객', value: 'DORMANT' },
  { label: '특정 회원 직접 선택', value: 'DIRECT' },
]
const audienceTotal = computed(() => Number(couponAudience.value[0]?.totalMatches || couponAudience.value.length))
const allAudienceSelected = computed(() => couponAudience.value.length > 0 && selectedAudienceIds.value.length === couponAudience.value.length)

onMounted(loadFilterOptions)

async function loadFilterOptions() {
  loading.value = true
  error.value = ''
  try {
    const [ownerData, siteData] = await Promise.all([benefitApi.getOwners(), benefitApi.getWashSites()])
    owners.value = ownerData || []
    washSites.value = siteData || []
    if (washSites.value.length) { selectedSiteId.value = washSites.value[0].washSiteId; await loadBenefits() }
  } catch (e) { error.value = apiMessage(e, '혜택 관리 대상을 불러오지 못했습니다.') } finally { loading.value = false }
}
async function ownerChanged() {
  loading.value = true
  error.value = ''
  try {
    washSites.value = await benefitApi.getWashSites(selectedOwnerId.value === ALL_FILTER ? undefined : selectedOwnerId.value) || []
    selectedSiteId.value = washSites.value[0]?.washSiteId || ''
    if (selectedSiteId.value) await loadBenefits()
    else { Object.assign(policy, defaultPolicy()); campaigns.value = []; statuses.value = [] }
  } catch (e) { error.value = apiMessage(e, '사업주의 세차장을 불러오지 못했습니다.') } finally { loading.value = false }
}
async function loadBenefits() {
  if (!selectedSiteId.value) return
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const [policyData, campaignData, statusData] = await Promise.all([
      benefitApi.getPolicy(selectedSiteId.value),
      benefitApi.getCampaigns(selectedSiteId.value),
      benefitApi.getCampaignStatuses(selectedSiteId.value),
    ])
    Object.assign(policy, defaultPolicy(), policyData || {})
    campaigns.value = campaignData || []
    statuses.value = statusData || []
  } catch (e) { error.value = apiMessage(e, '혜택 정보를 불러오지 못했습니다.') } finally { loading.value = false }
}
async function savePolicy() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const saved = await benefitApi.savePolicy(selectedSiteId.value, {
      enabled: Boolean(policy.enabled),
      mileageEnabled: Boolean(policy.enabled && policy.mileageEnabled),
      mileageEarnBasisPoints: Number(policy.mileageEarnBasisPoints || 0),
      mileageExpireDays: Number(policy.mileageExpireDays || 365),
      couponEnabled: Boolean(policy.enabled && policy.couponEnabled),
    })
    Object.assign(policy, defaultPolicy(), saved || {})
    success.value = '혜택 운영 정책이 저장되었습니다.'
  } catch (e) { error.value = apiMessage(e, '혜택 정책을 저장하지 못했습니다.') } finally { saving.value = false }
}
function openCreateCampaign() { Object.assign(campaignForm, defaultCampaign()); campaignDialogVisible.value = true }
function openEditCampaign(campaign) {
  Object.assign(campaignForm, defaultCampaign(), campaign, { startsAt: parseDate(campaign.startsAt), endsAt: parseDate(campaign.endsAt) })
  campaignDialogVisible.value = true
}
function normalizeCampaign() {
  if (campaignForm.couponType === 'FREE_WASH') { campaignForm.discountValue = 0; campaignForm.maxDiscountAmount = null }
  if (campaignForm.couponType !== 'PERCENT_DISCOUNT') campaignForm.maxDiscountAmount = null
  if (campaignForm.issueMode === 'MANUAL') campaignForm.requiredPaidWashes = null
  if (campaignForm.issueMode === 'VISIT_REWARD') { campaignForm.couponType = 'FREE_WASH'; campaignForm.discountValue = 0; campaignForm.maxDiscountAmount = null; campaignForm.requiredPaidWashes ||= 5 }
}
async function saveCampaign() {
  normalizeCampaign()
  if (campaignForm.startsAt && campaignForm.endsAt && campaignForm.startsAt > campaignForm.endsAt) { error.value = '캠페인 종료일은 시작일 이후여야 합니다.'; return }
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    ...campaignForm,
    washSiteId: Number(selectedSiteId.value),
    discountValue: Number(campaignForm.discountValue || 0),
    minimumPaymentAmount: Number(campaignForm.minimumPaymentAmount || 0),
    couponValidDays: Number(campaignForm.couponValidDays || 30),
    startsAt: localDateTime(campaignForm.startsAt),
    endsAt: localDateTime(campaignForm.endsAt),
  }
  try {
    const updating = Boolean(campaignForm.couponCampaignId)
    if (updating) await benefitApi.updateCampaign(campaignForm.couponCampaignId, payload)
    else await benefitApi.createCampaign(payload)
    campaignDialogVisible.value = false
    success.value = updating ? '쿠폰 캠페인이 수정되었습니다.' : '쿠폰 캠페인이 추가되었습니다.'
    await loadBenefits()
    success.value = updating ? '쿠폰 캠페인이 수정되었습니다.' : '쿠폰 캠페인이 추가되었습니다.'
  } catch (e) { error.value = apiMessage(e, '쿠폰 캠페인을 저장하지 못했습니다.') } finally { saving.value = false }
}
function openIssue(campaign) {
  issueCampaign.value = campaign
  issueCustomerIds.value = ''
  issueAudienceType.value = 'ALL'
  couponAudience.value = []
  selectedAudienceIds.value = []
  issueDialogVisible.value = true
  loadCouponAudience()
}
function audienceTypeChanged() {
  couponAudience.value = []
  selectedAudienceIds.value = []
  if (issueAudienceType.value !== 'DIRECT') loadCouponAudience()
}
async function loadCouponAudience() {
  if (!issueCampaign.value || issueAudienceType.value === 'DIRECT') return
  audienceLoading.value = true
  error.value = ''
  try {
    const customers = await benefitApi.getCouponAudience(issueCampaign.value.couponCampaignId, {
      audienceType: issueAudienceType.value,
      ...audienceFilters,
      limit: 500,
    })
    couponAudience.value = customers || []
    selectedAudienceIds.value = couponAudience.value.map((customer) => customer.customerId)
  } catch (e) {
    couponAudience.value = []
    selectedAudienceIds.value = []
    error.value = apiMessage(e, '쿠폰 지급 대상을 불러오지 못했습니다.')
  } finally { audienceLoading.value = false }
}
function toggleAllAudience(event) {
  selectedAudienceIds.value = event.target.checked
    ? couponAudience.value.map((customer) => customer.customerId)
    : []
}
async function issueCoupons() {
  const customerIds = issueAudienceType.value === 'DIRECT'
    ? [...new Set(issueCustomerIds.value.split(/[\s,]+/).map(Number).filter((value) => Number.isInteger(value) && value > 0))]
    : [...new Set(selectedAudienceIds.value.map(Number).filter((value) => Number.isInteger(value) && value > 0))]
  if (!customerIds.length) { error.value = '쿠폰을 지급할 회원 번호를 입력해 주세요.'; return }
  if (customerIds.length > 500) { error.value = '쿠폰은 한 번에 최대 500명에게 지급할 수 있습니다.'; return }
  saving.value = true
  error.value = ''
  try {
    const result = await benefitApi.issueCoupons(issueCampaign.value.couponCampaignId, customerIds)
    issueDialogVisible.value = false
    await loadBenefits()
    success.value = `${result?.issuedCount ?? customerIds.length}명에게 쿠폰이 지급되었습니다.`
  } catch (e) { error.value = apiMessage(e, '쿠폰을 지급하지 못했습니다.') } finally { saving.value = false }
}
async function expireCoupons() {
  saving.value = true
  error.value = ''
  try {
    const count = await benefitApi.expireCoupons(selectedSiteId.value)
    await loadBenefits()
    success.value = `만료 대상 쿠폰 ${Number(count || 0)}건을 정리했습니다.`
  } catch (e) { error.value = apiMessage(e, '쿠폰 만료 처리를 완료하지 못했습니다.') } finally { saving.value = false }
}

function defaultPolicy() { return { enabled: false, mileageEnabled: false, mileageEarnBasisPoints: 0, mileageExpireDays: 365, couponEnabled: false, updatedAt: null } }
function defaultCampaign() { return { couponCampaignId: null, campaignName: '', couponType: 'FIXED_DISCOUNT', issueMode: 'MANUAL', discountValue: 1000, maxDiscountAmount: null, minimumPaymentAmount: 0, requiredPaidWashes: null, couponValidDays: 30, startsAt: null, endsAt: null, active: true } }
function apiMessage(e, fallback) { return e.response?.data?.message || e.response?.data || fallback }
function parseDate(value) { return value ? new Date(value) : null }
function localDateTime(value) { if (!value) return null; const date = new Date(value); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 19) }
function dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '상시' }
function campaignType(value) { return campaignTypes.find((item) => item.value === value)?.label || value }
function issueMode(value) { return issueModes.find((item) => item.value === value)?.label || value }
function benefitValue(item) { if (item.couponType === 'FREE_WASH') return '무료 세차'; if (item.couponType === 'PERCENT_DISCOUNT') return `${Number(item.discountValue || 0) / 100}%`; return `${Number(item.discountValue || 0).toLocaleString('ko-KR')}원` }
function statusFor(campaignId) { return statuses.value.find((item) => Number(item.couponCampaignId) === Number(campaignId)) || {} }
function shortDate(value) { return value ? String(value).replace('T', ' ').slice(0, 10) : '기록 없음' }
</script>

<template>
  <section class="benefit-admin-page">
    <header class="benefit-page-header"><div><p>LOYALTY & COUPON</p><h1>혜택 관리</h1><span>세차장별 마일리지 정책과 쿠폰 캠페인·회원 지급 현황을 관리합니다.</span></div><Button label="만료 쿠폰 정리" icon="pi pi-clock" severity="secondary" outlined :disabled="!selectedSiteId" :loading="saving" @click="expireCoupons" /></header>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="success" severity="success" :closable="false">{{ success }}</Message>

    <section class="benefit-target-panel"><header><div><p>MANAGEMENT TARGET</p><h2>관리 대상 세차장</h2></div><span>{{ selectedSite?.siteName || '세차장을 선택하세요' }}</span></header><div><Select v-model="selectedOwnerId" :options="ownerOptions" option-label="ownerName" option-value="ownerId" @change="ownerChanged" /><Select v-model="selectedSiteId" :options="siteOptions" option-label="siteName" option-value="washSiteId" @change="loadBenefits" /><Button label="새로고침" icon="pi pi-refresh" severity="secondary" outlined :disabled="!selectedSiteId" :loading="loading" @click="loadBenefits" /></div></section>

    <div v-if="loading && !selectedSiteId" class="benefit-loading"><ProgressSpinner /><strong>혜택 정보를 불러오고 있습니다.</strong></div>
    <template v-else-if="selectedSiteId">
      <section class="benefit-summary-grid"><article><span class="blue"><i class="pi pi-ticket" /></span><div><small>전체 발급</small><strong>{{ stats.issued.toLocaleString() }}</strong></div></article><article><span class="green"><i class="pi pi-check-circle" /></span><div><small>사용 가능</small><strong>{{ stats.available.toLocaleString() }}</strong></div></article><article><span class="purple"><i class="pi pi-sparkles" /></span><div><small>사용 완료</small><strong>{{ stats.used.toLocaleString() }}</strong></div></article><article><span class="gray"><i class="pi pi-history" /></span><div><small>만료</small><strong>{{ stats.expired.toLocaleString() }}</strong></div></article></section>

      <section class="benefit-policy-panel"><header><div><p>BENEFIT POLICY</p><h2>운영 정책</h2><span v-if="policy.updatedAt">최종 변경 {{ dateTime(policy.updatedAt) }}</span></div><Tag :value="policy.enabled ? '혜택 운영 중' : '운영 안 함'" :severity="policy.enabled ? 'success' : 'secondary'" /></header><div class="benefit-policy-grid"><label class="switch-field"><span><strong>전체 혜택 운영</strong><small>끄면 적립·발급·사용을 중지하고 기존 이력은 유지합니다.</small></span><ToggleSwitch v-model="policy.enabled" /></label><label class="switch-field"><span><strong>마일리지 적립</strong><small>결제 완료 시 마일리지를 적립합니다.</small></span><ToggleSwitch v-model="policy.mileageEnabled" :disabled="!policy.enabled" /></label><label><span>적립률</span><InputNumber v-model="mileageRate" :min="0" :max="50" :min-fraction-digits="1" :max-fraction-digits="2" suffix="%" :disabled="!policy.enabled || !policy.mileageEnabled" /></label><label><span>마일리지 유효기간</span><InputNumber v-model="policy.mileageExpireDays" :min="1" :max="3650" suffix="일" :disabled="!policy.enabled || !policy.mileageEnabled" /></label><label class="switch-field"><span><strong>쿠폰 운영</strong><small>캠페인 발급과 쿠폰 사용을 허용합니다.</small></span><ToggleSwitch v-model="policy.couponEnabled" :disabled="!policy.enabled" /></label></div><footer><Button label="운영 정책 저장" icon="pi pi-save" :loading="saving" @click="savePolicy" /></footer></section>

      <section class="benefit-campaign-panel"><header><div><p>COUPON CAMPAIGNS</p><h2>쿠폰 캠페인</h2></div><div><span>{{ campaigns.length }}개 캠페인</span><Button label="캠페인 추가" icon="pi pi-plus" :disabled="!policy.enabled || !policy.couponEnabled" @click="openCreateCampaign" /></div></header><DataTable :value="campaigns" data-key="couponCampaignId" striped-rows scrollable :table-style="{ minWidth: '64rem' }" class="benefit-data-table"><Column field="campaignName" header="캠페인"><template #body="{ data }"><strong>{{ data.campaignName }}</strong><small>#{{ data.couponCampaignId }} · {{ issueMode(data.issueMode) }}</small></template></Column><Column field="couponType" header="혜택"><template #body="{ data }"><strong>{{ benefitValue(data) }}</strong><small>{{ campaignType(data.couponType) }}</small></template></Column><Column header="운영 기간"><template #body="{ data }"><span>{{ dateTime(data.startsAt) }}</span><small>~ {{ dateTime(data.endsAt) }}</small></template></Column><Column header="발급/사용"><template #body="{ data }"><strong>{{ Number(statusFor(data.couponCampaignId).issuedCount || 0).toLocaleString() }} / {{ Number(statusFor(data.couponCampaignId).usedCount || 0).toLocaleString() }}</strong><small>사용 가능 {{ Number(statusFor(data.couponCampaignId).availableCount || 0).toLocaleString() }}</small></template></Column><Column field="active" header="상태"><template #body="{ data }"><Tag :value="data.active ? '운영 중' : '중지'" :severity="data.active ? 'success' : 'secondary'" /></template></Column><Column header="관리" style="width:14rem"><template #body="{ data }"><div class="benefit-row-actions"><Button label="수정" icon="pi pi-pencil" size="small" text @click="openEditCampaign(data)" /><Button v-if="data.issueMode === 'MANUAL'" label="회원 지급" icon="pi pi-send" size="small" severity="secondary" outlined @click="openIssue(data)" /></div></template></Column><template #empty><div class="benefit-empty"><i class="pi pi-ticket" /><strong>등록된 쿠폰 캠페인이 없습니다.</strong><span>쿠폰 운영을 활성화한 뒤 첫 캠페인을 추가하세요.</span></div></template></DataTable></section>
    </template>
    <div v-else class="benefit-empty standalone"><i class="pi pi-building" /><strong>관리할 세차장이 없습니다.</strong><span>사업주와 세차장을 먼저 등록해 주세요.</span></div>

    <Dialog v-model:visible="campaignDialogVisible" modal :header="campaignForm.couponCampaignId ? '쿠폰 캠페인 수정' : '쿠폰 캠페인 추가'" :style="{ width:'min(96vw,820px)' }"><form class="campaign-form" @submit.prevent="saveCampaign"><div class="campaign-form-grid"><label class="wide"><span>캠페인명 <b>*</b></span><InputText v-model.trim="campaignForm.campaignName" maxlength="100" required /></label><label><span>쿠폰 종류 <b>*</b></span><Select v-model="campaignForm.couponType" :options="campaignTypes" option-label="label" option-value="value" @change="normalizeCampaign" /></label><label><span>지급 방식 <b>*</b></span><Select v-model="campaignForm.issueMode" :options="issueModes" option-label="label" option-value="value" @change="normalizeCampaign" /></label><label v-if="campaignForm.couponType !== 'FREE_WASH'"><span>{{ campaignForm.couponType === 'PERCENT_DISCOUNT' ? '할인율' : '할인금액' }} <b>*</b></span><InputNumber v-model="campaignDiscountValue" :min="campaignForm.couponType === 'PERCENT_DISCOUNT' ? 0.1 : 100" :step="campaignForm.couponType === 'PERCENT_DISCOUNT' ? 0.1 : 100" :suffix="campaignForm.couponType === 'PERCENT_DISCOUNT' ? '%' : '원'" required /></label><label v-if="campaignForm.couponType === 'PERCENT_DISCOUNT'"><span>최대 할인금액</span><InputNumber v-model="campaignForm.maxDiscountAmount" :min="1" suffix="원" /></label><label v-if="campaignForm.couponType !== 'FREE_WASH'"><span>최소 결제금액</span><InputNumber v-model="campaignForm.minimumPaymentAmount" :min="0" suffix="원" /></label><label v-if="campaignForm.issueMode === 'VISIT_REWARD'"><span>필요 이용 횟수 <b>*</b></span><InputNumber v-model="campaignForm.requiredPaidWashes" :min="1" :max="100" suffix="회" /></label><label><span>발급 후 유효기간 <b>*</b></span><InputNumber v-model="campaignForm.couponValidDays" :min="1" :max="3650" suffix="일" /></label><label><span>시작일</span><DatePicker v-model="campaignForm.startsAt" show-time hour-format="24" date-format="yy-mm-dd" show-icon /></label><label><span>종료일</span><DatePicker v-model="campaignForm.endsAt" show-time hour-format="24" date-format="yy-mm-dd" show-icon /></label><label class="switch-field wide"><span><strong>캠페인 활성화</strong><small>중지하면 새 쿠폰 발급 대상에서 제외됩니다.</small></span><ToggleSwitch v-model="campaignForm.active" /></label></div><footer><Button label="취소" severity="secondary" outlined type="button" @click="campaignDialogVisible=false" /><Button :label="campaignForm.couponCampaignId ? '변경사항 저장' : '캠페인 추가'" icon="pi pi-check" type="submit" :loading="saving" /></footer></form></Dialog>

    <Dialog v-model:visible="issueDialogVisible" modal header="회원 쿠폰 일괄 지급" :style="{ width:'min(96vw,760px)' }">
      <form class="issue-form" @submit.prevent="issueCoupons">
        <Message severity="info" :closable="false"><strong>{{ issueCampaign?.campaignName }}</strong> 쿠폰을 해당 세차장 이용 고객 중 최대 500명에게 지급합니다.</Message>
        <div class="issue-audience-filter">
          <label><span>대상 기준</span><select v-model="issueAudienceType" @change="audienceTypeChanged"><option v-for="option in audienceOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label v-if="issueAudienceType === 'RECENT' || issueAudienceType === 'NEW'"><span>기준 기간</span><InputNumber v-model="audienceFilters.recentDays" :min="1" :max="3650" suffix="일" /></label>
          <label v-if="issueAudienceType === 'LOYAL'"><span>집계 기간</span><InputNumber v-model="audienceFilters.loyalDays" :min="1" :max="3650" suffix="일" /></label>
          <label v-if="issueAudienceType === 'LOYAL'"><span>최소 이용</span><InputNumber v-model="audienceFilters.loyalMinWashes" :min="2" :max="100" suffix="회" /></label>
          <label v-if="issueAudienceType === 'DORMANT'"><span>미방문 기간</span><InputNumber v-model="audienceFilters.dormantDays" :min="1" :max="3650" suffix="일" /></label>
          <Button v-if="issueAudienceType !== 'DIRECT'" label="대상 조회" icon="pi pi-search" severity="secondary" outlined type="button" :loading="audienceLoading" @click="loadCouponAudience" />
        </div>
        <label v-if="issueAudienceType === 'DIRECT'"><span>회원 번호</span><Textarea v-model="issueCustomerIds" rows="7" auto-resize placeholder="회원 번호를 쉼표, 공백 또는 줄바꿈으로 구분하세요.&#10;예: 101, 102, 103" /></label>
        <div v-else class="issue-audience-list">
          <header><label><input type="checkbox" :checked="allAudienceSelected" :disabled="couponAudience.length === 0" @change="toggleAllAudience">조회된 고객 전체 선택</label><span>{{ selectedAudienceIds.length }} / {{ audienceTotal }}명 선택</span></header>
          <label v-for="customer in couponAudience" :key="customer.customerId" class="issue-audience-row"><input v-model="selectedAudienceIds" type="checkbox" :value="customer.customerId"><span><strong>{{ customer.customerName }}</strong><small>#{{ customer.customerId }} · {{ customer.plateNumbers || '등록 차량 없음' }}</small></span><span><strong>{{ customer.paidWashCount }}회</strong><small>최근 {{ shortDate(customer.lastPaidAt) }}</small></span></label>
          <div v-if="!audienceLoading && couponAudience.length === 0" class="benefit-empty compact"><strong>지급 가능한 고객이 없습니다.</strong><span>조건 또는 고객의 기존 쿠폰 보유 여부를 확인하세요.</span></div>
        </div>
        <small class="issue-note">결제 완료 이력이 있는 고객만 대상이며 동일 캠페인의 사용 가능한 쿠폰을 이미 가진 고객은 제외됩니다.</small>
        <footer><Button label="취소" severity="secondary" outlined type="button" @click="issueDialogVisible=false" /><Button label="선택 고객에게 지급" icon="pi pi-send" type="submit" :loading="saving" :disabled="audienceLoading" /></footer>
      </form>
    </Dialog>
  </section>
</template>

<style scoped>
.benefit-admin-page{display:grid;gap:18px}.benefit-page-header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.benefit-page-header p,.benefit-target-panel>header p,.benefit-policy-panel>header p,.benefit-campaign-panel>header p{margin:0 0 5px;color:#2563eb;font:700 10px Inter,Pretendard,sans-serif;letter-spacing:.13em}.benefit-page-header h1{margin:0 0 7px;color:#172033;font-size:25px}.benefit-page-header span{color:#7b8494;font-size:12px}.benefit-target-panel,.benefit-policy-panel,.benefit-campaign-panel{overflow:hidden;border:1px solid var(--border);border-radius:14px;background:#fff;box-shadow:0 3px 14px rgba(15,23,42,.03)}.benefit-target-panel>header,.benefit-policy-panel>header,.benefit-campaign-panel>header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 22px 14px;border-bottom:1px solid #edf0f4}.benefit-target-panel h2,.benefit-policy-panel h2,.benefit-campaign-panel h2{margin:0;color:#1d2939;font-size:15px}.benefit-target-panel>header>span,.benefit-campaign-panel>header>div:last-child>span{padding:6px 9px;border-radius:20px;color:#64748b;background:#f1f5f9;font-size:10px}.benefit-target-panel>div{display:grid;grid-template-columns:220px minmax(260px,1fr) auto;gap:10px;padding:15px 22px;background:#fafbfc}.benefit-target-panel .p-select{width:100%}.benefit-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.benefit-summary-grid article{display:flex;align-items:center;gap:13px;min-height:90px;padding:17px;border:1px solid var(--border);border-radius:14px;background:#fff}.benefit-summary-grid article>span{display:grid;place-items:center;width:42px;height:42px;border-radius:11px;font-size:18px}.benefit-summary-grid .blue{color:#2563eb;background:#dbeafe}.benefit-summary-grid .green{color:#16835f;background:#dff7ed}.benefit-summary-grid .purple{color:#7c3aed;background:#ede9fe}.benefit-summary-grid .gray{color:#64748b;background:#e9eef5}.benefit-summary-grid article>div{display:grid;gap:3px}.benefit-summary-grid small{color:#778195;font-size:11px}.benefit-summary-grid strong{color:#172033;font-size:22px}.benefit-policy-panel>header>div>span{display:block;margin-top:4px;color:#98a2b3;font-size:9px}.benefit-policy-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:13px;padding:20px 22px}.benefit-policy-grid>label,.campaign-form-grid>label,.issue-form>label{display:grid;gap:7px}.benefit-policy-grid>label>span,.campaign-form-grid>label>span,.issue-form>label>span{color:#475467;font-size:11px;font-weight:700}.benefit-policy-grid .p-inputnumber,.campaign-form-grid .p-inputnumber,.campaign-form-grid .p-inputtext,.campaign-form-grid .p-select,.campaign-form-grid .p-datepicker{width:100%}.switch-field{display:flex!important;align-items:center;justify-content:space-between;gap:12px;padding:13px;border:1px solid #e7ecf2;border-radius:10px;background:#fafbfc}.switch-field>span{display:grid;gap:3px}.switch-field strong{color:#344054;font-size:11px}.switch-field small{color:#98a2b3;font-size:9px;font-weight:400}.benefit-policy-panel>footer{display:flex;justify-content:flex-end;padding:0 22px 20px}.benefit-campaign-panel>header>div:last-child{display:flex;align-items:center;gap:9px}.benefit-data-table :deep(.p-datatable-thead>tr>th){color:#667085;background:#fff;font-size:11px}.benefit-data-table :deep(.p-datatable-tbody>tr>td){color:#475467;font-size:12px}.benefit-data-table strong{display:block;color:#344054}.benefit-data-table small{display:block;margin-top:4px;color:#98a2b3;font-size:10px}.benefit-row-actions{display:flex;align-items:center;gap:5px}.benefit-loading,.benefit-empty{min-height:260px;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#7b8494;font-size:11px}.benefit-empty.standalone{border:1px dashed #cad3e0;border-radius:14px;background:#fff}.benefit-empty i{color:#bdc8da;font-size:30px}.benefit-empty span{color:#98a2b3}.campaign-form,.issue-form{display:grid;gap:16px}.campaign-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:13px}.campaign-form-grid .wide{grid-column:1/-1}.campaign-form-grid label>span b{color:#dc2626}.campaign-form>footer,.issue-form>footer{display:flex;justify-content:flex-end;gap:9px}.issue-form .p-textarea{width:100%}@media(max-width:980px){.benefit-summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.benefit-policy-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:680px){.benefit-page-header,.benefit-target-panel>header,.benefit-policy-panel>header,.benefit-campaign-panel>header{align-items:stretch;flex-direction:column}.benefit-target-panel>div,.benefit-policy-grid,.campaign-form-grid{grid-template-columns:1fr}.campaign-form-grid .wide{grid-column:auto}.benefit-campaign-panel>header>div:last-child{justify-content:space-between}}@media(max-width:480px){.benefit-summary-grid{grid-template-columns:1fr}.benefit-target-panel>div{padding:14px}.benefit-policy-grid{padding:16px}}
.issue-audience-filter{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;align-items:end}
.issue-audience-filter select{width:100%;min-height:40px;border:1px solid #cbd5e1;border-radius:6px;padding:0 10px;background:#fff;color:#344054}
.issue-audience-list{overflow:auto;max-height:350px;border:1px solid #dce3ec;border-radius:8px}
.issue-audience-list>header{position:sticky;top:0;z-index:1;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 13px;border-bottom:1px solid #dce3ec;background:#f8fafc;color:#64748b;font-size:11px}
.issue-audience-list>header label,.issue-audience-row{display:flex;align-items:center;gap:10px}
.issue-audience-row{justify-content:space-between;padding:10px 13px;border-bottom:1px solid #edf1f5;cursor:pointer}
.issue-audience-row>span{display:grid;gap:3px;min-width:0}
.issue-audience-row>span:nth-child(2){margin-right:auto}
.issue-audience-row>span:last-child{text-align:right}
.issue-audience-row small{color:#98a2b3;font-size:10px}
.issue-audience-row input,.issue-audience-list>header input{width:16px;height:16px}
.benefit-empty.compact{min-height:130px}
.issue-note{color:#667085;font-size:10px}
@media(max-width:980px){.issue-audience-filter{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:680px){.issue-audience-filter{grid-template-columns:1fr}.issue-audience-row{align-items:flex-start;flex-wrap:wrap}.issue-audience-row>span:last-child{text-align:left}}
</style>

