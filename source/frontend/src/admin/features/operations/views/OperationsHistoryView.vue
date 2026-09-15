<script setup>
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOperationsManagement } from '@/admin/features/operations/composables/useOperationsManagement'

const route = useRoute(), router = useRouter(), store = useOperationsManagement()
const ALL_FILTER = 'ALL'
const activeTab = ref(route.name === 'admin-wash-history' || route.name === 'admin-wash-history-detail' ? 'history' : 'reservations')
const reservationFilters = reactive({ ownerId: ALL_FILTER, washSiteId: ALL_FILTER, status: ALL_FILTER, plateNumber: '', startDate: null, endDate: null })
const historyFilters = reactive({ ownerId: ALL_FILTER, washSiteId: ALL_FILTER, plateNumber: '', customerType: ALL_FILTER, washStatus: ALL_FILTER, paymentStatus: ALL_FILTER, startDate: firstDay(), endDate: new Date() })
const reservationStatuses = [{ label: '전체 상태', value: ALL_FILTER }, { label: '결제 대기', value: 'PENDING_PAYMENT' }, { label: '예약 확정', value: 'CONFIRMED' }, { label: '이용 완료', value: 'FULFILLED' }, { label: '예약 취소', value: 'CANCELED' }, { label: '노쇼', value: 'NO_SHOW' }]
const customerTypes = [{ label: '전체 고객', value: ALL_FILTER }, { label: '회원', value: 'MEMBER' }, { label: '비회원', value: 'NON_MEMBER' }]
const washStatuses = [{ label: '전체 세차 상태', value: ALL_FILTER }, { label: '입차', value: 'ENTERED' }, { label: '세차 중', value: 'WASHING' }, { label: '세차 완료', value: 'WASH_DONE' }, { label: '출차', value: 'EXITED' }]
const paymentStatuses = [{ label: '전체 결제 상태', value: ALL_FILTER }, { label: '결제 대기', value: 'WAITING' }, { label: '결제 완료', value: 'SUCCESS' }, { label: '결제 실패', value: 'FAILED' }, { label: '결제 취소', value: 'CANCELED' }]
const ownerOptions = computed(() => [{ ownerId: ALL_FILTER, ownerName: '전체 사업주' }, ...store.owners.value])
const siteOptions = computed(() => [{ washSiteId: ALL_FILTER, siteName: '전체 세차장' }, ...store.washSites.value])
const reservationVisible = computed({ get: () => Boolean(route.query.reservationId), set: (v) => { if (!v) closeDetail() } })
const historyVisible = computed({ get: () => Boolean(route.params.washHistoryId), set: (v) => { if (!v) closeDetail() } })
const reservationStats = computed(() => ({ total: store.reservationPage.totalElements, confirmed: store.reservations.value.filter(x => x.status === 'CONFIRMED').length, fulfilled: store.reservations.value.filter(x => x.status === 'FULFILLED').length, issue: store.reservations.value.filter(x => ['CANCELED','NO_SHOW'].includes(x.status)).length }))
const historyStats = computed(() => ({ total: store.historyPage.totalElements, washing: store.histories.value.filter(x => ['ENTERED','WASHING'].includes(x.washStatus)).length, done: store.histories.value.filter(x => ['WASH_DONE','EXITED'].includes(x.washStatus)).length, unpaid: store.histories.value.filter(x => !['SUCCESS','DONE'].includes(x.paymentStatus)).length }))

