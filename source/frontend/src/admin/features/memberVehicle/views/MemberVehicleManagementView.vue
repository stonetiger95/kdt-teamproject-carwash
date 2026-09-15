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
import Textarea from 'primevue/textarea'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMemberVehicleManagement } from '@/admin/features/memberVehicle/composables/useMemberVehicleManagement'

const route = useRoute()
const router = useRouter()
const store = useMemberVehicleManagement()
const ALL_FILTER = 'ALL'
const activeTab = ref(route.name === 'admin-vehicles' || route.name === 'admin-vehicle-detail' ? 'vehicles' : 'members')
const memberSearch = reactive({ keyword: '', status: ALL_FILTER, customerType: ALL_FILTER })
const vehicleSearch = reactive({ keyword: '', customerType: ALL_FILTER, blacklistStatus: ALL_FILTER })
const memberForm = reactive({ name: '', birthDate: null, phone: '', gender: 'N', emailAddress: '' })
const editingMember = ref(false)
const blacklistDialogVisible = ref(false)
const blacklistTarget = ref(null)
const blacklistReason = ref('')

const memberStatusOptions = [{ label: '전체 상태', value: ALL_FILTER }, { label: '활성', value: 'ACTIVE' }, { label: '정지', value: 'SUSPENDED' }, { label: '탈퇴', value: 'WITHDRAWN' }]
const customerTypeOptions = [{ label: '전체 구분', value: ALL_FILTER }, { label: '회원', value: 'MEMBER' }]
const vehicleCustomerTypeOptions = [{ label: '전체 고객', value: ALL_FILTER }, { label: '회원', value: 'MEMBER' }, { label: '비회원', value: 'NON_MEMBER' }]
const vehicleBlacklistOptions = [{ label: '전체 차량', value: ALL_FILTER }, { label: '차단 차량', value: 'BLACKLISTED' }, { label: '정상 차량', value: 'NORMAL' }]
const genderOptions = [{ label: '남성', value: 'M' }, { label: '여성', value: 'F' }, { label: '선택 안 함', value: 'N' }]
const memberDialogVisible = computed({ get: () => Boolean(route.query.customerId), set: (visible) => { if (!visible) closeMember() } })
const vehicleDialogVisible = computed({ get: () => Boolean(route.params.plateNumber), set: (visible) => { if (!visible) closeVehicle() } })
const memberSearchParams = computed(() => ({ page: store.memberPage.page, size: store.memberPage.size, keyword: memberSearch.keyword.trim() || undefined, status: optionalFilter(memberSearch.status), customerType: optionalFilter(memberSearch.customerType) }))
const vehicleSearchParams = computed(() => ({
  page: store.vehiclePage.page,
  size: store.vehiclePage.size,
  keyword: vehicleSearch.keyword.trim() || undefined,
  customerType: optionalFilter(vehicleSearch.customerType),
  blacklistStatus: optionalFilter(vehicleSearch.blacklistStatus),
}))
const memberVehicles = computed(() => Array.isArray(store.memberDetail.value?.vehicles) ? store.memberDetail.value.vehicles : [])
const entryHistory = computed(() => Array.isArray(store.vehicleDetail.value?.entryExitHistory) ? store.vehicleDetail.value.entryExitHistory : [])
const blacklistHistory = computed(() => Array.isArray(store.vehicleDetail.value?.blackListHistory) ? store.vehicleDetail.value.blackListHistory : [])
const canRegisterManualBlacklist = computed(() => Number(store.vehicleDetail.value?.visitCount || 0) > 0)
const dialogVisible = computed(() => memberDialogVisible.value || vehicleDialogVisible.value || blacklistDialogVisible.value)

