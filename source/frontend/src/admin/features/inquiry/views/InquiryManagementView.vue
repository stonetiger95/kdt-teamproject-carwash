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
import Textarea from 'primevue/textarea'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useInquiryManagement } from '@/admin/features/inquiry/composables/useInquiryManagement'

const route = useRoute()
const router = useRouter()
const {
  inquiries, selectedInquiry, refundPolicy, loading, detailLoading, actionLoading,
  error, actionMessage, counts, loadList, loadDetail, submitAnswer, submitRefund, clearSelection,
} = useInquiryManagement()

const keyword = ref('')
const statusFilter = ref('ALL')
const typeFilter = ref('ALL')
const answerText = ref('')
const selectedTemplate = ref('')
const refundDialogVisible = ref(false)
const refundForm = reactive({ refundAmount: 0, reason: '고객 환불 문의 승인' })

const statusOptions = [
  { label: '전체 상태', value: 'ALL' }, { label: '답변 대기', value: 'WAITING' },
  { label: '답변 완료', value: 'ANSWERED' }, { label: '종료', value: 'CLOSED' },
]
statusFilter.value = normalizeStatusFilter(route.query.status)
const typeOptions = [
  { label: '전체 유형', value: 'ALL' }, { label: '환불', value: 'REFUND' },
  { label: '세차장', value: 'WASH_SITE' }, { label: '기능 이용', value: 'SERVICE' },
  { label: '기타', value: 'ETC' },
]
const answerTemplates = [
  { label: '답변 템플릿 선택', value: '' },
  { label: '접수 및 확인 안내', value: 'RECEIVED', text: '안녕하세요. 문의해 주신 내용을 확인했습니다. 관련 내역을 검토한 후 안내드리겠습니다.' },
  { label: '환불 검토 안내', value: 'REFUND_REVIEW', text: '안녕하세요. 요청하신 결제 및 환불 가능 금액을 확인했습니다. 환불 정책과 처리 결과를 아래와 같이 안내드립니다.\n\n- 확인 결과: \n- 처리 내용: ' },
  { label: '세차장 확인 안내', value: 'SITE_CHECK', text: '안녕하세요. 문의하신 세차장 정보를 확인했습니다.\n\n- 확인한 사업장: \n- 확인 결과: \n- 안내 사항: ' },
  { label: '기능 이용 안내', value: 'SERVICE_GUIDE', text: '안녕하세요. 문의하신 기능의 이용 방법을 안내드립니다.\n\n- 확인 결과: \n- 이용 방법: \n- 추가 확인 사항: ' },
]

const filteredInquiries = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return inquiries.value.filter((item) => {
    const matchesStatus = statusFilter.value === 'ALL' || item.status === statusFilter.value
    const matchesType = typeFilter.value === 'ALL' || item.inquiryType === typeFilter.value
    const searchable = [item.inquiryId, item.title, item.writerName, item.writerId, item.siteName]
      .filter(Boolean).join(' ').toLowerCase()
    return matchesStatus && matchesType && (!query || searchable.includes(query))
  }).sort((a, b) => inquiryRank(b) - inquiryRank(a) || new Date(a.createdAt || 0) - new Date(b.createdAt || 0))
})

const slaCounts = computed(() => ({
  over24: inquiries.value.filter((item) => item.status === 'WAITING' && waitingHours(item.createdAt) >= 24).length,
  over48: inquiries.value.filter((item) => item.status === 'WAITING' && waitingHours(item.createdAt) >= 48).length,
}))

const detailVisible = computed({
  get: () => Boolean(route.params.inquiryId),
  set: (visible) => { if (!visible) closeDetail() },
})
const answers = computed(() => {
  if (Array.isArray(selectedInquiry.value?.answers) && selectedInquiry.value.answers.length) return selectedInquiry.value.answers
  if (!selectedInquiry.value?.answer) return []
  return [{ inquiryAnswerId: 'legacy', answer: selectedInquiry.value.answer, answererRole: 'ADMIN', createdAt: selectedInquiry.value.answeredAt }]
})
const canRefund = computed(() => selectedInquiry.value?.inquiryType === 'REFUND'
  && (Number(selectedInquiry.value?.refundableAmount || 0) > 0 || benefitOnlyRefund.value))
