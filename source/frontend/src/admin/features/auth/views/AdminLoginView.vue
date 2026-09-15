<script setup>
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminLogin as loginApi, getServerInstance } from '@/features/auth/api/authApi'
import { useAuthStore } from '@/features/auth/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ loginId: '', password: '' })
const submitting = ref(false)
const errorMessage = ref('')
const backendState = ref('checking')

const reasonMessage = computed(() => {
  if (route.query.reason === 'expired') return '인증이 만료되었습니다. 다시 로그인해 주세요.'
  if (route.query.reason === 'session-replaced') return '다른 기기 로그인 또는 계정 정보 변경으로 기존 접속이 종료되었습니다.'
  if (route.query.reason === 'forbidden') return '관리자 계정만 접근할 수 있습니다.'
  if (route.query.reason === 'server-restarted') return '서버가 재시작되어 안전하게 로그아웃했습니다.'
  return ''
})

const backendLabel = computed(() => ({
  checking: '서버 확인 중',
  connected: '관리 서버 연결됨',
  unavailable: '관리 서버 연결 안 됨',
}[backendState.value]))

async function submit() {
  if (!form.loginId.trim() || !form.password) {
    errorMessage.value = '아이디와 비밀번호를 입력해 주세요.'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const response = await loginApi({
      loginId: form.loginId.trim(),
      password: form.password,
    })

    if (response.role !== 'ROLE_ADMIN') {
      authStore.clearAuth()
      errorMessage.value = '관리자 권한이 없는 계정입니다.'
      return
    }

    authStore.applyLoginResponse(response)
  } catch (error) {
    errorMessage.value = error.response
      ? '아이디 또는 비밀번호를 확인해 주세요.'
      : '관리 서버에 연결할 수 없습니다.'
    return
  } finally {
    submitting.value = false
  }

  const redirect = String(route.query.redirect || '')
  const safeRedirect = redirect.startsWith('/admin/') && redirect !== '/admin/login'
    ? redirect
    : '/admin/dashboard'
  await router.replace(safeRedirect)
}

onMounted(async () => {
  try {
    await getServerInstance()
    backendState.value = 'connected'
  } catch (error) {
    backendState.value = 'unavailable'
  }
})
</script>

<template>
  <main class="admin-login-page">
    <section class="login-brand-panel">
      <div class="login-brand-content">
        <span class="login-brand-mark"><i class="pi pi-sparkles" /></span>
        <p class="login-kicker">WASH OPERATIONS</p>
        <h1>운영의 이상 신호를<br />가장 먼저 확인합니다.</h1>
        <p class="login-description">
          장비 상태, 문의 대응, 번호판 검토와 세차장 운영 현황을 위한 관리자 전용 콘솔입니다.
        </p>
        <div class="login-security-note">
          <i class="pi pi-shield" />
          <span><strong>관리자 전용</strong>인가된 계정만 접근할 수 있습니다.</span>
        </div>
      </div>
    </section>

    <section class="login-form-panel">
      <div class="login-form-card">
        <div class="login-mobile-brand"><span class="brand-mark"><i class="pi pi-sparkles" /></span><strong>WASH OPS</strong></div>
        <div class="login-heading">
          <span class="connection-state" :class="`state-${backendState}`"><i class="pi pi-circle-fill" /> {{ backendLabel }}</span>
          <h2>관리자 로그인</h2>
          <p>기존 관리자 계정으로 로그인해 주세요.</p>
        </div>

        <Message v-if="reasonMessage" severity="warn" :closable="false">{{ reasonMessage }}</Message>
        <Message v-if="errorMessage" severity="error" :closable="false">{{ errorMessage }}</Message>

        <form class="admin-login-form" @submit.prevent="submit">
          <label for="admin-login-id">아이디</label>
          <InputText id="admin-login-id" v-model="form.loginId" autocomplete="username" placeholder="관리자 아이디" fluid />

          <label for="admin-login-password">비밀번호</label>
          <Password
            v-model="form.password"
            input-id="admin-login-password"
            autocomplete="current-password"
            placeholder="비밀번호"
            :feedback="false"
            toggle-mask
            fluid
          />

          <Button type="submit" label="관리자 로그인" icon="pi pi-sign-in" :loading="submitting" fluid />
        </form>

        <p class="login-help"><i class="pi pi-info-circle" /> 계정 문의는 시스템 담당자에게 요청해 주세요.</p>
      </div>
    </section>
  </main>
</template>