onMounted(async () => {
  if (activeTab.value === 'vehicles') await store.loadVehicles(vehicleSearchParams.value)
  else await store.loadMembers(memberSearchParams.value)
  if (route.query.customerId) await openMember(route.query.customerId, false)
  if (route.params.plateNumber) await openVehicle(route.params.plateNumber, false)
})
function optionalFilter(value) { return value === ALL_FILTER ? undefined : value || undefined }
function resetMemberSearch() { Object.assign(memberSearch, { keyword: '', status: ALL_FILTER, customerType: ALL_FILTER }); store.memberPage.page = 1 }
function resetVehicleSearch() { Object.assign(vehicleSearch, { keyword: '', customerType: ALL_FILTER, blacklistStatus: ALL_FILTER }); store.vehiclePage.page = 1 }
watch(() => route.name, async (name) => {
  const nextTab = name === 'admin-vehicles' || name === 'admin-vehicle-detail' ? 'vehicles' : 'members'
  const tabChanged = nextTab !== activeTab.value
  activeTab.value = nextTab
  store.clearMessages()
  if (!tabChanged) return
  if (nextTab === 'vehicles') {
    resetVehicleSearch()
    await store.loadVehicles(vehicleSearchParams.value)
  } else {
    resetMemberSearch()
    await store.loadMembers(memberSearchParams.value)
  }
})

