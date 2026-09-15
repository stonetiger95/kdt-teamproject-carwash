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
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBillingManagement } from '@/admin/features/billing/composables/useBillingManagement'

const route = useRoute()
const router = useRouter()
const store = useBillingManagement()
const ALL_FILTER = 'ALL'
const activeTab = ref(route.name === 'admin-settlement' ? 'settlement' : 'payments')
const paymentFilters = reactive({ keyword: String(route.query.keyword || ''), status: ALL_FILTER, purpose: ALL_FILTER, method: ALL_FILTER, startDate: null, endDate: null })
const settlementFilters = reactive({ ownerId: ALL_FILTER, washSiteId: ALL_FILTER, periodType: 'DAILY', startDate: firstDay(), endDate: new Date() })
const detailDialogVisible = ref(false)
const refundSectionVisible = ref(false)
const reconciliationSectionVisible = ref(false)
const refundTarget = computed(() => store.paymentDetail.value)
const refundPolicy = ref(null)
const refundForm = reactive({ refundAmount: 0, cancelReason: '관리자 환불' })
const reconciliationForm = reactive({ resolution: '', paymentMethod: 'CARD', providerProcessedAt: null, reviewNote: '' })

const statusOptions = [{ label: '전체 상태', value: ALL_FILTER }, { label: '결제 완료', value: 'SUCCESS' }, { label: '결제 대기', value: 'WAITING' }, { label: '취소 완료', value: 'CANCELED' }, { label: '결제 실패', value: 'FAILED' }]
const purposeOptions = [{ label: '전체 구분', value: ALL_FILTER }, { label: '선불 충전', value: 'CHARGE' }, { label: '세차 결제', value: 'WASH_PAYMENT' }]
const methodOptions = [{ label: '전체 수단', value: ALL_FILTER }, { label: '카드', value: 'CARD' }, { label: '선불 지갑', value: 'PREPAID' }, { label: 'PayApp', value: 'PAYAPP' }]
const reconciliationOptions = [{ label: '결제 완료 확인', value: 'SUCCESS' }, { label: '미결제·취소 확인', value: 'CANCELED' }]
const payAppMethodOptions = [{ label: '신용카드', value: 'CARD' }, { label: '휴대전화', value: 'PHONE' }, { label: '계좌이체', value: 'BANK_TRANSFER' }, { label: '가상계좌', value: 'VIRTUAL_ACCOUNT' }, { label: '카카오페이', value: 'KAKAOPAY' }, { label: '네이버페이', value: 'NAVERPAY' }, { label: '토스페이', value: 'TOSSPAY' }, { label: '기타 PayApp', value: 'PAYAPP' }]
const periodOptions = [{ label: '일간', value: 'DAILY' }, { label: '주간', value: 'WEEKLY' }, { label: '월간', value: 'MONTHLY' }]
const ownerOptions = computed(() => [{ ownerId: ALL_FILTER, ownerName: '전체 사업주' }, ...store.owners.value])
const siteOptions = computed(() => [{ washSiteId: ALL_FILTER, siteName: settlementFilters.ownerId !== ALL_FILTER ? '선택 사업주의 전체 세차장' : '전체 세차장' }, ...store.washSites.value])
const visiblePayments = computed(() => store.payments.value.filter((item) => {
  const query = paymentFilters.keyword.trim().toLowerCase()
  const text = [item.orderId, item.orderName, item.customerName, item.customerId, item.plateNumber].filter(Boolean).join(' ').toLowerCase()
  return (!optionalFilter(paymentFilters.status) || item.status === paymentFilters.status) && (!optionalFilter(paymentFilters.purpose) || item.purpose === paymentFilters.purpose) && (!optionalFilter(paymentFilters.method) || item.method === paymentFilters.method) && (!query || text.includes(query))
}))
const paymentStats = computed(() => ({
  total: visiblePayments.value.length,
  successAmount: sum(visiblePayments.value.filter((p) => p.status === 'SUCCESS'), 'amount'),
  waiting: visiblePayments.value.filter((p) => p.status === 'WAITING').length,
  canceled: visiblePayments.value.filter((p) => p.status === 'CANCELED').length,
}))
const maxTrendAmount = computed(() => Math.max(1, ...(store.dashboard.value?.trends || []).map((item) => Number(item.totalPaidAmount || 0))))
const canRefund = computed(() => refundTarget.value?.status === 'SUCCESS' && Boolean(refundTarget.value?.refundablePaymentId || refundTarget.value?.paymentId))
const canReviewPayApp = computed(() => isPayAppReviewable(refundTarget.value))
const canSubmitReconciliation = computed(() => canReviewPayApp.value && Boolean(reconciliationForm.resolution && reconciliationForm.providerProcessedAt && reconciliationForm.reviewNote.trim() && (reconciliationForm.resolution !== 'SUCCESS' || reconciliationForm.paymentMethod)))
const maxRefund = computed(() => Number(refundPolicy.value?.payableAmount ?? refundTarget.value?.amount ?? 0))