const benefitOnlyRefund = computed(() => Number(refundPolicy.value?.payableAmount) === 0
  && (Number(refundPolicy.value?.couponDiscountAmount || 0) > 0 || Number(refundPolicy.value?.mileageUsedAmount || 0) > 0))
const maxRefundAmount = computed(() => Number(refundPolicy.value?.payableAmount
  ?? selectedInquiry.value?.refundableAmount ?? 0))
const responseChecklist = computed(() => {
  const item = selectedInquiry.value
  if (!item) return []
  if (item.inquiryType === 'REFUND') return ['결제 번호와 결제금액 확인', '기존 환불액과 환불 가능액 확인', '쿠폰·마일리지 복원 정책 확인']
  if (item.inquiryType === 'WASH_SITE') return ['관련 세차장과 연락처 확인', '문의 내용의 운영 사실 확인', '고객에게 안내할 후속 조치 확인']
  if (item.inquiryType === 'SERVICE') return ['이용한 기능과 발생 상황 확인', '재현 조건 또는 오류 내용을 확인', '고객이 수행할 다음 단계를 안내']
  return ['문의 핵심 내용 확인', '작성자 구분과 관련 정보 확인', '답변에 후속 조치 포함']
})

onMounted(async () => {
  await loadList()
  if (route.params.inquiryId) await loadDetail(route.params.inquiryId)
})

watch(() => route.params.inquiryId, async (inquiryId, previousId) => {
  if (inquiryId && inquiryId !== previousId) await loadDetail(inquiryId)
})
watch(() => route.query.status, (status) => {
  statusFilter.value = normalizeStatusFilter(status)
})

function openDetail(item) {
  router.push({ name: 'admin-inquiry-detail', params: { inquiryId: item.inquiryId } })
}
function normalizeStatusFilter(value) {
  const status = String(value || '').toUpperCase()
  return statusOptions.some((option) => option.value === status) ? status : 'ALL'
}
function closeDetail() {
  clearSelection()
  answerText.value = ''
  selectedTemplate.value = ''
  refundDialogVisible.value = false
  if (route.name !== 'admin-inquiries') router.push({ name: 'admin-inquiries' })
}
async function registerAnswer() {
  if (await submitAnswer(answerText.value)) answerText.value = ''
}
function applyTemplate() {
  const template = answerTemplates.find((item) => item.value === selectedTemplate.value)
  if (template?.text) answerText.value = template.text
}
function openRelatedSite() { if (selectedInquiry.value?.washSiteId) router.push({ name:'admin-wash-sites', query:{ washSiteId:selectedInquiry.value.washSiteId } }) }
function openRelatedPayment() { if (selectedInquiry.value?.paymentHistoryId) router.push({ name:'admin-payments', query:{ keyword:String(selectedInquiry.value.paymentHistoryId) } }) }
function openRefundDialog() {
  refundForm.refundAmount = maxRefundAmount.value
  refundForm.reason = '고객 환불 문의 승인'
  refundDialogVisible.value = true
}
async function processRefund() {
  const amount = Number(refundForm.refundAmount)
  if (!Number.isInteger(amount) || amount < 0 || (!benefitOnlyRefund.value && amount < 1) || amount > maxRefundAmount.value) return
  if (await submitRefund({ refundAmount: amount, reason: refundForm.reason })) refundDialogVisible.value = false
}
function statusLabel(value) { return ({ WAITING: '답변 대기', ANSWERED: '답변 완료', CLOSED: '종료' })[value] || value || '미확인' }
function statusSeverity(value) { return value === 'WAITING' ? 'warn' : value === 'ANSWERED' ? 'success' : 'secondary' }
function typeLabel(value) { return ({ REFUND: '환불', WASH_SITE: '세차장', SERVICE: '기능 이용', ETC: '기타' })[value] || '기타' }
function roleLabel(value) { return ({ ADMIN: '관리자', OWNER: '사업주', CUSTOMER: '고객' })[value] || value || '미확인' }
function waitingHours(value) { if (!value) return 0; const time = new Date(value).getTime(); return Number.isNaN(time) ? 0 : Math.max(0, (Date.now() - time) / 3600000) }
function inquiryPriority(item) { if (item.status === 'WAITING' && waitingHours(item.createdAt) >= 48) return { label:'긴급', severity:'danger', rank:4 }; if (item.status === 'WAITING' && (waitingHours(item.createdAt) >= 24 || item.inquiryType === 'REFUND')) return { label:'높음', severity:'warn', rank:3 }; if (item.status === 'WAITING') return { label:'일반', severity:'info', rank:2 }; return { label:'처리됨', severity:'secondary', rank:1 } }
function inquiryRank(item) { return inquiryPriority(item).rank }
function supportArea(item) { if (item.inquiryType === 'REFUND' || item.paymentHistoryId) return '결제 지원'; if (item.inquiryType === 'WASH_SITE' || item.washSiteId) return '사업장 지원'; return '고객 지원' }
function slaText(item) { if (item.status !== 'WAITING') return item.answeredAt ? `답변 ${dateTime(item.answeredAt)}` : '답변 처리됨'; const hours = waitingHours(item.createdAt); if (hours >= 48) return `${Math.floor(hours - 24)}시간 기한 초과`; if (hours >= 24) return `${Math.max(1, Math.floor(hours - 24))}시간 기한 초과`; return `${Math.max(1, Math.ceil(24 - hours))}시간 내 답변 권장` }
function slaClass(item) { if (item.status !== 'WAITING') return 'done'; return waitingHours(item.createdAt) >= 48 ? 'danger' : waitingHours(item.createdAt) >= 24 ? 'warning' : '' }
function dateTime(value) { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date) }
function amount(value) { return value == null ? '—' : `${Number(value).toLocaleString('ko-KR')}원` }
</script>

