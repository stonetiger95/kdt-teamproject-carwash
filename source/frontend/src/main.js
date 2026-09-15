import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'
import 'primeicons/primeicons.css'

import App from './App.vue'
import router from '@/app/router'
import { useAuthStore } from '@/features/auth/stores/authStore'
import * as authApi from '@/features/auth/api/authApi'

import './style.css'
import '@/admin/styles/main.css'

const BACKEND_INSTANCE_KEY = 'serverInstanceId'
const FRONTEND_INSTANCE_KEY = 'frontendInstanceId'
const SESSION_STATUS_INTERVAL_MS = 15000
const AdminAura = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}', 100: '{blue.100}', 200: '{blue.200}',
      300: '{blue.300}', 400: '{blue.400}', 500: '{blue.500}',
      600: '{blue.600}', 700: '{blue.700}', 800: '{blue.800}',
      900: '{blue.900}', 950: '{blue.950}',
    },
  },
})

function clearAuthStorage() {
  for (const key of ['accessToken', 'loginId', 'userName', 'role', 'customerId', 'ownerId', 'customerStatus']) {
    localStorage.removeItem(key)
  }
}

function checkFrontendServerRestart() {
  const currentId = __FRONTEND_INSTANCE_ID__
  const savedId = localStorage.getItem(FRONTEND_INSTANCE_KEY)
  const tokenWasPresent = Boolean(localStorage.getItem('accessToken'))
  localStorage.setItem(FRONTEND_INSTANCE_KEY, currentId)

  if (savedId && savedId !== currentId) {
    clearAuthStorage()
    return tokenWasPresent
  }
  return false
}

async function getCurrentBackendInstanceId() {
  const serverInfo = await authApi.getServerInstance()
  return serverInfo.instanceId
}

async function checkBackendServerRestart() {
  const currentId = await getCurrentBackendInstanceId()
  const savedId = localStorage.getItem(BACKEND_INSTANCE_KEY)
  const tokenWasPresent = Boolean(localStorage.getItem('accessToken'))
  localStorage.setItem(BACKEND_INSTANCE_KEY, currentId)

  if (savedId && savedId !== currentId) {
    clearAuthStorage()
    return { restarted: tokenWasPresent, instanceId: currentId }
  }
  return { restarted: false, instanceId: currentId }
}

async function startApp() {
  const frontendRestarted = checkFrontendServerRestart()
  let backendRestarted = false
  let lastBackendInstanceId = localStorage.getItem(BACKEND_INSTANCE_KEY)

  try {
    const result = await checkBackendServerRestart()
    backendRestarted = result.restarted
    lastBackendInstanceId = result.instanceId
  } catch (error) {
    console.error('초기 Spring Boot 서버 확인 실패', error)
  }

  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  const authStore = useAuthStore(pinia)
  window.addEventListener('auth:unauthorized', (event) => {
    const wasAdminRoute = router.currentRoute.value.path.startsWith('/admin')
    const sessionWasReplaced = event.detail?.code === 'SESSION_REPLACED'
    authStore.clearAuth()

    if (sessionWasReplaced) {
      window.alert('다른 기기에서 로그인했거나 계정 정보가 변경되어 현재 접속이 종료되었습니다.')
    }

    if (wasAdminRoute) {
      router.replace({
        name: 'admin-login',
        query: { reason: sessionWasReplaced ? 'session-replaced' : 'expired' },
      })
    } else if (router.currentRoute.value.name !== 'home') {
      router.replace({ name: 'home' })
    }
  })

  if (frontendRestarted || backendRestarted) authStore.clearAuth()

  app.use(router)
  app.use(PrimeVue, {
    theme: {
      preset: AdminAura,
      options: {
        darkModeSelector: '.app-dark',
        cssLayer: false,
      },
    },
    ripple: true,
  })
  app.mount('#app')

  window.setInterval(async () => {
    try {
      const currentBackendInstanceId = await getCurrentBackendInstanceId()
      if (lastBackendInstanceId && lastBackendInstanceId !== currentBackendInstanceId) {
        const wasAdminRoute = router.currentRoute.value.path.startsWith('/admin')
        authStore.clearAuth()
        localStorage.setItem(BACKEND_INSTANCE_KEY, currentBackendInstanceId)
        lastBackendInstanceId = currentBackendInstanceId
        await router.replace(wasAdminRoute
          ? { name: 'admin-login', query: { reason: 'server-restarted' } }
          : { name: 'home' })
        return
      }

      localStorage.setItem(BACKEND_INSTANCE_KEY, currentBackendInstanceId)
      lastBackendInstanceId = currentBackendInstanceId
    } catch (error) {
      console.error('Spring Boot 서버 상태 확인 실패', error)
    }
  }, 3000)

  let sessionCheckRunning = false
  window.setInterval(async () => {
    if (!authStore.accessToken || sessionCheckRunning) return

    sessionCheckRunning = true
    try {
      await authApi.getSessionStatus()
    } catch {
      // Authentication failures are handled by the shared Axios interceptor.
    } finally {
      sessionCheckRunning = false
    }
  }, SESSION_STATUS_INTERVAL_MS)
}

startApp()
