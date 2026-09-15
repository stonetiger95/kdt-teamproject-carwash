<script setup>
import Button from 'primevue/button'
import InputMask from 'primevue/inputmask'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import Select from 'primevue/select'
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { checkEmail, checkLoginId, checkPhone } from '@/features/auth/api/authApi'
import { useJusoAddressPopup } from '@/admin/features/address/composables/useJusoAddressPopup'
import { registerOwner } from '@/admin/features/ownerRegistration/api/ownerRegistrationApi'

const router = useRouter()
const form = reactive({ loginId: '', password: '', ownerName: '', businessNumber: '', phone: '', emailAddress: '', businessAddress: '' })
const confirmPassword = ref('')
const emailDomainOption = ref('naver.com')
const emailId = ref('')
const directEmailDomain = ref('')
const postcode = ref('')
const roadAddress = ref('')
const detailAddress = ref('')
const detailAddressInput = ref(null)
const submitting = ref(false)
const checking = reactive({ loginId: false, phone: false, email: false })
const checks = reactive({ loginId: null, phone: null, email: null })
const checkedValues = reactive({ loginId: '', phone: '', email: '' })
const formError = ref('')
const successMessage = ref('')

const emailDomains = [
  { label: 'naver.com', value: 'naver.com' },
  { label: 'gmail.com', value: 'gmail.com' },
  { label: 'daum.net', value: 'daum.net' },
  { label: '직접 입력', value: 'direct' },
]
const emailAddress = computed(() => {
  const domain = emailDomainOption.value === 'direct' ? directEmailDomain.value.trim() : emailDomainOption.value
  return emailId.value.trim() && domain ? `${emailId.value.trim()}@${domain}`.toLowerCase() : ''
})
const passwordsMatch = computed(() => Boolean(form.password && form.password === confirmPassword.value))

function handleAddressSelected(address) {
  postcode.value = String(address.zipNo || '').trim()
  roadAddress.value = String(address.roadAddrPart1 || address.roadFullAddr || '').trim()
  detailAddress.value = String(address.addrDetail || '').trim()
  syncAddress()
  nextTick(() => detailAddressInput.value?.$el?.focus?.() || detailAddressInput.value?.focus?.())
}
const { errorMessage: addressError, openJusoAddressSearch } = useJusoAddressPopup(handleAddressSelected)

function syncAddress() {
  const value = [roadAddress.value, detailAddress.value.trim()].filter(Boolean).join(' ').replace(/\s+/g, ' ')
  form.businessAddress = postcode.value && value ? `[${postcode.value}] ${value}` : value
}
function invalidate(field) { checks[field] = null; checkedValues[field] = '' }
function normalizeLoginId() { form.loginId = form.loginId.replace(/[^A-Za-z0-9]/g, ''); invalidate('loginId') }
function normalizeEmail() { invalidate('email') }

async function verifyLoginId() {
  if (!form.loginId) return
  checking.loginId = true
  try {
    checks.loginId = Boolean(await checkLoginId(form.loginId))
    checkedValues.loginId = checks.loginId ? form.loginId : ''
  } catch { checks.loginId = false } finally { checking.loginId = false }
}
async function verifyPhone() {
  const phone = String(form.phone || '').replace(/\D/g, '')
  if (!/^01[016789]\d{7,8}$/.test(phone)) { checks.phone = false; return false }
  checking.phone = true
  try {
    checks.phone = Boolean(await checkPhone(phone))
    checkedValues.phone = checks.phone ? phone : ''
    return checks.phone
  } catch { checks.phone = false; return false } finally { checking.phone = false }
}
async function verifyEmail() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailAddress.value)) { checks.email = false; return false }
  checking.email = true
  try {
    checks.email = Boolean(await checkEmail(emailAddress.value))
    checkedValues.email = checks.email ? emailAddress.value : ''
    return checks.email
  } catch { checks.email = false; return false } finally { checking.email = false }
}

function validate() {
  if (!/^[A-Za-z0-9]+$/.test(form.loginId)) return '로그인 아이디는 영문과 숫자로 입력해 주세요.'
  if (checkedValues.loginId !== form.loginId || !checks.loginId) return '로그인 아이디 중복 확인을 완료해 주세요.'
  if (form.password.length < 4) return '비밀번호는 4자 이상 입력해 주세요.'
  if (!passwordsMatch.value) return '비밀번호 확인이 일치하지 않습니다.'
  if (!form.ownerName.trim()) return '사업주명을 입력해 주세요.'
  if (!/^\d{3}-\d{2}-\d{5}$/.test(form.businessNumber || '')) return '사업자등록번호를 올바르게 입력해 주세요.'
  if (!roadAddress.value || !form.businessAddress) return '주소 검색을 통해 사업장 주소를 선택해 주세요.'
  return ''
}