onMounted(async () => {
  await store.loadFilterOptions()
  if (activeTab.value === 'settlement') await loadSettlement()
  else await searchPayments()
})
watch(() => route.name, async (name) => {
  const nextTab = name === 'admin-settlement' ? 'settlement' : 'payments'
  if (nextTab === activeTab.value) return
  activeTab.value = nextTab
  closePaymentDetail()
  store.error.value = ''
  store.successMessage.value = ''
  if (nextTab === 'settlement') {
    resetSettlementFilters()
    await store.loadFilterOptions()
    await loadSettlement()
  } else {
    resetPaymentFilters()
    await searchPayments()
  }
})

async function changeTab(value) {
  await router.push({ name: value === 'settlement' ? 'admin-settlement' : 'admin-payments' })
}
function resetPaymentFilters() { Object.assign(paymentFilters, { keyword: '', status: ALL_FILTER, purpose: ALL_FILTER, method: ALL_FILTER, startDate: null, endDate: null }) }
function resetSettlementFilters() { Object.assign(settlementFilters, { ownerId: ALL_FILTER, washSiteId: ALL_FILTER, periodType: 'DAILY', startDate: firstDay(), endDate: new Date() }) }
function optionalFilter(value) { return value === ALL_FILTER ? undefined : value || undefined }
function paymentParams() { return { status: optionalFilter(paymentFilters.status), purpose: optionalFilter(paymentFilters.purpose), method: optionalFilter(paymentFilters.method), keyword: paymentFilters.keyword.trim() || undefined, startDate: paymentFilters.startDate ? `${dateKey(paymentFilters.startDate)}T00:00:00` : undefined, endDate: paymentFilters.endDate ? `${dateKey(paymentFilters.endDate)}T23:59:59` : undefined } }
async function searchPayments() { await store.loadPayments(paymentParams()) }
async function ownerChanged() { settlementFilters.washSiteId = ALL_FILTER; await store.loadFilterOptions(optionalFilter(settlementFilters.ownerId) || '') }
function settlementParams() { return { ownerId: optionalFilter(settlementFilters.ownerId), washSiteId: optionalFilter(settlementFilters.washSiteId), periodType: settlementFilters.periodType, startDate: dateKey(settlementFilters.startDate), endDate: dateKey(settlementFilters.endDate) } }
async function loadSettlement() { if (!settlementFilters.startDate || !settlementFilters.endDate || settlementFilters.startDate > settlementFilters.endDate) return; await store.loadSettlement(settlementParams()) }
async function openPaymentDetail(event) {
  const payment = event?.data || event
  if (!payment?.paymentId) return
  detailDialogVisible.value = true
  refundSectionVisible.value = false
  reconciliationSectionVisible.value = false
  refundPolicy.value = null
  await store.loadPaymentDetail(payment.paymentId)
}
async function prepareRefund() {
  if (!refundTarget.value) return
  refundPolicy.value = await store.refundPolicy(refundTarget.value)
  refundForm.refundAmount = maxRefund.value
  refundForm.cancelReason = '관리자 환불'
  refundSectionVisible.value = true
}
async function submitRefund() {
  const amount = Number(refundForm.refundAmount)
  if (!Number.isInteger(amount) || amount <= 0 || amount > maxRefund.value || !refundForm.cancelReason.trim()) return
  const paymentId = refundTarget.value?.paymentId
  if (await store.refund(refundTarget.value, amount, refundForm.cancelReason.trim(), paymentParams())) {
    refundSectionVisible.value = false
    if (paymentId) await store.loadPaymentDetail(paymentId)
  }
}
function prepareReconciliation() {
  Object.assign(reconciliationForm, { resolution: '', paymentMethod: 'CARD', providerProcessedAt: null, reviewNote: '' })
  reconciliationSectionVisible.value = true
  refundSectionVisible.value = false
}
async function submitReconciliation() {
  const paymentId = refundTarget.value?.paymentId
  if (!paymentId || !canSubmitReconciliation.value) return

  const latestPayment = await store.loadPaymentDetail(paymentId)
  if (!isPayAppReviewable(latestPayment)) {
    reconciliationSectionVisible.value = false
    await store.loadPayments(paymentParams())
    store.error.value = latestPayment
      ? '이미 완료·취소·실패 처리된 결제입니다. 최신 상태를 불러왔습니다.'
      : '결제 상태를 다시 확인하지 못했습니다.'
    return
  }

  const payload = {
    resolution: reconciliationForm.resolution,
    paymentMethod: reconciliationForm.resolution === 'SUCCESS' ? reconciliationForm.paymentMethod : null,
    providerProcessedAt: localDateTimeValue(reconciliationForm.providerProcessedAt),
    reviewNote: reconciliationForm.reviewNote.trim(),
  }
  if (await store.reviewPayAppPayment(paymentId, payload, paymentParams())) {
    reconciliationSectionVisible.value = false
  }
}
function closePaymentDetail() {
  detailDialogVisible.value = false
  refundSectionVisible.value = false
  reconciliationSectionVisible.value = false
  refundPolicy.value = null
  store.paymentDetail.value = null
}
function sum(items, field) { return items.reduce((total, item) => total + Number(item[field] || 0), 0) }
function isPayAppReviewable(payment) { return payment?.status === 'WAITING' && String(payment?.paymentProvider || '').toUpperCase() === 'PAYAPP' && Boolean(payment?.providerTransactionId) }
function firstDay() { const now = new Date(); return new Date(now.getFullYear(), now.getMonth(), 1) }
function dateKey(value) { if (!value) return ''; const date = new Date(value); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function localDateTimeValue(value) { if (!value) return null; const date = new Date(value); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 19) }
function amount(value) { return value == null ? '—' : `${Number(value).toLocaleString('ko-KR')}원` }
function dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' }
function purpose(value) { return ({ CHARGE: '선불 충전', WASH_PAYMENT: '세차 결제' })[value] || '결제' }
function method(value) { return ({ CARD: '카드', PREPAID: '선불 지갑', PAYAPP: 'PayApp' })[value] || value || '—' }
function paymentStatus(value) { return ({ WAITING: '결제 대기', SUCCESS: '결제 완료', CANCELED: '취소 완료', FAILED: '결제 실패' })[value] || value || '미확인' }
function paymentSeverity(value) { return value === 'SUCCESS' ? 'success' : value === 'WAITING' ? 'warn' : value === 'FAILED' ? 'danger' : 'secondary' }
function trendWidth(value) { return `${Math.max(2, Math.min(100, Number(value || 0) / maxTrendAmount.value * 100))}%` }
</script>

