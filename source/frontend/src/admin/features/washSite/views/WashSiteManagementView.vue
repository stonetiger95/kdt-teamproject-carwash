<script setup>
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useJusoAddressPopup } from '@/admin/features/address/composables/useJusoAddressPopup'
import { useWashSiteManagement } from '@/admin/features/washSite/composables/useWashSiteManagement'

const store = useWashSiteManagement()
const route = useRoute()
const ALL_FILTER = 'ALL'
const statusOptions = [{ label: '전체 상태', value: 'ALL' }, { label: '운영 중', value: 'ACTIVE' }, { label: '운영 중지', value: 'INACTIVE' }]
const keyword = ref('')
const selectedOwnerId = ref(String(route.query.ownerId || ALL_FILTER))
const statusFilter = ref(normalizeStatusFilter(route.query.status))
const formVisible = ref(false)
const formError = ref('')
const originalAddress = ref('')
const postcode = ref('')
const roadAddress = ref('')
const detailAddress = ref('')
const detailAddressInput = ref(null)
const addressSelected = ref(false)
const form = reactive({ washSiteId: null, ownerId: null, siteName: '', address: '', sitePhone: '', status: 'ACTIVE', courseName: '기본 세차', washPrice: null, latitude: null, longitude: null })

function setStoredAddress(value) {
  const fullAddress = String(value || '').trim()
  const matched = fullAddress.match(/^\[(\d{5})\]\s*(.+)$/)
  postcode.value = matched?.[1] || ''
  roadAddress.value = matched?.[2] || fullAddress
  detailAddress.value = ''
  addressSelected.value = false
  form.address = fullAddress
}
function handleAddressSelected(address) {
  postcode.value = String(address.zipNo || '').trim()
  roadAddress.value = String(address.roadAddrPart1 || address.roadFullAddr || '').trim()
  detailAddress.value = String(address.addrDetail || '').trim()
  addressSelected.value = true
  form.latitude = null
  form.longitude = null
  syncAddress()
  nextTick(() => detailAddressInput.value?.$el?.focus?.() || detailAddressInput.value?.focus?.())
}
function syncAddress() {
  if (!roadAddress.value) return
  const value = [roadAddress.value, detailAddress.value.trim()].filter(Boolean).join(' ').replace(/\s+/g, ' ')
  form.address = postcode.value && value ? `[${postcode.value}] ${value}` : value
}
const { errorMessage: addressError, openJusoAddressSearch } = useJusoAddressPopup(handleAddressSelected)

const ownerOptions = computed(() => [{ ownerId: ALL_FILTER, ownerName: '전체 사업주', businessNumber: '' }, ...store.owners.value])
const operationCounts = computed(() => ({
  attention: store.washSites.value.filter((site) => operationState(site).rank > 0).length,
  missingLocation: store.washSites.value.filter((site) => site.latitude == null || site.longitude == null).length,
}))
const editingSite = computed(() => store.washSites.value.find((site) => Number(site.washSiteId) === Number(form.washSiteId)) || null)
const editingOwner = computed(() => ownerFor(editingSite.value))
const filteredSites = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return store.washSites.value.filter((site) => {
    const statusMatches = statusFilter.value === 'ALL' || site.status === statusFilter.value
    const text = [site.siteName, site.address, site.sitePhone, site.ownerName, site.businessNumber, site.courseName].filter(Boolean).join(' ').toLowerCase()
    return statusMatches && (!query || text.includes(query))
  }).sort((a, b) => operationState(b).rank - operationState(a).rank || new Date(a.createdAt || 0) - new Date(b.createdAt || 0))
})

onMounted(async () => {
  await store.load(ownerFilter())
  openRequestedSite(route.query.washSiteId)
})
watch(() => route.query.washSiteId, (washSiteId) => openRequestedSite(washSiteId))
watch(() => route.query.ownerId, async (ownerId) => {
  selectedOwnerId.value = String(ownerId || ALL_FILTER)
  await store.load(ownerFilter())
})
watch(() => route.query.status, (status) => {
  statusFilter.value = normalizeStatusFilter(status)
})