async function submit() {
  formError.value = ''
  successMessage.value = ''
  const validationMessage = validate()
  if (validationMessage) { formError.value = validationMessage; return }

  const phone = String(form.phone || '').replace(/\D/g, '')
  if (checkedValues.phone !== phone || !checks.phone || checkedValues.email !== emailAddress.value || !checks.email) {
    const [phoneOk, emailOk] = await Promise.all([verifyPhone(), verifyEmail()])
    if (!phoneOk || !emailOk) { formError.value = '전화번호와 이메일의 중복 확인 결과를 확인해 주세요.'; return }
  }

  submitting.value = true
  try {
    await registerOwner({ ...form, loginId: form.loginId.trim(), ownerName: form.ownerName.trim(), phone, emailAddress: emailAddress.value })
    successMessage.value = '사업주 계정이 등록되었습니다. 세차장 관리에서 사업장을 연결할 수 있습니다.'
    window.setTimeout(() => router.push({ name: 'admin-wash-sites' }), 900)
  } catch (error) {
    formError.value = error.response?.data?.message || error.response?.data || '사업주 등록에 실패했습니다.'
  } finally { submitting.value = false }
}
</script>

<template>
  <section class="owner-registration-page">
    <header class="owner-page-header">
      <div><p>BUSINESS OWNER ONBOARDING</p><h1>사업주 등록</h1><span>관리자가 사업주 계정을 직접 생성합니다. 등록 후 세차장 관리에서 사업장을 연결하세요.</span></div>
      <Button label="세차장 관리" icon="pi pi-building" severity="secondary" outlined @click="router.push({ name: 'admin-wash-sites' })" />
    </header>

    <Message v-if="formError" severity="error" :closable="false">{{ formError }}</Message>
    <Message v-if="successMessage" severity="success" :closable="false">{{ successMessage }}</Message>

    <form class="owner-form-card" @submit.prevent="submit">
      <section>
        <header><span><i class="pi pi-key" /></span><div><h2>로그인 정보</h2><p>사업주가 사용할 계정 정보를 입력합니다.</p></div></header>
        <div class="owner-form-grid">
          <label class="wide"><span>로그인 아이디 <b>*</b></span><div class="owner-inline-field"><InputText v-model="form.loginId" maxlength="50" placeholder="영문·숫자" @input="normalizeLoginId" /><Button label="중복 확인" severity="secondary" outlined type="button" :loading="checking.loginId" @click="verifyLoginId" /></div><small v-if="checks.loginId !== null" :class="checks.loginId ? 'valid' : 'invalid'">{{ checks.loginId ? '사용 가능한 아이디입니다.' : '이미 사용 중이거나 확인할 수 없는 아이디입니다.' }}</small></label>
          <label><span>비밀번호 <b>*</b></span><Password v-model="form.password" :feedback="false" toggle-mask input-class="owner-full-input" placeholder="4자 이상" /></label>
          <label><span>비밀번호 확인 <b>*</b></span><Password v-model="confirmPassword" :feedback="false" toggle-mask input-class="owner-full-input" /><small v-if="confirmPassword" :class="passwordsMatch ? 'valid' : 'invalid'">{{ passwordsMatch ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.' }}</small></label>
        </div>
      </section>

      <section>
        <header><span><i class="pi pi-id-card" /></span><div><h2>사업주 정보</h2><p>계정 소유자와 사업자 정보를 입력합니다.</p></div></header>
        <div class="owner-form-grid">
          <label><span>사업주명 <b>*</b></span><InputText v-model.trim="form.ownerName" maxlength="50" /></label>
          <label><span>사업자등록번호 <b>*</b></span><InputMask v-model="form.businessNumber" mask="999-99-99999" placeholder="000-00-00000" /></label>
          <label><span>휴대전화 <b>*</b></span><InputMask v-model="form.phone" mask="999-9999-9999" placeholder="010-0000-0000" @update:model-value="invalidate('phone')" /><small v-if="checks.phone === false" class="invalid">이미 등록되었거나 형식이 올바르지 않습니다.</small></label>
          <label><span>이메일 <b>*</b></span><div class="owner-email-field"><InputText v-model="emailId" placeholder="이메일" @input="normalizeEmail" /><i>@</i><Select v-model="emailDomainOption" :options="emailDomains" option-label="label" option-value="value" @change="normalizeEmail" /></div><InputText v-if="emailDomainOption === 'direct'" v-model="directEmailDomain" placeholder="도메인 직접 입력" @input="normalizeEmail" /><small v-if="checks.email === false" class="invalid">이미 등록되었거나 형식이 올바르지 않습니다.</small></label>
        </div>
      </section>

      <section>
        <header><span><i class="pi pi-map-marker" /></span><div><h2>사업장 주소</h2><p>도로명주소 검색 결과와 상세주소를 함께 저장합니다.</p></div></header>
        <div class="owner-address-fields">
          <div class="owner-inline-field"><InputText :model-value="postcode" readonly placeholder="우편번호" /><Button label="주소 검색" icon="pi pi-search" severity="secondary" outlined type="button" @click="openJusoAddressSearch" /></div>
          <InputText :model-value="roadAddress" readonly placeholder="도로명주소" />
          <InputText ref="detailAddressInput" v-model="detailAddress" maxlength="100" placeholder="상세주소" @input="syncAddress" />
          <Message v-if="addressError" severity="error" :closable="false">{{ addressError }}</Message>
        </div>
      </section>

      <footer><span><i class="pi pi-info-circle" /> 등록 즉시 사업주 로그인이 가능한 계정이 생성됩니다.</span><Button label="사업주 등록" icon="pi pi-user-plus" type="submit" :loading="submitting" /></footer>
    </form>
  </section>
</template>

<style scoped>
.owner-registration-page{display:grid;gap:18px;max-width:1050px;margin:0 auto}.owner-page-header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.owner-page-header p{margin:0 0 5px;color:#2563eb;font:700 10px Inter,Pretendard,sans-serif;letter-spacing:.13em}.owner-page-header h1{margin:0 0 7px;color:#172033;font-size:25px}.owner-page-header span{color:#7b8494;font-size:12px}.owner-form-card{overflow:hidden;border:1px solid var(--border);border-radius:14px;background:#fff;box-shadow:0 3px 14px rgba(15,23,42,.035)}.owner-form-card>section{display:grid;gap:17px;padding:23px 25px;border-bottom:1px solid #edf0f4}.owner-form-card>section>header{display:flex;align-items:center;gap:12px}.owner-form-card>section>header>span{display:grid;place-items:center;width:38px;height:38px;border-radius:10px;color:#2563eb;background:#eaf2ff}.owner-form-card h2{margin:0;color:#1d2939;font-size:15px}.owner-form-card header p{margin:4px 0 0;color:#8a94a6;font-size:10px}.owner-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.owner-form-grid label,.owner-address-fields{display:grid;gap:7px}.owner-form-grid label.wide{grid-column:1/-1}.owner-form-grid label>span{color:#475467;font-size:11px;font-weight:700}.owner-form-grid label>span b{color:#dc2626}.owner-form-grid :deep(.p-inputtext),.owner-form-grid :deep(.p-password),.owner-form-grid :deep(.p-select),.owner-address-fields .p-inputtext{width:100%}.owner-inline-field{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px}.owner-email-field{display:grid;grid-template-columns:minmax(100px,1fr) auto minmax(145px,.8fr);align-items:center;gap:8px}.owner-email-field i{color:#98a2b3;font-style:normal}.owner-form-grid small{font-size:10px}.owner-form-grid small.valid{color:#16835f}.owner-form-grid small.invalid{color:#dc2626}.owner-address-fields{max-width:750px}.owner-form-card>footer{display:flex;align-items:center;justify-content:space-between;gap:15px;padding:18px 25px;background:#fafbfc}.owner-form-card>footer>span{color:#667085;font-size:10px}.owner-form-card>footer>span i{margin-right:5px;color:#2563eb}@media(max-width:680px){.owner-page-header,.owner-form-card>footer{align-items:stretch;flex-direction:column}.owner-form-grid{grid-template-columns:1fr}.owner-form-grid label.wide{grid-column:auto}.owner-email-field{grid-template-columns:1fr auto}.owner-email-field .p-select{grid-column:1/-1}.owner-form-card>section{padding:19px}.owner-form-card>footer{padding:16px 19px}.owner-form-card>footer .p-button{width:100%}}
</style>