onMounted(async () => { await store.loadFilters(); if (activeTab.value === 'history') await searchHistories(); else await searchReservations(); if (route.query.reservationId) await store.loadReservationDetail(route.query.reservationId); if (route.params.washHistoryId) await store.loadHistoryDetail(route.params.washHistoryId) })
watch(() => route.name, async name => {
  const nextTab = ['admin-wash-history','admin-wash-history-detail'].includes(name) ? 'history' : 'reservations'
  if (nextTab === activeTab.value) return
  activeTab.value = nextTab
  store.clearDetails()
  await store.loadFilters()
  if (nextTab === 'history') { resetHistoryFilters(); await searchHistories(1) }
  else { resetReservationFilters(); await searchReservations(1) }
})
async function changeTab(value) { store.clearDetails(); await router.push({ name: value === 'history' ? 'admin-wash-history' : 'admin-reservations' }) }
function resetReservationFilters() { Object.assign(reservationFilters, { ownerId: ALL_FILTER, washSiteId: ALL_FILTER, status: ALL_FILTER, plateNumber: '', startDate: null, endDate: null }); store.reservationPage.page = 1 }
function resetHistoryFilters() { Object.assign(historyFilters, { ownerId: ALL_FILTER, washSiteId: ALL_FILTER, plateNumber: '', customerType: ALL_FILTER, washStatus: ALL_FILTER, paymentStatus: ALL_FILTER, startDate: firstDay(), endDate: new Date() }); store.historyPage.page = 1 }
function optionalFilter(value) { return value === ALL_FILTER ? undefined : value || undefined }
async function ownerChanged(filters) { filters.washSiteId = ALL_FILTER; await store.loadFilters(optionalFilter(filters.ownerId) || '') }
function reservationParams() { return { page: store.reservationPage.page, size: store.reservationPage.size, ownerId: optionalFilter(reservationFilters.ownerId), washSiteId: optionalFilter(reservationFilters.washSiteId), status: optionalFilter(reservationFilters.status), plateNumber: reservationFilters.plateNumber.trim() || undefined, startDate: key(reservationFilters.startDate), endDate: key(reservationFilters.endDate) } }
function historyParams() { return { page: store.historyPage.page, size: store.historyPage.size, ownerId: optionalFilter(historyFilters.ownerId), washSiteId: optionalFilter(historyFilters.washSiteId), plateNumber: historyFilters.plateNumber.trim() || undefined, customerType: optionalFilter(historyFilters.customerType), washStatus: optionalFilter(historyFilters.washStatus), paymentStatus: optionalFilter(historyFilters.paymentStatus), startDate: key(historyFilters.startDate), endDate: key(historyFilters.endDate) } }
async function searchReservations(page=1) { store.reservationPage.page=page; await store.loadReservations(reservationParams()) }
async function searchHistories(page=1) { store.historyPage.page=page; await store.loadHistories(historyParams()) }
async function reservationPageChanged(e) { store.reservationPage.page=e.page+1; store.reservationPage.size=e.rows; await store.loadReservations(reservationParams()) }
async function historyPageChanged(e) { store.historyPage.page=e.page+1; store.historyPage.size=e.rows; await store.loadHistories(historyParams()) }
async function openReservation(item) { if (await store.loadReservationDetail(item.reservationId)) router.push({ name:'admin-reservations', query:{ reservationId:item.reservationId } }) }
async function openHistory(item) { if (await store.loadHistoryDetail(item.washHistoryId)) router.push({ name:'admin-wash-history-detail', params:{ washHistoryId:item.washHistoryId } }) }
function closeDetail() { store.clearDetails(); if (activeTab.value === 'history') router.push({ name:'admin-wash-history' }); else router.push({ name:'admin-reservations' }) }
async function openRelatedWashHistories(item) {
  store.clearDetails()
  activeTab.value = 'history'
  historyFilters.plateNumber = item.plateNumber || ''
  historyFilters.startDate = item.startTime ? new Date(item.startTime) : null
  historyFilters.endDate = item.startTime ? new Date(item.startTime) : null
  await router.push({ name:'admin-wash-history' })
  await searchHistories(1)
}
async function openRelatedReservations(item) {
  store.clearDetails()
  activeTab.value = 'reservations'
  reservationFilters.plateNumber = item.plateNumber || ''
  reservationFilters.startDate = item.entryTime ? new Date(item.entryTime) : null
  reservationFilters.endDate = item.entryTime ? new Date(item.entryTime) : null
  await router.push({ name:'admin-reservations' })
  await searchReservations(1)
}
function openPayments(item) { router.push({ name:'admin-payments', query:{ keyword:item.plateNumber || '' } }) }
function openPlateLogs(item) { router.push({ name:'admin-plate-detection-logs', query:{ plateNumber:item.plateNumber || '' } }) }
function firstDay() { const d=new Date(); return new Date(d.getFullYear(),d.getMonth(),1) }
function key(value) { if(!value)return undefined; const d=new Date(value),o=d.getTimezoneOffset(); return new Date(d.getTime()-o*60000).toISOString().slice(0,10) }
function datetime(v) { return v ? String(v).replace('T',' ').slice(0,16) : '—' }
function money(v) { return v == null ? '—' : `${Number(v).toLocaleString('ko-KR')}원` }
function reservationStatus(v) { return ({PENDING_PAYMENT:'결제 대기',CONFIRMED:'예약 확정',FULFILLED:'이용 완료',CANCELED:'예약 취소',NO_SHOW:'노쇼'})[v]||v||'미확인' }
function reservationSeverity(v) { return v==='CONFIRMED'?'success':v==='FULFILLED'?'info':['PENDING_PAYMENT','NO_SHOW'].includes(v)?'warn':'danger' }
function reservationCustomerType(v) { return v === 'MEMBER' ? '회원' : v === 'GUEST' ? '비회원' : v || '미확인' }
function washStatus(v) { return ({ENTERED:'입차',WASHING:'세차 중',WASH_DONE:'세차 완료',EXITED:'출차'})[v]||v||'미확인' }
function washSeverity(v) { return ['WASH_DONE','EXITED'].includes(v)?'success':v==='WASHING'?'info':'warn' }
function paymentStatus(v) { return ({WAITING:'결제 대기',SUCCESS:'결제 완료',DONE:'결제 완료',FAILED:'결제 실패',CANCELED:'결제 취소'})[v]||v||'미확인' }
function paymentSeverity(v) { return ['SUCCESS','DONE'].includes(v)?'success':v==='WAITING'?'warn':'danger' }
function washPaymentIssue(item) {
  if (!item) return null
  const washFinished = ['WASH_DONE','EXITED'].includes(item.washStatus)
  const paid = ['SUCCESS','DONE'].includes(item.paymentStatus)
  if (item.washStatus === 'EXITED' && item.paymentStatus === 'WAITING') return { severity:'error', text:'출차가 완료됐지만 결제가 대기 상태입니다.' }
  if (washFinished && !paid) return { severity:'error', text:'세차가 완료됐지만 결제가 완료되지 않았습니다. 결제 내역을 확인해 주세요.' }
  return null
}
</script>