async function changeTab(value) {
  store.clearMessages()
  await router.push({ name: value === 'vehicles' ? 'admin-vehicles' : 'admin-member-manage' })
}
async function searchMembers(page = 1) { store.memberPage.page = page; await store.loadMembers(memberSearchParams.value) }
async function searchVehicles(page = 1) { store.vehiclePage.page = page; await store.loadVehicles(vehicleSearchParams.value) }
async function memberPageChanged(event) { store.memberPage.page = event.page + 1; store.memberPage.size = event.rows; await store.loadMembers(memberSearchParams.value) }
async function vehiclePageChanged(event) { store.vehiclePage.page = event.page + 1; store.vehiclePage.size = event.rows; await store.loadVehicles(vehicleSearchParams.value) }
async function openMember(customer, updateRoute = true) {
  const customerId = typeof customer === 'object' ? customer.customerId : customer
  if (await store.loadMemberDetail(customerId)) { fillMemberForm(); editingMember.value = false; if (updateRoute) await router.push({ name: 'admin-member-manage', query: { customerId } }) }
}
function closeMember() { store.clearMessages(); store.memberDetail.value = null; editingMember.value = false; if (route.query.customerId) router.push({ name: 'admin-member-manage' }) }
async function openVehicle(vehicle, updateRoute = true) {
  const plateNumber = typeof vehicle === 'object' ? vehicle.plateNumber : vehicle
  if (await store.loadVehicleDetail(plateNumber) && updateRoute) await router.push({ name: 'admin-vehicle-detail', params: { plateNumber } })
}
function closeVehicle() { store.clearMessages(); store.vehicleDetail.value = null; if (route.name === 'admin-vehicle-detail') router.push({ name: 'admin-vehicles' }) }
function fillMemberForm() { const item = store.memberDetail.value; Object.assign(memberForm, { name: item?.name || '', birthDate: item?.birthDate ? new Date(`${item.birthDate}T00:00:00`) : null, phone: item?.phone || '', gender: item?.gender || 'N', emailAddress: item?.emailAddress || '' }) }
async function saveMember() {
  const payload = { name: memberForm.name.trim(), birthDate: memberForm.birthDate ? localDate(memberForm.birthDate) : null, phone: memberForm.phone.trim(), gender: memberForm.gender, emailAddress: memberForm.emailAddress.trim() }
  if (await store.saveMember(payload, memberSearchParams.value)) { editingMember.value = false; fillMemberForm() }
}
async function toggleMemberStatus() {
  const item = store.memberDetail.value
  if (!item || item.status === 'WITHDRAWN') return
  const status = item.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
  if (await store.saveMember({ status }, memberSearchParams.value)) fillMemberForm()
}
function requestBlacklist(vehicle) {
  const isOpenVehicle = vehicle?.plateNumber === store.vehicleDetail.value?.plateNumber
  if (isOpenVehicle && !canRegisterManualBlacklist.value) {
    store.clearMessages()
    store.error.value = '입출차 이력이 있는 차량만 수동 블랙리스트에 등록할 수 있습니다.'
    return
  }
  store.clearMessages()
  blacklistTarget.value = vehicle
  blacklistReason.value = ''
  blacklistDialogVisible.value = true
}
function closeBlacklistDialog() { blacklistDialogVisible.value = false; store.clearMessages() }
async function submitBlacklist() { if (!blacklistReason.value.trim()) return; if (await store.registerBlacklist(blacklistTarget.value.plateNumber, blacklistReason.value, vehicleSearchParams.value)) blacklistDialogVisible.value = false }
async function releaseBlacklist(vehicle) { await store.releaseBlacklist(vehicle.plateNumber, vehicleSearchParams.value) }
function statusLabel(value) { return ({ ACTIVE: '활성', SUSPENDED: '정지', WITHDRAWN: '탈퇴', DEACTIVATED: '비활성' })[value] || value || '미확인' }
function statusSeverity(value) { return value === 'ACTIVE' ? 'success' : value === 'SUSPENDED' ? 'danger' : 'secondary' }
function customerType(value) { return value === 'MEMBER' ? '회원' : value === 'NON_MEMBER' ? '비회원' : '미확인' }
function vehicleType(value) { return ({ SEDAN: '승용차', SUV: 'SUV', TRUCK: '트럭', VAN: '승합차' })[value] || value || '미등록' }
function localDate(value) { const date = new Date(value); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' }
</script>

<template>
  <section class="member-vehicle-admin-page">
    <header class="member-vehicle-header"><div><p>CUSTOMER & VEHICLE</p><h1>회원·차량 관리</h1><span>회원 상태와 등록 차량, 입출차 및 차단 상태를 통합 관리합니다.</span></div></header>
    <Message v-if="store.error.value && !dialogVisible" severity="error" :closable="false">{{ store.error.value }}</Message>
    <Message v-if="store.successMessage.value && !dialogVisible" severity="success" :closable="false">{{ store.successMessage.value }}</Message>

    <Tabs :value="activeTab" class="member-vehicle-tabs" @update:value="changeTab">
      <TabList><Tab value="members"><i class="pi pi-users" /> 회원 관리</Tab><Tab value="vehicles"><i class="pi pi-car" /> 차량·블랙리스트</Tab></TabList>
      <TabPanels>
        <TabPanel value="members">
          <section class="member-vehicle-panel"><header><div><p>MEMBER DIRECTORY</p><h2>회원 목록</h2></div><span>총 {{ store.error.value ? '—' : store.memberPage.totalElements }}명</span></header>
            <form class="member-filter-bar" @submit.prevent="searchMembers(1)"><span class="mv-search"><i class="pi pi-search" /><InputText v-model="memberSearch.keyword" placeholder="이름, 로그인 ID, 전화번호 검색" /></span><Select v-model="memberSearch.status" :options="memberStatusOptions" option-label="label" option-value="value" /><Select v-model="memberSearch.customerType" :options="customerTypeOptions" option-label="label" option-value="value" /><Button label="조회" icon="pi pi-search" type="submit" :loading="store.loading.value" /></form>
            <div v-if="store.loading.value && !store.members.value.length" class="mv-loading"><ProgressSpinner /><strong>회원 목록을 불러오고 있습니다.</strong></div>
            <DataTable v-else :value="store.members.value" data-key="customerId" striped-rows scrollable :table-style="{ minWidth: '58rem' }" class="mv-data-table" @row-click="openMember($event.data)"><Column field="customerId" header="ID" style="width:6rem" /><Column field="loginId" header="로그인 ID" /><Column field="name" header="이름"><template #body="{ data }"><button class="mv-primary-link" type="button" @click.stop="openMember(data)">{{ data.name || '이름 없음' }}</button></template></Column><Column field="phone" header="전화번호"><template #body="{ data }">{{ data.phone || '—' }}</template></Column><Column field="customerType" header="구분"><template #body="{ data }">{{ customerType(data.customerType) }}</template></Column><Column field="status" header="상태"><template #body="{ data }"><Tag :value="statusLabel(data.status)" :severity="statusSeverity(data.status)" /></template></Column><Column field="activeVehicleCount" header="활성 차량"><template #body="{ data }"><strong>{{ data.activeVehicleCount ?? '—' }}</strong>대</template></Column><template #empty><div class="mv-empty"><i class="pi pi-users" /><strong>{{ store.error.value ? '회원 데이터를 표시할 수 없습니다.' : '조회된 회원이 없습니다.' }}</strong></div></template></DataTable>
            <Paginator v-if="store.memberPage.totalElements" :first="(store.memberPage.page-1)*store.memberPage.size" :rows="store.memberPage.size" :total-records="store.memberPage.totalElements" :rows-per-page-options="[10,20,50]" @page="memberPageChanged" />
          </section>
        </TabPanel>

        <TabPanel value="vehicles">
          <section class="member-vehicle-panel"><header><div><p>VEHICLE DIRECTORY</p><h2>차량 및 차단 상태</h2></div><span>총 {{ store.error.value ? '—' : store.vehiclePage.totalElements }}대</span></header>
            <form class="vehicle-filter-bar" @submit.prevent="searchVehicles(1)"><span class="mv-search"><i class="pi pi-search" /><InputText v-model="vehicleSearch.keyword" placeholder="차량번호 검색" aria-label="차량번호 검색" /></span><Select v-model="vehicleSearch.customerType" :options="vehicleCustomerTypeOptions" option-label="label" option-value="value" aria-label="회원 구분" /><Select v-model="vehicleSearch.blacklistStatus" :options="vehicleBlacklistOptions" option-label="label" option-value="value" aria-label="차단 상태" /><Button label="조회" icon="pi pi-search" type="submit" :loading="store.loading.value" /></form>
            <div v-if="store.loading.value && !store.vehicles.value.length" class="mv-loading"><ProgressSpinner /><strong>차량 목록을 불러오고 있습니다.</strong></div>
            <DataTable v-else :value="store.vehicles.value" data-key="plateNumber" striped-rows scrollable :table-style="{ minWidth: '62rem' }" class="mv-data-table" @row-click="openVehicle($event.data)"><Column field="plateNumber" header="차량번호"><template #body="{ data }"><button class="vehicle-plate-link" type="button" @click.stop="openVehicle(data)">{{ data.plateNumber || '—' }}</button></template></Column><Column field="customerName" header="소유자"><template #body="{ data }"><strong>{{ data.customerName || '비회원 차량' }}</strong><small>{{ customerType(data.customerType) }}</small></template></Column><Column field="customerStatus" header="회원 상태"><template #body="{ data }"><Tag :value="statusLabel(data.customerStatus)" :severity="statusSeverity(data.customerStatus)" /></template></Column><Column field="vehicleStatus" header="차량 상태"><template #body="{ data }">{{ statusLabel(data.vehicleStatus) }}</template></Column><Column field="visitCount" header="방문"><template #body="{ data }">{{ data.visitCount ?? '—' }}회</template></Column><Column field="lastEntryTime" header="최근 입차"><template #body="{ data }">{{ dateTime(data.lastEntryTime) }}</template></Column><Column field="blacklisted" header="차단"><template #body="{ data }"><Tag :value="data.blacklisted ? '차단' : '정상'" :severity="data.blacklisted ? 'danger' : 'success'" /></template></Column><template #empty><div class="mv-empty"><i class="pi pi-car" /><strong>{{ store.error.value ? '차량 데이터를 표시할 수 없습니다.' : '조회된 차량이 없습니다.' }}</strong></div></template></DataTable>
            <Paginator v-if="store.vehiclePage.totalElements" :first="(store.vehiclePage.page-1)*store.vehiclePage.size" :rows="store.vehiclePage.size" :total-records="store.vehiclePage.totalElements" :rows-per-page-options="[10,20,50]" @page="vehiclePageChanged" />
          </section>
        </TabPanel>
      </TabPanels>
    </Tabs>

    <Dialog v-model:visible="memberDialogVisible" modal class="member-detail-dialog" :header="store.memberDetail.value ? `회원 #${store.memberDetail.value.customerId}` : '회원 상세'" :style="{ width:'min(96vw,1050px)' }">
      <Message v-if="store.error.value && !blacklistDialogVisible" severity="error" :closable="false">{{ store.error.value }}</Message>
      <Message v-if="store.successMessage.value && !blacklistDialogVisible" severity="success" :closable="false">{{ store.successMessage.value }}</Message>
      <div v-if="store.detailLoading.value" class="mv-loading detail"><ProgressSpinner /><strong>회원 상세 정보를 확인하고 있습니다.</strong></div>
      <div v-else-if="store.memberDetail.value" class="member-detail-content"><header><div><span>{{ store.memberDetail.value.loginId }}</span><h2>{{ store.memberDetail.value.name || '이름 없음' }}</h2><small>{{ store.memberDetail.value.emailAddress || '이메일 미등록' }}</small></div><div><Tag :value="statusLabel(store.memberDetail.value.status)" :severity="statusSeverity(store.memberDetail.value.status)" /><Button v-if="store.memberDetail.value.status !== 'WITHDRAWN'" :label="editingMember ? '수정 취소' : '정보 수정'" :icon="editingMember ? 'pi pi-times' : 'pi pi-pencil'" severity="secondary" outlined @click="editingMember ? (fillMemberForm(), editingMember=false) : editingMember=true" /><Button v-if="!editingMember && store.memberDetail.value.status !== 'WITHDRAWN'" :label="store.memberDetail.value.status === 'ACTIVE' ? '회원 정지' : '정지 해제'" :severity="store.memberDetail.value.status === 'ACTIVE' ? 'danger' : 'primary'" @click="toggleMemberStatus" /></div></header>
        <dl class="member-summary"><div><dt>로그인 ID</dt><dd>{{ store.memberDetail.value.loginId || '—' }}</dd></div><div><dt>전체 차량</dt><dd>{{ store.memberDetail.value.vehicleCount ?? '—' }}대</dd></div><div><dt>활성 차량</dt><dd>{{ store.memberDetail.value.activeVehicleCount ?? '—' }}대</dd></div><div><dt>회원 구분</dt><dd>{{ customerType(store.memberDetail.value.customerType) }}</dd></div></dl>
        <form class="member-edit-form" @submit.prevent="saveMember"><label><span>이름</span><InputText v-model.trim="memberForm.name" :disabled="!editingMember" /></label><label><span>생년월일</span><DatePicker v-model="memberForm.birthDate" date-format="yy-mm-dd" :disabled="!editingMember" /></label><label><span>전화번호</span><InputText v-model.trim="memberForm.phone" :disabled="!editingMember" /></label><label><span>성별</span><Select v-model="memberForm.gender" :options="genderOptions" option-label="label" option-value="value" :disabled="!editingMember" /></label><label class="email"><span>이메일</span><InputText v-model.trim="memberForm.emailAddress" type="email" :disabled="!editingMember" /></label><Button v-if="editingMember" label="회원 정보 저장" icon="pi pi-check" type="submit" :loading="store.actionLoading.value" /></form>
        <section class="member-owned-vehicles"><header><h3>등록 차량</h3><span>{{ memberVehicles.length }}대</span></header><DataTable :value="memberVehicles" data-key="vehicleId" size="small" scrollable :table-style="{ minWidth: '48rem' }"><Column field="plateNumber" header="번호판"><template #body="{ data }"><strong class="vehicle-plate-text">{{ data.plateNumber }}</strong></template></Column><Column field="vehicleType" header="차종"><template #body="{ data }">{{ vehicleType(data.vehicleType) }}</template></Column><Column field="vehicleStatus" header="상태"><template #body="{ data }">{{ statusLabel(data.vehicleStatus) }}</template></Column><Column header="차단 상태"><template #body="{ data }"><Tag v-if="data.manualBlacklisted" value="수동 차단" severity="danger" /><Tag v-else-if="data.suspendedBlacklisted" value="회원 정지 연동" severity="warn" /><Tag v-else value="정상" severity="success" /></template></Column><Column header="관리"><template #body="{ data }"><Button v-if="data.manualBlacklisted" label="수동 차단 해제" size="small" severity="secondary" outlined :loading="store.actionLoading.value" @click="releaseBlacklist(data)" /><Button v-else-if="data.vehicleStatus === 'ACTIVE'" label="수동 차단" size="small" severity="danger" outlined @click="requestBlacklist(data)" /></template></Column><template #empty><div class="small-empty">등록된 차량이 없습니다.</div></template></DataTable></section>
      </div>
    </Dialog>

    <Dialog v-model:visible="vehicleDialogVisible" modal class="vehicle-detail-dialog" :header="store.vehicleDetail.value ? `차량 ${store.vehicleDetail.value.plateNumber}` : '차량 상세'" :style="{ width:'min(96vw,1050px)' }"><Message v-if="store.error.value && !blacklistDialogVisible" severity="error" :closable="false">{{ store.error.value }}</Message><Message v-if="store.successMessage.value && !blacklistDialogVisible" severity="success" :closable="false">{{ store.successMessage.value }}</Message><div v-if="store.detailLoading.value" class="mv-loading detail"><ProgressSpinner /><strong>차량 상세 정보를 확인하고 있습니다.</strong></div><div v-else-if="store.vehicleDetail.value" class="vehicle-detail-content"><header><div><span>REGISTERED VEHICLE</span><h2>{{ store.vehicleDetail.value.plateNumber }}</h2><small>{{ store.vehicleDetail.value.customerName || '비회원 차량' }} · {{ customerType(store.vehicleDetail.value.customerType) }}</small></div><div><Tag :value="store.vehicleDetail.value.blacklisted ? '차단 차량' : '정상 차량'" :severity="store.vehicleDetail.value.blacklisted ? 'danger' : 'success'" /><Button v-if="store.vehicleDetail.value.manualBlacklisted" label="수동 차단 해제" severity="secondary" outlined :loading="store.actionLoading.value" @click="releaseBlacklist(store.vehicleDetail.value)" /><Button v-else label="수동 블랙리스트 등록" severity="danger" :disabled="!canRegisterManualBlacklist" :title="canRegisterManualBlacklist ? '입출차 이력을 기준으로 수동 차단합니다.' : '입출차 이력이 없어 등록할 수 없습니다.'" @click="requestBlacklist(store.vehicleDetail.value)" /></div></header><dl class="vehicle-summary"><div><dt>소유자 상태</dt><dd>{{ statusLabel(store.vehicleDetail.value.customerStatus) }}</dd></div><div><dt>차량 상태</dt><dd>{{ statusLabel(store.vehicleDetail.value.vehicleStatus) }}</dd></div><div><dt>방문 횟수</dt><dd>{{ store.vehicleDetail.value.visitCount ?? '—' }}회</dd></div><div><dt>최근 입차</dt><dd>{{ dateTime(store.vehicleDetail.value.lastEntryTime) }}</dd></div></dl><Message v-if="!store.vehicleDetail.value.manualBlacklisted && !canRegisterManualBlacklist" severity="info" :closable="false">입출차 이력이 없는 차량은 수동 블랙리스트에 등록할 수 없습니다.</Message><Message v-if="store.vehicleDetail.value.suspendedBlacklisted" severity="warn" :closable="false">회원 정지 상태로 연동 차단된 차량입니다. 회원 정지를 해제해야 정상화됩니다.</Message><section class="vehicle-history-grid"><article><header><h3>최근 입출차</h3><span>{{ entryHistory.length }}건</span></header><div v-if="entryHistory.length" class="history-list"><div v-for="(item,index) in entryHistory" :key="index"><i class="pi pi-sign-in" /><span><strong>{{ item.siteName || '세차장' }}</strong><small>{{ dateTime(item.entryTime || item.detectedTime || item.createdAt) }}</small></span></div></div><div v-else class="small-empty">입출차 이력이 없습니다.</div></article><article><header><h3>블랙리스트 이력</h3><span>{{ blacklistHistory.length }}건</span></header><div v-if="blacklistHistory.length" class="history-list danger"><div v-for="item in blacklistHistory" :key="item.vehicleBlacklistId"><i class="pi pi-ban" /><span><strong>{{ item.reason || '사유 미등록' }}</strong><small>{{ dateTime(item.createdAt) }} · {{ item.active ? '활성' : '해제' }}</small></span></div></div><div v-else class="small-empty">차단 이력이 없습니다.</div></article></section></div></Dialog>

    <Dialog v-model:visible="blacklistDialogVisible" modal header="차량 수동 블랙리스트 등록" :style="{ width:'min(92vw,520px)' }"><Message v-if="store.error.value" severity="error" :closable="false">{{ store.error.value }}</Message><form class="blacklist-form" @submit.prevent="submitBlacklist"><Message severity="warn" :closable="false">{{ blacklistTarget?.plateNumber }} 차량의 출입을 차단합니다. 추후 수동 해제가 가능합니다.</Message><label><span>등록 사유</span><Textarea v-model="blacklistReason" rows="5" maxlength="500" placeholder="차단 사유를 구체적으로 입력하세요." required /></label><footer><Button label="취소" severity="secondary" outlined type="button" @click="closeBlacklistDialog" /><Button label="블랙리스트 등록" icon="pi pi-ban" severity="danger" type="submit" :loading="store.actionLoading.value" :disabled="!blacklistReason.trim()" /></footer></form></Dialog>
  </section>
</template>