async function changeOwner() {
  await store.load(ownerFilter())
}
function ownerFilter() { return selectedOwnerId.value === ALL_FILTER ? '' : selectedOwnerId.value }
function normalizeStatusFilter(value) {
  const status = String(value || '').toUpperCase()
  return statusOptions.some((option) => option.value === status) ? status : 'ALL'
}
function openCreate() {
  Object.assign(form, { washSiteId: null, ownerId: ownerFilter() ? Number(ownerFilter()) : null, siteName: '', address: '', sitePhone: '', status: 'ACTIVE', courseName: '기본 세차', washPrice: null, latitude: null, longitude: null })
  applyOwnerAddress()
  originalAddress.value = ''
  addressError.value = ''
  formError.value = ''
  formVisible.value = true
}
function openEdit(site) {
  Object.assign(form, { washSiteId: site.washSiteId, ownerId: Number(site.ownerId) || null, siteName: site.siteName || '', address: site.address || '', sitePhone: site.sitePhone || '', status: site.status || 'ACTIVE', courseName: site.courseName || '기본 세차', washPrice: site.washPrice ?? null, latitude: site.latitude ?? null, longitude: site.longitude ?? null })
  originalAddress.value = String(site.address || '').trim()
  setStoredAddress(originalAddress.value)
  addressError.value = ''
  formError.value = ''
  formVisible.value = true
}

async function submit() {
  formError.value = validate()
  if (formError.value) return

  const address = form.address.trim()
  const payload = {
    ownerId: Number(form.ownerId),
    siteName: form.siteName.trim(),
    address,
    sitePhone: form.sitePhone,
    status: form.status,
    courseName: form.courseName.trim() || '기본 세차',
    washPrice: Number(form.washPrice),
  }

  const addressUnchanged = Boolean(form.washSiteId) && address === originalAddress.value
  if (addressUnchanged && form.latitude != null && form.longitude != null) {
    payload.latitude = Number(form.latitude)
    payload.longitude = Number(form.longitude)
  }

  if (await store.save(payload, form.washSiteId, ownerFilter())) {
    formVisible.value = false
  } else {
    formError.value = store.saveError.value || '세차장 정보를 저장하지 못했습니다.'
  }
}
function validate() {
  if (!form.ownerId) return '사업주를 선택해 주세요.'
  if (!form.siteName.trim()) return '세차장명을 입력해 주세요.'
  if (!form.address.trim()) return '주소를 입력해 주세요.'
  if (!isValidSitePhone(form.sitePhone)) return '전화번호는 숫자 8~12자리와 하이픈·괄호만 사용할 수 있습니다.'
  const price = Number(form.washPrice)
  if (!Number.isInteger(price) || price < 100 || price % 100 !== 0) return '세차가격은 100원 이상, 100원 단위로 입력해 주세요.'
  return ''
}
function isValidSitePhone(value) {
  const phone = String(value || '').trim()
  const digitCount = (phone.match(/\d/g) || []).length
  return phone.length <= 20
    && /^[0-9()\-\s]+$/.test(phone)
    && digitCount >= 8
    && digitCount <= 12
}
function applyOwnerAddress() {
  const owner = store.owners.value.find((item) => Number(item.ownerId) === Number(form.ownerId))
  const address = String(owner?.businessAddress || '').trim()
  setStoredAddress(address)
  form.latitude = null
  form.longitude = null
  addressError.value = ''
}
function openRequestedSite(washSiteId) { const site = store.washSites.value.find((item) => Number(item.washSiteId) === Number(washSiteId)); if (site) openEdit(site) }
function ownerFor(site) { return store.owners.value.find((owner) => Number(owner.ownerId) === Number(site?.ownerId)) || null }
function ownerContact(site) { const owner = ownerFor(site); return owner?.phone || owner?.emailAddress || '연락처 미등록' }
function ownerStatus(value) { return ({ ACTIVE: '활성', INACTIVE: '비활성', SUSPENDED: '이용 정지', PENDING: '승인 대기', APPROVED: '승인 완료' })[value] || value || '미확인' }
function operationState(site) { const reasons = []; if (site?.status === 'INACTIVE') reasons.push('운영 중지 상태'); if (site?.latitude == null || site?.longitude == null) reasons.push('지도 좌표 미등록'); if (site?.washPrice == null) reasons.push('세차 가격 미등록'); if (!site?.sitePhone) reasons.push('세차장 전화번호 미등록'); const owner = ownerFor(site); if (!owner?.phone && !owner?.emailAddress) reasons.push('사업주 연락처 미등록'); if (site?.status === 'INACTIVE') return { label: '운영 중지', severity: 'danger', rank: 4, reasons }; if (site?.latitude == null || site?.longitude == null) return { label: '위치 확인', severity: 'warn', rank: 3, reasons }; if (site?.washPrice == null || !site?.sitePhone) return { label: '정보 보완', severity: 'info', rank: 2, reasons }; if (!owner?.phone && !owner?.emailAddress) return { label: '연락처 확인', severity: 'info', rank: 1, reasons }; return { label: '운영 정상', severity: 'success', rank: 0, reasons: ['필수 운영 정보 등록 완료'] } }
function ownerLabel(owner) { return owner.businessNumber ? `${owner.ownerName || '이름 없음'} · ${owner.businessNumber}` : owner.ownerName }
function formatAmount(value) { return value == null ? '가격 미설정' : `${Number(value).toLocaleString('ko-KR')}원` }
function dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' }
</script>