<template>
  <section class="operations-admin-page"><header class="operations-page-header"><div><p>OPERATIONS HISTORY</p><h1>예약·세차 내역</h1><span>전체 사업장의 예약 일정과 실제 세차·결제 진행 상태를 확인합니다.</span></div></header><Message v-if="store.error.value" severity="error" :closable="false">{{ store.error.value }}</Message>
    <Tabs :value="activeTab" class="operations-tabs" @update:value="changeTab"><TabList><Tab value="reservations"><i class="pi pi-calendar" /> 예약 관리</Tab><Tab value="history"><i class="pi pi-history" /> 세차 내역</Tab></TabList><TabPanels>
      <TabPanel value="reservations"><section class="operations-summary-grid"><article><small>전체 예약</small><strong>{{ store.error.value?'—':reservationStats.total }}</strong><span>검색 조건 기준</span></article><article class="blue"><small>예약 확정</small><strong>{{ store.error.value?'—':reservationStats.confirmed }}</strong><span>현재 페이지</span></article><article class="green"><small>이용 완료</small><strong>{{ store.error.value?'—':reservationStats.fulfilled }}</strong><span>현재 페이지</span></article><article class="orange"><small>취소·노쇼</small><strong>{{ store.error.value?'—':reservationStats.issue }}</strong><span>현재 페이지</span></article></section>
        <section class="operations-panel"><header><div><p>RESERVATION LIST</p><h2>전체 예약 목록</h2></div><span>총 {{ store.error.value?'—':store.reservationPage.totalElements }}건</span></header><form class="operations-filter reservation" @submit.prevent="searchReservations(1)"><Select v-model="reservationFilters.ownerId" :options="ownerOptions" option-label="ownerName" option-value="ownerId" @change="ownerChanged(reservationFilters)" /><Select v-model="reservationFilters.washSiteId" :options="siteOptions" option-label="siteName" option-value="washSiteId" /><Select v-model="reservationFilters.status" :options="reservationStatuses" option-label="label" option-value="value" /><DatePicker v-model="reservationFilters.startDate" placeholder="시작일" date-format="yy-mm-dd" show-icon /><DatePicker v-model="reservationFilters.endDate" placeholder="종료일" date-format="yy-mm-dd" show-icon /><span class="operations-search"><i class="pi pi-search" /><InputText v-model="reservationFilters.plateNumber" placeholder="차량번호" /></span><Button label="조회" icon="pi pi-search" type="submit" :loading="store.loading.value" /></form><div v-if="store.loading.value&&!store.reservations.value.length" class="operations-loading"><ProgressSpinner /><strong>예약 내역을 불러오고 있습니다.</strong></div><DataTable v-else :value="store.reservations.value" data-key="reservationId" striped-rows scrollable :table-style="{ minWidth: '72rem' }" class="operations-table" @row-click="openReservation($event.data)"><Column field="startTime" header="예약 시간"><template #body="{data}"><strong>{{ datetime(data.startTime) }}</strong><small>{{ String(data.endTime||'').slice(11,16) }} 종료</small></template></Column><Column field="siteName" header="세차장·장비"><template #body="{data}"><strong>{{ data.siteName||'—' }}</strong><small>{{ data.deviceName||'장비 미지정' }}</small></template></Column><Column field="customerName" header="고객"><template #body="{data}"><strong>{{ data.customerName||'이름 미등록' }}</strong><small>{{ data.customerPhone||'연락처 없음' }}</small><Tag :value="reservationCustomerType(data.reservationType)" :severity="data.reservationType==='MEMBER'?'info':'secondary'" /></template></Column><Column field="plateNumber" header="차량번호"><template #body="{data}"><span class="ops-plate">{{ data.plateNumber||'—' }}</span></template></Column><Column field="courseName" header="코스" /><Column field="reservationPrice" header="금액"><template #body="{data}">{{ money(data.reservationPrice) }}</template></Column><Column field="paymentStatus" header="결제"><template #body="{data}"><Tag :value="paymentStatus(data.paymentStatus)" :severity="paymentSeverity(data.paymentStatus)" /></template></Column><Column field="status" header="예약 상태"><template #body="{data}"><Tag :value="reservationStatus(data.status)" :severity="reservationSeverity(data.status)" /></template></Column><template #empty><div class="operations-empty">조회된 예약이 없습니다.</div></template></DataTable><Paginator v-if="store.reservationPage.totalElements" :first="(store.reservationPage.page-1)*store.reservationPage.size" :rows="store.reservationPage.size" :total-records="store.reservationPage.totalElements" :rows-per-page-options="[10,20,50]" @page="reservationPageChanged" /></section>
      </TabPanel>
      <TabPanel value="history"><section class="operations-summary-grid"><article><small>전체 세차</small><strong>{{ store.error.value?'—':historyStats.total }}</strong><span>검색 조건 기준</span></article><article class="blue"><small>진행 중</small><strong>{{ store.error.value?'—':historyStats.washing }}</strong><span>현재 페이지</span></article><article class="green"><small>세차 완료</small><strong>{{ store.error.value?'—':historyStats.done }}</strong><span>현재 페이지</span></article><article class="orange"><small>미결제·실패</small><strong>{{ store.error.value?'—':historyStats.unpaid }}</strong><span>현재 페이지</span></article></section>
        <section class="operations-panel"><header><div><p>WASH HISTORY</p><h2>세차 이용 내역</h2></div><span>총 {{ store.error.value?'—':store.historyPage.totalElements }}건</span></header><form class="operations-filter history" @submit.prevent="searchHistories(1)"><Select v-model="historyFilters.ownerId" :options="ownerOptions" option-label="ownerName" option-value="ownerId" @change="ownerChanged(historyFilters)" /><Select v-model="historyFilters.washSiteId" :options="siteOptions" option-label="siteName" option-value="washSiteId" /><span class="operations-search"><i class="pi pi-search" /><InputText v-model="historyFilters.plateNumber" placeholder="차량번호" /></span><Select v-model="historyFilters.customerType" :options="customerTypes" option-label="label" option-value="value" /><Select v-model="historyFilters.washStatus" :options="washStatuses" option-label="label" option-value="value" /><Select v-model="historyFilters.paymentStatus" :options="paymentStatuses" option-label="label" option-value="value" /><DatePicker v-model="historyFilters.startDate" date-format="yy-mm-dd" show-icon /><DatePicker v-model="historyFilters.endDate" date-format="yy-mm-dd" show-icon /><Button label="조회" icon="pi pi-search" type="submit" :loading="store.loading.value" /></form><div v-if="store.loading.value&&!store.histories.value.length" class="operations-loading"><ProgressSpinner /><strong>세차 내역을 불러오고 있습니다.</strong></div><DataTable v-else :value="store.histories.value" data-key="washHistoryId" striped-rows scrollable :table-style="{ minWidth: '68rem' }" class="operations-table" @row-click="openHistory($event.data)"><Column field="washHistoryId" header="ID"><template #body="{data}">#{{ data.washHistoryId }}</template></Column><Column field="siteName" header="세차장" /><Column field="plateNumber" header="차량번호"><template #body="{data}"><span class="ops-plate">{{ data.plateNumber||'—' }}</span></template></Column><Column field="customerType" header="고객"><template #body="{data}">{{ data.customerType==='MEMBER'?'회원':'비회원' }}</template></Column><Column field="courseName" header="코스" /><Column field="amount" header="금액"><template #body="{data}">{{ money(data.amount) }}</template></Column><Column field="entryTime" header="입차"><template #body="{data}">{{ datetime(data.entryTime) }}</template></Column><Column field="washStatus" header="세차 상태"><template #body="{data}"><Tag :value="washStatus(data.washStatus)" :severity="washSeverity(data.washStatus)" /></template></Column><Column field="paymentStatus" header="결제"><template #body="{data}"><Tag :value="paymentStatus(data.paymentStatus)" :severity="paymentSeverity(data.paymentStatus)" /></template></Column><template #empty><div class="operations-empty">조회된 세차 내역이 없습니다.</div></template></DataTable><Paginator v-if="store.historyPage.totalElements" :first="(store.historyPage.page-1)*store.historyPage.size" :rows="store.historyPage.size" :total-records="store.historyPage.totalElements" :rows-per-page-options="[10,20,50]" @page="historyPageChanged" /></section>
      </TabPanel>
    </TabPanels></Tabs>

    <Dialog v-model:visible="reservationVisible" modal :header="store.reservationDetail.value?`예약 #${store.reservationDetail.value.reservationId}`:'예약 상세'" :style="{width:'min(96vw,900px)'}">
      <div v-if="store.detailLoading.value" class="operations-loading detail"><ProgressSpinner /></div>
      <div v-else-if="store.reservationDetail.value" class="reservation-detail-content">
        <header><div><span>{{ store.reservationDetail.value.siteName }} · {{ store.reservationDetail.value.deviceName||'장비 미지정' }}</span><h2>{{ datetime(store.reservationDetail.value.startTime) }}</h2></div><Tag :value="reservationStatus(store.reservationDetail.value.status)" :severity="reservationSeverity(store.reservationDetail.value.status)" /></header>
        <dl><div><dt>고객</dt><dd>{{ store.reservationDetail.value.customerName||'—' }}</dd></div><div><dt>고객 구분</dt><dd>{{ reservationCustomerType(store.reservationDetail.value.reservationType) }}</dd></div><div><dt>연락처</dt><dd>{{ store.reservationDetail.value.customerPhone||'—' }}</dd></div><div><dt>차량번호</dt><dd>{{ store.reservationDetail.value.plateNumber||'—' }}</dd></div><div><dt>세차 코스</dt><dd>{{ store.reservationDetail.value.courseName||'—' }}</dd></div><div><dt>예약 금액</dt><dd>{{ money(store.reservationDetail.value.reservationPrice) }}</dd></div><div><dt>결제 상태</dt><dd>{{ paymentStatus(store.reservationDetail.value.paymentStatus) }}</dd></div><div><dt>등록일</dt><dd>{{ datetime(store.reservationDetail.value.createdAt) }}</dd></div></dl>
        <section class="reservation-state-flow"><div class="done"><i class="pi pi-file-plus" /><span><small>예약 등록</small><strong>{{ datetime(store.reservationDetail.value.createdAt) }}</strong></span></div><div :class="{done:store.reservationDetail.value.status!=='PENDING_PAYMENT'}"><i class="pi pi-credit-card" /><span><small>결제·확정</small><strong>{{ paymentStatus(store.reservationDetail.value.paymentStatus) }}</strong></span></div><div :class="{done:store.reservationDetail.value.status==='FULFILLED'}"><i class="pi pi-car" /><span><small>이용 결과</small><strong>{{ store.reservationDetail.value.status==='FULFILLED'?datetime(store.reservationDetail.value.fulfilledAt):reservationStatus(store.reservationDetail.value.status) }}</strong></span></div></section>
        <Message v-if="store.reservationDetail.value.cancelReason" severity="error" :closable="false">취소 사유: {{ store.reservationDetail.value.cancelReason }} · {{ datetime(store.reservationDetail.value.canceledAt) }}</Message>
        <section class="related-record-actions"><div><h3>관련 업무 조회</h3><p>현재 API에 직접 연결 ID가 없어 차량번호와 예약일 기준으로 조회합니다.</p></div><div><Button label="세차 내역" icon="pi pi-history" severity="secondary" outlined :disabled="!store.reservationDetail.value.plateNumber" @click="openRelatedWashHistories(store.reservationDetail.value)" /><Button label="차량 결제" icon="pi pi-credit-card" severity="secondary" outlined :disabled="!store.reservationDetail.value.plateNumber" @click="openPayments(store.reservationDetail.value)" /><Button label="번호판 기록" icon="pi pi-camera" severity="secondary" outlined :disabled="!store.reservationDetail.value.plateNumber" @click="openPlateLogs(store.reservationDetail.value)" /></div></section>
        <Message severity="secondary" :closable="false">관리자는 예약 조회만 가능합니다. 예약 취소와 노쇼 처리는 현재 관리자 API에서 제공하지 않습니다.</Message>
      </div>
    </Dialog>
    <Dialog v-model:visible="historyVisible" modal :header="store.historyDetail.value?`세차 내역 #${store.historyDetail.value.washHistoryId}`:'세차 상세'" :style="{width:'min(96vw,1050px)'}">
      <div v-if="store.detailLoading.value" class="operations-loading detail"><ProgressSpinner /></div>
      <div v-else-if="store.historyDetail.value" class="wash-detail-content">
        <header><div><span>{{ store.historyDetail.value.siteName||'세차장 미확인' }}</span><h2>{{ store.historyDetail.value.plateNumber||'차량번호 없음' }}</h2></div><div><Tag :value="washStatus(store.historyDetail.value.washStatus)" :severity="washSeverity(store.historyDetail.value.washStatus)" /><Tag :value="paymentStatus(store.historyDetail.value.paymentStatus)" :severity="paymentSeverity(store.historyDetail.value.paymentStatus)" /></div></header>
        <dl><div><dt>고객 구분</dt><dd>{{ store.historyDetail.value.customerType==='MEMBER'?'회원':'비회원' }}</dd></div><div><dt>세차 코스</dt><dd>{{ store.historyDetail.value.courseName||'—' }}</dd></div><div><dt>결제 금액</dt><dd>{{ money(store.historyDetail.value.amount) }}</dd></div><div><dt>세차장 전화</dt><dd>{{ store.historyDetail.value.sitePhone||'—' }}</dd></div></dl>
        <Message v-if="washPaymentIssue(store.historyDetail.value)" :severity="washPaymentIssue(store.historyDetail.value).severity" :closable="false">{{ washPaymentIssue(store.historyDetail.value).text }}</Message>
        <section class="wash-timeline"><div :class="{done:store.historyDetail.value.entryTime}"><i class="pi pi-sign-in" /><span><small>입차</small><strong>{{ datetime(store.historyDetail.value.entryTime) }}</strong></span></div><div :class="{done:store.historyDetail.value.washStartTime}"><i class="pi pi-play" /><span><small>세차 시작</small><strong>{{ datetime(store.historyDetail.value.washStartTime) }}</strong></span></div><div :class="{done:store.historyDetail.value.washEndTime}"><i class="pi pi-check" /><span><small>세차 완료</small><strong>{{ datetime(store.historyDetail.value.washEndTime) }}</strong></span></div><div :class="{done:store.historyDetail.value.exitTime}"><i class="pi pi-sign-out" /><span><small>출차</small><strong>{{ datetime(store.historyDetail.value.exitTime) }}</strong></span></div></section>
        <section class="related-record-actions"><div><h3>관련 업무 조회</h3><p>차량번호와 이용일 기준으로 예약·결제·번호판 기록을 조회합니다.</p></div><div><Button label="예약 내역" icon="pi pi-calendar" severity="secondary" outlined :disabled="!store.historyDetail.value.plateNumber" @click="openRelatedReservations(store.historyDetail.value)" /><Button label="결제 내역" icon="pi pi-credit-card" severity="secondary" outlined :disabled="!store.historyDetail.value.plateNumber" @click="openPayments(store.historyDetail.value)" /><Button label="번호판 기록" icon="pi pi-camera" severity="secondary" outlined :disabled="!store.historyDetail.value.plateNumber" @click="openPlateLogs(store.historyDetail.value)" /></div></section>
        <Message v-if="store.imageError.value" severity="warn" :closable="false">{{ store.imageError.value }}</Message>
        <section class="wash-photo-grid"><figure><div><img v-if="store.entryImageUrl.value" :src="store.entryImageUrl.value" alt="입차 사진"><span v-else>{{ store.imageLoading.value?'사진 확인 중':'입차 사진 없음' }}</span></div><figcaption>입차 사진 <small>{{ datetime(store.historyDetail.value.entryTime) }}</small></figcaption></figure><figure><div><img v-if="store.exitImageUrl.value" :src="store.exitImageUrl.value" alt="출차 사진"><span v-else>{{ store.imageLoading.value?'사진 확인 중':'출차 사진 없음' }}</span></div><figcaption>출차 사진 <small>{{ datetime(store.historyDetail.value.exitTime) }}</small></figcaption></figure></section>
      </div>
    </Dialog>
  </section>
</template>

