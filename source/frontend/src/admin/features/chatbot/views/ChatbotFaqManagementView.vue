<script setup>
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import { computed, onMounted, reactive, ref } from 'vue'
import * as chatbotAdminApi from '@/admin/features/chatbot/api/chatbotAdminApi'

const faqs = ref([])
const unresolvedQuestions = ref([])
const metrics = ref({})
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const dialogVisible = ref(false)
const form = reactive(defaultForm())

const enabledCount = computed(() => faqs.value.filter((item) => item.enabled).length)
const openQuestions = computed(() => unresolvedQuestions.value.filter((item) => item.status === 'OPEN'))

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [faqData, questionData, metricData] = await Promise.all([
      chatbotAdminApi.getFaqs(),
      chatbotAdminApi.getUnresolvedQuestions(),
      chatbotAdminApi.getMetrics(),
    ])
    faqs.value = faqData || []
    unresolvedQuestions.value = questionData || []
    metrics.value = metricData || {}
  } catch (exception) {
    error.value = apiMessage(exception, '챗봇 관리 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function openCreate(prefilledQuestion = '') {
  Object.assign(form, defaultForm(), { question: prefilledQuestion })
  dialogVisible.value = true
}

function openEdit(item) {
  Object.assign(form, defaultForm(), item)
  dialogVisible.value = true
}

async function saveFaq() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    question: form.question.trim(),
    answer: form.answer.trim(),
    keywords: form.keywords.trim(),
    enabled: Boolean(form.enabled),
    displayOrder: Number(form.displayOrder || 0),
  }
  try {
    if (form.chatbotFaqId) await chatbotAdminApi.updateFaq(form.chatbotFaqId, payload)
    else await chatbotAdminApi.createFaq(payload)
    dialogVisible.value = false
    await loadAll()
    success.value = form.chatbotFaqId ? 'FAQ가 수정되었습니다.' : 'FAQ가 추가되었습니다.'
  } catch (exception) {
    error.value = apiMessage(exception, 'FAQ를 저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

async function toggleFaq(item) {
  error.value = ''
  try {
    await chatbotAdminApi.updateFaq(item.chatbotFaqId, {
      question: item.question,
      answer: item.answer,
      keywords: item.keywords || '',
      enabled: !item.enabled,
      displayOrder: Number(item.displayOrder || 0),
    })
    await loadAll()
  } catch (exception) {
    error.value = apiMessage(exception, 'FAQ 상태를 변경하지 못했습니다.')
  }
}

async function removeFaq(item) {
  if (!window.confirm(`“${item.question}” FAQ를 삭제할까요?`)) return
  error.value = ''
  try {
    await chatbotAdminApi.deleteFaq(item.chatbotFaqId)
    await loadAll()
    success.value = 'FAQ가 삭제되었습니다.'
  } catch (exception) {
    error.value = apiMessage(exception, 'FAQ를 삭제하지 못했습니다.')
  }
}

async function resolveQuestion(item) {
  error.value = ''
  try {
    await chatbotAdminApi.resolveQuestion(item.chatbotUnresolvedQuestionId)
    await loadAll()
    success.value = '미해결 질문을 처리 완료로 표시했습니다.'
  } catch (exception) {
    error.value = apiMessage(exception, '질문 상태를 변경하지 못했습니다.')
  }
}

function useAsFaq(item) {
  openCreate(item.maskedQuestion || '')
}

function defaultForm() {
  return { chatbotFaqId: null, question: '', answer: '', keywords: '', enabled: true, displayOrder: 0 }
}

function apiMessage(exception, fallback) {
  return exception.response?.data?.message || exception.response?.data || fallback
}

function dateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function decimal(value, digits = 1) {
  return Number(value || 0).toLocaleString('ko-KR', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}
</script>

<template>
  <section class="chatbot-admin-page">
    <header class="page-header">
      <div>
        <p>AI CUSTOMER SUPPORT</p>
        <h1>챗봇 FAQ 관리</h1>
        <span>검증된 답변 템플릿과 AI가 답하지 못한 질문을 관리합니다.</span>
      </div>
      <div class="header-actions">
        <Button label="새로고침" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="loadAll" />
        <Button label="FAQ 추가" icon="pi pi-plus" @click="openCreate()" />
      </div>
    </header>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="success" severity="success" :closable="false">{{ success }}</Message>

    <section class="summary-grid">
      <article><i class="pi pi-comments" /><div><small>요청 수 (서버 시작 후)</small><strong>{{ Number(metrics.requestCount || 0).toLocaleString('ko-KR') }}</strong></div></article>
      <article><i class="pi pi-stopwatch" /><div><small>평균 응답시간</small><strong>{{ decimal(metrics.averageResponseMilliseconds) }}ms</strong></div></article>
      <article class="warning"><i class="pi pi-exclamation-triangle" /><div><small>OpenAI 오류</small><strong>{{ Number(metrics.openAiFailureCount || 0).toLocaleString('ko-KR') }}</strong></div></article>
      <article><i class="pi pi-dollar" /><div><small>OpenAI 추정 비용</small><strong>${{ decimal(metrics.estimatedOpenAiCostUsd, 4) }}</strong></div></article>
      <article><i class="pi pi-check-circle" /><div><small>사용 중 FAQ</small><strong>{{ enabledCount }}</strong></div></article>
      <article class="warning"><i class="pi pi-question-circle" /><div><small>미해결 질문</small><strong>{{ openQuestions.length }}</strong></div></article>
    </section>

    <div v-if="loading && !faqs.length" class="loading"><ProgressSpinner /><strong>챗봇 정보를 불러오고 있습니다.</strong></div>
    <template v-else>
      <section class="panel">
        <header><div><p>VERIFIED ANSWERS</p><h2>답변 템플릿</h2></div><span>{{ enabledCount }}개 사용 중</span></header>
        <DataTable :value="faqs" data-key="chatbotFaqId" striped-rows paginator :rows="10" scrollable :table-style="{ minWidth: '64rem' }">
          <Column field="displayOrder" header="순서" sortable style="width:6rem" />
          <Column field="question" header="질문" sortable><template #body="{ data }"><strong>{{ data.question }}</strong><small>{{ data.keywords || '키워드 없음' }}</small></template></Column>
          <Column field="answer" header="답변"><template #body="{ data }"><span class="answer-preview">{{ data.answer }}</span></template></Column>
          <Column field="enabled" header="상태" style="width:8rem"><template #body="{ data }"><Tag :value="data.enabled ? '사용 중' : '중지'" :severity="data.enabled ? 'success' : 'secondary'" /></template></Column>
          <Column header="관리" style="width:15rem"><template #body="{ data }"><div class="row-actions"><Button :label="data.enabled ? '중지' : '사용'" :icon="data.enabled ? 'pi pi-pause' : 'pi pi-play'" size="small" text @click="toggleFaq(data)" /><Button label="수정" icon="pi pi-pencil" size="small" text @click="openEdit(data)" /><Button icon="pi pi-trash" aria-label="삭제" size="small" text severity="danger" @click="removeFaq(data)" /></div></template></Column>
          <template #empty><div class="empty"><i class="pi pi-comments" /><strong>등록된 FAQ가 없습니다.</strong></div></template>
        </DataTable>
      </section>

      <section class="panel">
        <header><div><p>UNRESOLVED QUEUE</p><h2>AI 미해결 질문</h2></div><span>{{ openQuestions.length }}건 대기</span></header>
        <DataTable :value="openQuestions" data-key="chatbotUnresolvedQuestionId" striped-rows paginator :rows="10" scrollable :table-style="{ minWidth: '52rem' }">
          <Column field="maskedQuestion" header="마스킹된 고객 질문"><template #body="{ data }"><strong>{{ data.maskedQuestion }}</strong><small>{{ data.reason }}</small></template></Column>
          <Column field="createdAt" header="발생 시각" sortable style="width:13rem"><template #body="{ data }">{{ dateTime(data.createdAt) }}</template></Column>
          <Column header="처리" style="width:17rem"><template #body="{ data }"><div class="row-actions"><Button label="FAQ로 만들기" icon="pi pi-plus" size="small" outlined @click="useAsFaq(data)" /><Button label="처리 완료" icon="pi pi-check" size="small" text @click="resolveQuestion(data)" /></div></template></Column>
          <template #empty><div class="empty"><i class="pi pi-check-circle" /><strong>대기 중인 미해결 질문이 없습니다.</strong></div></template>
        </DataTable>
      </section>
    </template>

    <Dialog v-model:visible="dialogVisible" modal :header="form.chatbotFaqId ? 'FAQ 수정' : 'FAQ 추가'" :style="{ width: 'min(94vw, 720px)' }">
      <form class="faq-form" @submit.prevent="saveFaq">
        <label><span>대표 질문</span><InputText v-model.trim="form.question" maxlength="300" required /></label>
        <label><span>답변</span><Textarea v-model.trim="form.answer" rows="7" maxlength="2000" required /></label>
        <label><span>키워드 <small>쉼표로 구분</small></span><InputText v-model.trim="form.keywords" maxlength="1000" placeholder="예약 시간, 입차, 30분 전" /></label>
        <div class="form-row">
          <label><span>표시 순서</span><InputNumber v-model="form.displayOrder" :min="0" :max="9999" /></label>
          <label class="switch-field"><span>고객 답변에 사용</span><ToggleSwitch v-model="form.enabled" /></label>
        </div>
        <footer><Button label="취소" severity="secondary" outlined type="button" @click="dialogVisible = false" /><Button label="저장" icon="pi pi-save" type="submit" :loading="saving" /></footer>
      </form>
    </Dialog>
  </section>
</template>

<style scoped>
.chatbot-admin-page{display:grid;gap:18px}.page-header{display:flex;align-items:flex-end;justify-content:space-between;gap:18px}.page-header p,.panel>header p{margin:0 0 5px;color:#2563eb;font:700 10px Inter,Pretendard,sans-serif;letter-spacing:.13em}.page-header h1{margin:0 0 7px;color:#172033;font-size:25px}.page-header span{color:#7b8494;font-size:12px}.header-actions,.row-actions{display:flex;align-items:center;gap:7px}.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.summary-grid article{display:flex;align-items:center;gap:13px;min-height:88px;padding:17px;border:1px solid var(--border);border-radius:14px;background:#fff}.summary-grid article>i{display:grid;place-items:center;width:42px;height:42px;border-radius:11px;color:#2563eb;background:#dbeafe;font-size:18px}.summary-grid article.warning>i{color:#b45309;background:#fef3c7}.summary-grid article>div{display:grid;gap:3px}.summary-grid small{color:#778195;font-size:11px}.summary-grid strong{color:#172033;font-size:22px}.panel{overflow:hidden;border:1px solid var(--border);border-radius:14px;background:#fff;box-shadow:0 3px 14px rgba(15,23,42,.03)}.panel>header{display:flex;align-items:center;justify-content:space-between;padding:18px 22px 14px;border-bottom:1px solid #edf0f4}.panel h2{margin:0;color:#1d2939;font-size:15px}.panel>header>span{padding:6px 9px;border-radius:20px;color:#64748b;background:#f1f5f9;font-size:10px}.panel :deep(.p-datatable-thead>tr>th){color:#667085;background:#fff;font-size:11px}.panel :deep(.p-datatable-tbody>tr>td){color:#475467;font-size:12px}.panel strong{display:block;color:#344054}.panel small{display:block;margin-top:4px;color:#98a2b3;font-size:10px}.answer-preview{display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:3;max-width:520px;line-height:1.5}.loading,.empty{min-height:180px;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#7b8494;font-size:11px}.empty i{color:#bdc8da;font-size:28px}.faq-form{display:grid;gap:15px}.faq-form>label,.form-row>label{display:grid;gap:7px}.faq-form label>span{color:#475467;font-size:11px;font-weight:700}.faq-form label>span small{display:inline;margin-left:5px;font-weight:400}.faq-form .p-inputtext,.faq-form .p-textarea,.faq-form .p-inputnumber{width:100%}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switch-field{display:flex!important;align-items:center;justify-content:space-between;padding:12px;border:1px solid #e7ecf2;border-radius:10px;background:#fafbfc}.faq-form footer{display:flex;justify-content:flex-end;gap:9px}@media(max-width:760px){.page-header{align-items:stretch;flex-direction:column}.header-actions{justify-content:flex-end}.summary-grid{grid-template-columns:1fr}.form-row{grid-template-columns:1fr}.panel{overflow-x:auto}}
</style>