<template>
  <section class="billing-admin-page">
    <header class="billing-page-header"><div><p>BILLING & SETTLEMENT</p><h1>결제·정산 관리</h1><span>결제 상태와 환불 업무, 기간별 매출 및 미결제 이상 내역을 관리합니다.</span></div></header>
    <Message v-if="store.error.value" severity="error" :closable="false">{{ store.error.value }}</Message>
    <Message v-if="store.successMessage.value" severity="success" :closable="false">{{ store.successMessage.value }}</Message>

    <Tabs :value="activeTab" class="billing-tabs" @update:value="changeTab"><TabList><Tab value="payments"><i class="pi pi-credit-card" /> 결제 관리</Tab><Tab value="settlement"><i class="pi pi-wallet" /> 정산 분석</Tab></TabList><TabPanels>
      <TabPanel value="payments">
        <section class="billing-summary-grid"><article><span class="blue"><i class="pi pi-list" /></span><div><small>조회 결제</small><strong>{{ store.error.value ? '—' : paymentStats.total }}</strong></div></article><article><span class="green"><i class="pi pi-check-circle" /></span><div><small>결제 완료 금액</small><strong>{{ store.error.value ? '—' : amount(paymentStats.successAmount) }}</strong></div></article><article><span class="orange"><i class="pi pi-clock" /></span><div><small>결제 대기</small><strong>{{ store.error.value ? '—' : paymentStats.waiting }}</strong></div></article><article><span class="red"><i class="pi pi-replay" /></span><div><small>취소 완료</small><strong>{{ store.error.value ? '—' : paymentStats.canceled }}</strong></div></article></section>
        <section class="billing-panel">
          <header><div><p>PAYMENT HISTORY</p><h2>결제 내역</h2></div><span>검색 결과 {{ visiblePayments.length }}건 · 행 선택 시 상세보기</span></header>
          <form class="payment-filter-bar" @submit.prevent="searchPayments"><span class="billing-search"><i class="pi pi-search" /><InputText v-model="paymentFilters.keyword" placeholder="주문번호, 고객, 차량번호 검색" /></span><Select v-model="paymentFilters.status" :options="statusOptions" option-label="label" option-value="value" /><Select v-model="paymentFilters.purpose" :options="purposeOptions" option-label="label" option-value="value" /><Select v-model="paymentFilters.method" :options="methodOptions" option-label="label" option-value="value" /><DatePicker v-model="paymentFilters.startDate" date-format="yy-mm-dd" placeholder="시작일" show-icon /><DatePicker v-model="paymentFilters.endDate" date-format="yy-mm-dd" placeholder="종료일" show-icon /><Button label="조회" icon="pi pi-search" type="submit" :loading="store.loading.value" /></form>
          <div v-if="store.loading.value && !store.payments.value.length" class="billing-loading"><ProgressSpinner /><strong>결제 내역을 불러오고 있습니다.</strong></div>
          <DataTable v-else :value="visiblePayments" paginator :rows="10" :rows-per-page-options="[10,20,50]" data-key="paymentId" striped-rows scrollable :table-style="{ minWidth: '68rem' }" class="billing-data-table billing-payment-table" @row-click="openPaymentDetail">
            <Column field="orderId" header="주문번호"><template #body="{ data }"><strong>{{ data.orderId || `#${data.paymentId}` }}</strong><small>{{ data.orderName || '상품명 없음' }}</small></template></Column>
            <Column field="purpose" header="구분"><template #body="{ data }">{{ purpose(data.purpose) }}</template></Column>
            <Column field="customerName" header="고객"><template #body="{ data }"><strong>{{ data.customerName || data.customerId || '—' }}</strong><small>{{ data.plateNumber || '차량 미연결' }}</small></template></Column>
            <Column field="amount" header="금액" sortable><template #body="{ data }"><strong>{{ amount(data.amount) }}</strong></template></Column>
            <Column field="method" header="수단"><template #body="{ data }">{{ method(data.method) }}</template></Column>
            <Column field="status" header="상태" sortable><template #body="{ data }"><Tag :value="paymentStatus(data.status)" :severity="paymentSeverity(data.status)" /></template></Column>
            <Column field="approvedAt" header="승인일"><template #body="{ data }">{{ dateTime(data.approvedAt) }}</template></Column>
            <template #empty><div class="billing-empty"><i class="pi pi-credit-card" /><strong>{{ store.error.value ? '결제 데이터를 표시할 수 없습니다.' : '조건에 맞는 결제 내역이 없습니다.' }}</strong></div></template>
          </DataTable>
        </section>
      </TabPanel>

      <TabPanel value="settlement">
        <section class="billing-panel settlement-panel"><header><div><p>SETTLEMENT ANALYTICS</p><h2>정산 조회 조건</h2></div><span v-if="store.dashboard.value">{{ store.dashboard.value.startDate }} ~ {{ store.dashboard.value.endDate }}</span></header><form class="settlement-filter-bar" @submit.prevent="loadSettlement"><Select v-model="settlementFilters.ownerId" :options="ownerOptions" option-label="ownerName" option-value="ownerId" @change="ownerChanged" /><Select v-model="settlementFilters.washSiteId" :options="siteOptions" option-label="siteName" option-value="washSiteId" /><Select v-model="settlementFilters.periodType" :options="periodOptions" option-label="label" option-value="value" /><DatePicker v-model="settlementFilters.startDate" date-format="yy-mm-dd" show-icon /><DatePicker v-model="settlementFilters.endDate" date-format="yy-mm-dd" show-icon /><Button label="정산 조회" icon="pi pi-chart-bar" type="submit" :loading="store.loading.value" /></form></section>
        <div v-if="store.loading.value && !store.dashboard.value" class="billing-loading standalone"><ProgressSpinner /><strong>정산 데이터를 집계하고 있습니다.</strong></div>
        <template v-else-if="store.dashboard.value"><section class="settlement-summary-grid"><article><small>총 이용금액</small><strong>{{ amount(store.dashboard.value.totalUseAmount) }}</strong><span>{{ store.dashboard.value.totalUseCount ?? '—' }}건</span></article><article><small>결제 완료금액</small><strong>{{ amount(store.dashboard.value.totalPaidAmount) }}</strong><span>{{ store.dashboard.value.successPaymentCount ?? '—' }}건</span></article><article class="warning"><small>결제 대기</small><strong>{{ store.dashboard.value.waitingPaymentCount ?? '—' }}건</strong><span>확인 필요</span></article><article class="danger"><small>이상 내역</small><strong>{{ store.dashboard.value.abnormalLogCount ?? '—' }}건</strong><span>미결제·상태 불일치</span></article><article><small>결제 전환율</small><strong>{{ store.dashboard.value.conversionRate == null ? '—' : `${Number(store.dashboard.value.conversionRate).toFixed(1)}%` }}</strong><span>{{ store.dashboard.value.uniqueCustomerCount ?? '—' }}명 이용</span></article></section>
          <section class="settlement-dashboard-grid"><article class="settlement-trend-card"><header><div><p>REVENUE TREND</p><h3>기간별 결제 추이</h3></div><span>{{ store.dashboard.value.trends?.length || 0 }}개 구간</span></header><div v-if="store.dashboard.value.trends?.length" class="settlement-trend-list"><div v-for="item in store.dashboard.value.trends" :key="item.periodLabel"><time>{{ item.periodLabel }}</time><span><i :style="{ width: trendWidth(item.totalPaidAmount) }" /></span><strong>{{ amount(item.totalPaidAmount) }}</strong><small>{{ item.totalUseCount ?? '—' }}건</small></div></div><div v-else class="small-billing-empty">집계된 기간별 데이터가 없습니다.</div></article><article class="settlement-site-card"><header><div><p>SITE COMPARISON</p><h3>세차장별 결제 비교</h3></div><span>{{ store.comparisons.value.length }}곳</span></header><div v-if="store.comparisons.value.length" class="settlement-site-list"><div v-for="site in store.comparisons.value" :key="site.washSiteId"><span><strong>{{ site.siteName || `세차장 #${site.washSiteId}` }}</strong><small>{{ site.ownerName || '사업주 미확인' }} · {{ site.totalUseCount ?? '—' }}건</small></span><b>{{ amount(site.totalPaidAmount) }}</b><Tag v-if="site.waitingPaymentCount" :value="`대기 ${site.waitingPaymentCount}`" severity="warn" /></div></div><div v-else class="small-billing-empty">비교할 세차장 데이터가 없습니다.</div></article></section>
          <section class="billing-panel abnormal-panel"><header><div><p>ABNORMAL TRANSACTIONS</p><h2>이상·미결제 내역</h2></div><span>{{ store.dashboard.value.abnormalLogs?.length || 0 }}건</span></header><DataTable :value="store.dashboard.value.abnormalLogs || []" data-key="washHistoryId" scrollable :table-style="{ minWidth: '58rem' }" class="billing-data-table"><Column field="washHistoryId" header="세차 ID"><template #body="{ data }">#{{ data.washHistoryId }}</template></Column><Column field="plateNumber" header="차량번호" /><Column field="customerType" header="구분"><template #body="{ data }">{{ data.customerType === 'MEMBER' ? '회원' : '비회원' }}</template></Column><Column field="entryTime" header="입차"><template #body="{ data }">{{ dateTime(data.entryTime) }}</template></Column><Column field="paymentStatus" header="결제 상태"><template #body="{ data }"><Tag :value="paymentStatus(data.paymentStatus)" :severity="paymentSeverity(data.paymentStatus)" /></template></Column><Column field="washStatus" header="세차 상태" /><Column field="reason" header="이상 사유" /><template #empty><div class="small-billing-empty">이상 내역이 없습니다.</div></template></DataTable></section>
        </template>
      </TabPanel>
    </TabPanels></Tabs>

    <Dialog v-model:visible="detailDialogVisible" modal :header="`결제 상세 #${refundTarget?.paymentId || ''}`" class="billing-detail-dialog" :style="{ width:'min(94vw,860px)' }" @hide="closePaymentDetail">
      <div v-if="store.detailLoading.value" class="billing-detail-loading"><ProgressSpinner /><strong>결제 상세 정보를 불러오고 있습니다.</strong></div>
      <Message v-else-if="!refundTarget" severity="error" :closable="false">결제 상세 정보를 표시할 수 없습니다.</Message>
      <div v-else class="billing-detail-content">
        <header class="billing-detail-summary">
          <div><small>{{ purpose(refundTarget.purpose) }}</small><h3>{{ refundTarget.orderName || refundTarget.orderId || `결제 #${refundTarget.paymentId}` }}</h3><span>{{ refundTarget.orderId || '주문번호 없음' }}</span></div>
          <div><strong>{{ amount(refundTarget.amount) }}</strong><Tag :value="paymentStatus(refundTarget.status)" :severity="paymentSeverity(refundTarget.status)" /></div>
        </header>

        <dl class="billing-detail-grid">
          <div><dt>고객</dt><dd>{{ refundTarget.customerName || refundTarget.customerId || '—' }}</dd><small>{{ refundTarget.customerEmail || '이메일 없음' }}</small></div>
          <div><dt>차량번호</dt><dd>{{ refundTarget.plateNumber || '차량 미연결' }}</dd><small>세차 내역 {{ refundTarget.washHistoryId ? `#${refundTarget.washHistoryId}` : '미연결' }}</small></div>
          <div><dt>결제수단</dt><dd>{{ method(refundTarget.method) }}</dd><small>{{ refundTarget.paymentProvider || '결제사 미확인' }}</small></div>
          <div><dt>PG 거래번호</dt><dd>{{ refundTarget.providerTransactionId || refundTarget.payAppPaymentId || '—' }}</dd><small>{{ refundTarget.providerStatus || 'PG 상태 미확인' }}</small></div>
        </dl>

        <section class="billing-payment-timeline">
          <h4>처리 시각</h4>
          <div><article><i class="pi pi-send" /><span>결제 요청</span><strong>{{ dateTime(refundTarget.requestedAt) }}</strong></article><article><i class="pi pi-check-circle" /><span>결제 승인</span><strong>{{ dateTime(refundTarget.approvedAt) }}</strong></article><article><i class="pi pi-times-circle" /><span>결제 취소</span><strong>{{ dateTime(refundTarget.canceledAt) }}</strong></article></div>
          <Message v-if="refundTarget.cancelReason" severity="secondary" :closable="false">취소 사유: {{ refundTarget.cancelReason }}</Message>
        </section>

        <footer class="billing-detail-actions">
          <a v-if="refundTarget.receiptUrl" class="billing-receipt-link" :href="refundTarget.receiptUrl" target="_blank" rel="noopener noreferrer"><i class="pi pi-receipt" /> 영수증 보기</a>
          <Button v-if="canRefund && !refundSectionVisible" label="환불 처리" icon="pi pi-replay" severity="danger" outlined @click="prepareRefund" />
          <Button v-if="canReviewPayApp && !reconciliationSectionVisible" label="PayApp 상태 수동 확인" icon="pi pi-verified" severity="warn" outlined @click="prepareReconciliation" />
        </footer>

        <form v-if="reconciliationSectionVisible" class="billing-refund-form billing-reconciliation-form" @submit.prevent="submitReconciliation">
          <Message severity="warn" :closable="false">PayApp 관리자에서 거래번호 {{ refundTarget.providerTransactionId }}와 {{ amount(refundTarget.amount) }}을 대조한 뒤에만 반영하세요. 잘못 확정하면 잔액 또는 정산이 달라질 수 있습니다.</Message>
          <label><span>확인 결과</span><Select v-model="reconciliationForm.resolution" :options="reconciliationOptions" option-label="label" option-value="value" placeholder="PayApp 확인 결과 선택" /></label>
          <label v-if="reconciliationForm.resolution === 'SUCCESS'"><span>실제 결제수단</span><Select v-model="reconciliationForm.paymentMethod" :options="payAppMethodOptions" option-label="label" option-value="value" /></label>
          <label><span>PayApp 처리 시각</span><DatePicker v-model="reconciliationForm.providerProcessedAt" show-time hour-format="24" date-format="yy-mm-dd" placeholder="관리자 결제내역의 처리 시각" /></label>
          <label><span>확인 메모</span><Textarea v-model.trim="reconciliationForm.reviewNote" rows="3" maxlength="500" placeholder="PayApp 관리자에서 확인한 상태와 근거를 입력하세요." /></label>
          <footer><Button label="확인 취소" severity="secondary" outlined type="button" @click="reconciliationSectionVisible=false" /><Button label="검토 결과 반영" icon="pi pi-check" severity="warn" type="submit" :loading="store.actionLoading.value" :disabled="!canSubmitReconciliation" /></footer>
        </form>

        <form v-if="refundSectionVisible" class="billing-refund-form billing-detail-refund" @submit.prevent="submitRefund">
          <Message severity="warn" :closable="false">환불은 결제 상태와 정산 금액에 반영됩니다. 주문과 금액을 확인해 주세요.</Message>
          <Message v-if="refundPolicy?.partialRefundAllowed === false" severity="info" :closable="false">마일리지가 사용된 결제로 전액 환불만 가능합니다.</Message>
          <label><span>환불 금액</span><InputNumber v-model="refundForm.refundAmount" :min="1" :max="maxRefund" suffix="원" :readonly="refundPolicy?.partialRefundAllowed === false" /></label>
          <small>최대 환불 가능 금액 {{ amount(maxRefund) }}</small>
          <label><span>환불 사유</span><InputText v-model.trim="refundForm.cancelReason" maxlength="500" required /></label>
          <footer><Button label="환불 취소" severity="secondary" outlined type="button" @click="refundSectionVisible=false" /><Button label="환불 실행" icon="pi pi-exclamation-triangle" severity="danger" type="submit" :loading="store.actionLoading.value" :disabled="!canRefund || !refundForm.cancelReason.trim() || refundForm.refundAmount <= 0" /></footer>
        </form>
      </div>
    </Dialog>
  </section>
</template>