<template>
  <section class="inquiry-admin-page">
    <header class="inquiry-page-header">
      <div><p>CUSTOMER SUPPORT</p><h1>문의 관리</h1><span>고객과 사업주의 문의를 확인하고 답변·환불 업무를 처리합니다.</span></div>
      <Button label="새로고침" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="loadList" />
    </header>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="actionMessage" severity="success" :closable="false">{{ actionMessage }}</Message>

    <section class="inquiry-summary-grid">
      <article><span class="summary-icon blue"><i class="pi pi-inbox" /></span><div><small>전체 문의</small><strong>{{ error ? '—' : counts.total }}</strong></div></article>
      <article><span class="summary-icon orange"><i class="pi pi-clock" /></span><div><small>답변 대기</small><strong>{{ error ? '—' : counts.waiting }}</strong></div></article>
      <article><span class="summary-icon red"><i class="pi pi-hourglass" /></span><div><small>24시간 기한 초과</small><strong>{{ error ? '—' : slaCounts.over24 }}</strong><em v-if="!error">48시간 이상 {{ slaCounts.over48 }}건</em></div></article>
      <article><span class="summary-icon red"><i class="pi pi-replay" /></span><div><small>환불 문의</small><strong>{{ error ? '—' : counts.refund }}</strong></div></article>
    </section>

    <section class="inquiry-list-panel">
      <header>
        <div><p>INQUIRY QUEUE</p><h2>문의 접수 목록</h2></div>
        <span>검색 결과 {{ filteredInquiries.length }}건</span>
      </header>
      <div class="inquiry-filter-bar">
        <span class="inquiry-search"><i class="pi pi-search" /><InputText v-model="keyword" placeholder="제목, 작성자, 세차장 검색" /></span>
        <Select v-model="statusFilter" :options="statusOptions" option-label="label" option-value="value" />
        <Select v-model="typeFilter" :options="typeOptions" option-label="label" option-value="value" />
      </div>

      <div v-if="loading && !inquiries.length" class="inquiry-loading"><ProgressSpinner /><strong>문의 목록을 불러오고 있습니다.</strong></div>
      <DataTable v-else :value="filteredInquiries" paginator :rows="10" :rows-per-page-options="[10, 20, 50]" striped-rows scrollable :table-style="{ minWidth: '72rem' }" data-key="inquiryId" class="inquiry-data-table" @row-click="openDetail($event.data)">
        <Column field="inquiryId" header="번호" sortable style="width: 7rem" />
        <Column header="우선순위" sortable><template #body="{ data }"><Tag :value="inquiryPriority(data).label" :severity="inquiryPriority(data).severity" /></template></Column>
        <Column field="inquiryType" header="유형·담당" sortable><template #body="{ data }"><span class="inquiry-type">{{ typeLabel(data.inquiryType) }}</span><small>{{ supportArea(data) }}</small></template></Column>
        <Column field="title" header="제목" sortable><template #body="{ data }"><button class="inquiry-title-button" type="button" @click.stop="openDetail(data)">{{ data.title || '제목 없음' }}</button><small v-if="data.siteName">{{ data.siteName }}</small></template></Column>
        <Column field="writerName" header="작성자" sortable><template #body="{ data }"><strong class="inquiry-writer">{{ data.writerName || data.writerId || '—' }}</strong><small>{{ roleLabel(data.writerRole) }}</small></template></Column>
        <Column field="status" header="상태" sortable><template #body="{ data }"><Tag :value="statusLabel(data.status)" :severity="statusSeverity(data.status)" /></template></Column>
        <Column field="createdAt" header="접수·답변 기한" sortable><template #body="{ data }">{{ dateTime(data.createdAt) }}<small class="inquiry-sla" :class="slaClass(data)">{{ slaText(data) }}</small></template></Column>
        <template #empty><div class="inquiry-empty"><i class="pi pi-comments" /><strong>{{ error ? '문의 데이터를 표시할 수 없습니다.' : '조건에 맞는 문의가 없습니다.' }}</strong></div></template>
      </DataTable>
    </section>

    <Dialog v-model:visible="detailVisible" modal class="inquiry-detail-dialog" :header="selectedInquiry ? `문의 #${selectedInquiry.inquiryId}` : '문의 상세'" :style="{ width: 'min(96vw, 1050px)' }">
      <div v-if="detailLoading" class="inquiry-loading detail"><ProgressSpinner /><strong>문의 내용을 불러오고 있습니다.</strong></div>
      <div v-else-if="selectedInquiry" class="inquiry-detail-content">
        <header class="inquiry-detail-title"><div><span>{{ typeLabel(selectedInquiry.inquiryType) }} · {{ supportArea(selectedInquiry) }}</span><h2>{{ selectedInquiry.title || '제목 없음' }}</h2></div><div><Tag :value="inquiryPriority(selectedInquiry).label" :severity="inquiryPriority(selectedInquiry).severity" /><Tag :value="statusLabel(selectedInquiry.status)" :severity="statusSeverity(selectedInquiry.status)" /></div></header>
        <dl class="inquiry-meta-grid">
          <div><dt>작성자</dt><dd>{{ selectedInquiry.writerName || selectedInquiry.writerId || '—' }}</dd></div>
          <div><dt>구분</dt><dd>{{ roleLabel(selectedInquiry.writerRole) }}</dd></div>
          <div><dt>담당 영역</dt><dd>{{ supportArea(selectedInquiry) }}</dd></div>
          <div><dt>우선순위</dt><dd>{{ inquiryPriority(selectedInquiry).label }}</dd></div>
          <div><dt>접수일</dt><dd>{{ dateTime(selectedInquiry.createdAt) }}</dd></div>
          <div><dt>답변 기한</dt><dd :class="['sla-value', slaClass(selectedInquiry)]">{{ slaText(selectedInquiry) }}</dd></div>
          <div><dt>답변 수</dt><dd>{{ answers.length }}건</dd></div>
          <div><dt>최종 답변일</dt><dd>{{ dateTime(selectedInquiry.answeredAt) }}</dd></div>
        </dl>
        <section class="inquiry-workflow-card"><div><span class="done"><i class="pi pi-inbox" /></span><strong>문의 접수</strong><small>{{ dateTime(selectedInquiry.createdAt) }}</small></div><i class="pi pi-angle-right" /><div><span :class="{done:selectedInquiry.status!=='WAITING'}"><i class="pi pi-search" /></span><strong>내용 확인</strong><small>{{ supportArea(selectedInquiry) }}</small></div><i class="pi pi-angle-right" /><div><span :class="{done:answers.length}"><i class="pi pi-send" /></span><strong>답변 처리</strong><small>{{ answers.length ? dateTime(selectedInquiry.answeredAt) : slaText(selectedInquiry) }}</small></div></section>
        <article class="inquiry-content-card"><h3>문의 내용</h3><p>{{ selectedInquiry.content || selectedInquiry.inquiryContent || '내용이 없습니다.' }}</p></article>

        <section v-if="selectedInquiry.washSiteId" class="inquiry-site-card"><span><i class="pi pi-building" /></span><div><small>관련 세차장</small><strong>{{ selectedInquiry.siteName || `세차장 #${selectedInquiry.washSiteId}` }}</strong><p>{{ selectedInquiry.siteAddress || '주소 미등록' }} · {{ selectedInquiry.sitePhone || '전화번호 미등록' }}</p></div><Button label="세차장 관리" icon="pi pi-external-link" severity="secondary" outlined size="small" @click="openRelatedSite" /></section>

        <section v-if="selectedInquiry.inquiryType === 'REFUND'" class="inquiry-refund-card">
          <header><div><p>REFUND REQUEST</p><h3>환불 요청 정보</h3></div><div class="refund-actions"><Button v-if="selectedInquiry.paymentHistoryId" label="결제 내역" icon="pi pi-external-link" severity="secondary" outlined @click="openRelatedPayment" /><Button v-if="canRefund" label="환불 처리" icon="pi pi-replay" severity="danger" @click="openRefundDialog" /></div></header>
          <dl><div><dt>결제 번호</dt><dd>#{{ selectedInquiry.paymentHistoryId || '—' }}</dd></div><div><dt>결제금액</dt><dd>{{ amount(selectedInquiry.paymentAmount) }}</dd></div><div><dt>환불 완료액</dt><dd>{{ amount(selectedInquiry.refundedAmount) }}</dd></div><div><dt>환불 가능액</dt><dd>{{ amount(selectedInquiry.refundableAmount) }}</dd></div></dl>
          <Message v-if="!canRefund" severity="secondary" :closable="false">현재 환불 가능한 잔액이 없습니다.</Message>
        </section>

        <section class="inquiry-conversation">
          <header><div><p>CONVERSATION</p><h3>답변 내역</h3></div><span>{{ answers.length }}건</span></header>
          <ol v-if="answers.length"><li v-for="(item, index) in answers" :key="item.inquiryAnswerId || index"><span><i class="pi pi-user" /></span><div><header><strong>{{ roleLabel(item.answererRole) }}{{ item.answererId ? ` · ${item.answererId}` : '' }}</strong><time>{{ dateTime(item.createdAt) }}</time></header><p>{{ item.answer }}</p></div></li></ol>
          <div v-else class="conversation-empty"><i class="pi pi-clock" /><span>아직 등록된 답변이 없습니다.</span></div>
        </section>

        <section class="inquiry-checklist"><header><div><p>RESPONSE CHECKLIST</p><h3>답변 전 확인</h3></div><span>{{ supportArea(selectedInquiry) }}</span></header><ul><li v-for="item in responseChecklist" :key="item"><i class="pi pi-check-circle" />{{ item }}</li></ul></section>
        <form class="inquiry-answer-form" @submit.prevent="registerAnswer"><div class="answer-form-header"><label for="admin-inquiry-answer">새 답변 작성</label><Select v-model="selectedTemplate" :options="answerTemplates" option-label="label" option-value="value" @change="applyTemplate" /></div><Message severity="secondary" :closable="false">템플릿은 화면에서만 문구를 채우며 서버에 저장되지 않습니다. 내용을 확인하고 수정한 뒤 등록하세요.</Message><Textarea id="admin-inquiry-answer" v-model="answerText" rows="6" maxlength="2000" placeholder="문의 내용을 확인한 후 답변을 입력하세요." required /><footer><small>{{ answerText.length }} / 2,000자</small><Button label="답변 등록" icon="pi pi-send" type="submit" :loading="actionLoading" :disabled="!answerText.trim()" /></footer></form>
      </div>
    </Dialog>

    <Dialog v-model:visible="refundDialogVisible" modal header="환불 처리 확인" :style="{ width: 'min(92vw, 520px)' }">
      <form class="inquiry-refund-form" @submit.prevent="processRefund"><Message severity="warn" :closable="false">환불은 결제 상태를 변경하는 작업입니다. 금액과 사유를 다시 확인해 주세요.</Message><label>환불 금액<InputNumber v-model="refundForm.refundAmount" :min="benefitOnlyRefund ? 0 : 1" :max="maxRefundAmount" suffix="원" :readonly="refundPolicy?.partialRefundAllowed === false" /></label><small>최대 환불 가능 금액 {{ amount(maxRefundAmount) }}</small><label>처리 사유<InputText v-model.trim="refundForm.reason" maxlength="500" required /></label><footer><Button label="취소" severity="secondary" outlined type="button" @click="refundDialogVisible = false" /><Button label="환불 실행" icon="pi pi-exclamation-triangle" severity="danger" type="submit" :loading="actionLoading" /></footer></form>
    </Dialog>
  </section>
</template>