<template>
  <section class="washsite-admin-page">
    <header class="washsite-page-header"><div><p>BUSINESS LOCATIONS</p><h1>세차장 관리</h1><span>사업장 운영 상태와 기본 세차 코스·가격·위치 정보를 관리합니다.</span></div><Button label="신규 세차장 등록" icon="pi pi-plus" @click="openCreate" /></header>
    <Message v-if="store.error.value" severity="error" :closable="false">{{ store.error.value }}</Message>
    <Message v-if="store.successMessage.value" severity="success" :closable="false">{{ store.successMessage.value }}</Message>

    <section class="washsite-summary-grid">
      <article><span class="blue"><i class="pi pi-building" /></span><div><small>전체 세차장</small><strong>{{ store.error.value ? '—' : store.counts.value.total }}</strong></div></article>
      <article><span class="green"><i class="pi pi-check-circle" /></span><div><small>운영 중</small><strong>{{ store.error.value ? '—' : store.counts.value.active }}</strong></div></article>
      <article><span class="gray"><i class="pi pi-pause-circle" /></span><div><small>운영 중지</small><strong>{{ store.error.value ? '—' : store.counts.value.inactive }}</strong></div></article>
      <article><span class="orange"><i class="pi pi-exclamation-triangle" /></span><div><small>운영 확인 필요</small><strong>{{ store.error.value ? '—' : operationCounts.attention }}</strong><em v-if="!store.error.value">좌표 미등록 {{ operationCounts.missingLocation }}곳</em></div></article>
    </section>

    <section class="washsite-list-panel">
      <header><div><p>WASH SITE DIRECTORY</p><h2>사업장 목록</h2></div><span>검색 결과 {{ filteredSites.length }}건</span></header>
      <div class="washsite-filter-bar"><span class="washsite-search"><i class="pi pi-search" /><InputText v-model="keyword" placeholder="세차장, 주소, 사업주 검색" /></span><Select v-model="selectedOwnerId" :options="ownerOptions" option-label="ownerName" option-value="ownerId" @change="changeOwner"><template #option="{ option }">{{ ownerLabel(option) }}</template></Select><Select v-model="statusFilter" :options="statusOptions" option-label="label" option-value="value" /><Button label="새로고침" icon="pi pi-refresh" severity="secondary" outlined :loading="store.loading.value" @click="store.load(ownerFilter())" /></div>
      <div v-if="store.loading.value && !store.washSites.value.length" class="washsite-loading"><ProgressSpinner /><strong>세차장 정보를 불러오고 있습니다.</strong></div>
      <DataTable v-else :value="filteredSites" paginator :rows="10" :rows-per-page-options="[10,20,50]" data-key="washSiteId" striped-rows scrollable :table-style="{ minWidth: '78rem' }" class="washsite-data-table" @row-click="openEdit($event.data)">
        <Column field="washSiteId" header="ID" sortable style="width: 6rem" />
        <Column header="운영 점검"><template #body="{ data }"><Tag :value="operationState(data).label" :severity="operationState(data).severity" /></template></Column>
        <Column field="siteName" header="세차장"><template #body="{ data }"><button type="button" class="washsite-name" @click.stop="openEdit(data)">{{ data.siteName || '이름 없음' }}</button><small>{{ data.address || '주소 미등록' }}</small></template></Column>
        <Column field="ownerName" header="사업주" sortable><template #body="{ data }"><strong>{{ data.ownerName || '—' }}</strong><small>{{ data.businessNumber || '사업자번호 미등록' }} · {{ ownerContact(data) }}</small></template></Column>
        <Column field="courseName" header="기본 코스"><template #body="{ data }">{{ data.courseName || '기본 세차' }}</template></Column>
        <Column field="washPrice" header="가격" sortable><template #body="{ data }"><strong>{{ formatAmount(data.washPrice) }}</strong></template></Column>
        <Column field="sitePhone" header="연락처"><template #body="{ data }">{{ data.sitePhone || '—' }}</template></Column>
        <Column field="status" header="운영 상태" sortable><template #body="{ data }"><Tag :value="data.status === 'ACTIVE' ? '운영 중' : '운영 중지'" :severity="data.status === 'ACTIVE' ? 'success' : 'secondary'" /></template></Column>
        <Column field="createdAt" header="등록일"><template #body="{ data }">{{ dateTime(data.createdAt) }}</template></Column>
        <template #empty><div class="washsite-empty"><i class="pi pi-building-columns" /><strong>{{ store.error.value ? '세차장 데이터를 표시할 수 없습니다.' : '조건에 맞는 세차장이 없습니다.' }}</strong></div></template>
      </DataTable>
    </section>

    <Dialog v-model:visible="formVisible" modal :header="form.washSiteId ? '세차장 정보 수정' : '신규 세차장 등록'" :style="{ width: 'min(96vw, 780px)' }">
      <form class="washsite-form" @submit.prevent="submit">
        <Message v-if="formError" severity="error" :closable="false">{{ formError }}</Message>
        <section v-if="editingSite" class="washsite-operation-card">
          <header><div><p>OPERATION CHECK</p><h3>운영 점검 정보</h3></div><Tag :value="operationState(editingSite).label" :severity="operationState(editingSite).severity" /></header>
          <dl><div><dt>사업주 상태</dt><dd>{{ ownerStatus(editingOwner?.status) }}</dd></div><div><dt>사업주 전화</dt><dd>{{ editingOwner?.phone || '미등록' }}</dd></div><div><dt>사업주 이메일</dt><dd>{{ editingOwner?.emailAddress || '미등록' }}</dd></div><div><dt>세차장 등록일</dt><dd>{{ dateTime(editingSite.createdAt) }}</dd></div></dl>
          <ul><li v-for="reason in operationState(editingSite).reasons" :key="reason"><i :class="operationState(editingSite).rank ? 'pi pi-exclamation-circle' : 'pi pi-check-circle'" />{{ reason }}</li></ul>
        </section>
        <div class="washsite-form-grid"><label><span>사업주 <b>*</b></span><Select v-model="form.ownerId" :options="store.owners.value" option-label="ownerName" option-value="ownerId" placeholder="사업주 선택" @change="applyOwnerAddress"><template #option="{ option }">{{ ownerLabel(option) }}</template></Select></label><label><span>운영 상태 <b>*</b></span><Select v-model="form.status" :options="[{ label: '운영 중', value: 'ACTIVE' }, { label: '운영 중지', value: 'INACTIVE' }]" option-label="label" option-value="value" /></label><label><span>세차장명 <b>*</b></span><InputText v-model.trim="form.siteName" maxlength="100" /></label><label><span>유선전화번호 <b>*</b></span><InputText v-model="form.sitePhone" inputmode="tel" maxlength="20" placeholder="02-1234-5678" /></label><label><span>기본 세차코스 <b>*</b></span><InputText v-model.trim="form.courseName" maxlength="100" /></label><label><span>세차가격 <b>*</b></span><InputNumber v-model="form.washPrice" :min="100" :step="100" suffix="원" /></label></div>
        <section class="washsite-address-card">
          <header><div><h3>사업장 주소</h3><p>사업주 등록 주소가 자동 입력되며 주소 검색으로 변경할 수 있습니다.</p></div></header>
          <div class="washsite-address-fields">
            <div class="washsite-address-row"><InputText :model-value="postcode" readonly placeholder="우편번호" /><Button label="주소 검색" icon="pi pi-search" severity="secondary" outlined type="button" @click="openJusoAddressSearch" /></div>
            <InputText :model-value="roadAddress" readonly placeholder="도로명주소" />
            <InputText ref="detailAddressInput" v-model="detailAddress" maxlength="100" placeholder="상세주소" :disabled="!addressSelected" @input="syncAddress" />
            <small v-if="!addressSelected && form.address">자동 입력된 주소입니다. 변경하려면 주소 검색을 이용해 주세요.</small>
            <Message v-if="addressError" severity="error" :closable="false">{{ addressError }}</Message>
          </div>
        </section>
        <footer><span v-if="form.washSiteId">세차장 #{{ form.washSiteId }}</span><span v-else /><div><Button label="취소" severity="secondary" outlined type="button" @click="formVisible = false" /><Button :label="form.washSiteId ? '변경사항 저장' : '세차장 등록'" icon="pi pi-check" type="submit" :loading="store.saving.value" /></div></footer>
      </form>
    </Dialog>
  </section>
</template>



