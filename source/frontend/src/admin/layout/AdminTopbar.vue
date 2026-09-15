<script setup>
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/stores/authStore'
import { useAdminLayout } from './layoutState'

const { layoutConfig, toggleMenu, toggleDarkMode } = useAdminLayout()
const router = useRouter()
const authStore = useAuthStore()
const profileOpen = ref(false)
const profileRef = ref(null)
const loggingOut = ref(false)

function toggleProfile() {
  profileOpen.value = !profileOpen.value
}

function closeOnOutside(event) {
  if (!profileRef.value?.contains(event.target)) profileOpen.value = false
}

function closeOnEscape(event) {
  if (event.key === 'Escape') profileOpen.value = false
}

async function logout() {
  loggingOut.value = true
  await authStore.logout()
  loggingOut.value = false
  profileOpen.value = false
  await router.replace({ name: 'admin-login' })
}

onMounted(() => {
  document.addEventListener('click', closeOnOutside)
  document.addEventListener('keydown', closeOnEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutside)
  document.removeEventListener('keydown', closeOnEscape)
})
</script>

<template>
  <header class="layout-topbar">
    <div class="layout-topbar-logo-container">
      <Button
        class="layout-menu-button layout-topbar-action"
        icon="pi pi-bars"
        text
        rounded
        aria-label="관리자 메뉴 열기"
        @click="toggleMenu"
      />
      <RouterLink to="/admin/dashboard" class="layout-topbar-logo" aria-label="관리자 대시보드">
        <span class="brand-mark"><i class="pi pi-sparkles" /></span>
        <span class="brand-copy">
          <strong>WASH OPS</strong>
          <small>ADMIN CONSOLE</small>
        </span>
      </RouterLink>
    </div>

    <div class="layout-topbar-actions">
      <Button
        :icon="layoutConfig.darkTheme ? 'pi pi-sun' : 'pi pi-moon'"
        text
        rounded
        severity="secondary"
        :aria-label="layoutConfig.darkTheme ? '라이트 모드로 전환' : '다크 모드로 전환'"
        :title="layoutConfig.darkTheme ? '라이트 모드' : '다크 모드'"
        @click="toggleDarkMode"
      />
      <Button icon="pi pi-bell" text rounded severity="secondary" aria-label="알림" />
      <div ref="profileRef" class="profile-wrapper">
        <button
          class="admin-profile"
          type="button"
          :aria-expanded="profileOpen"
          aria-haspopup="menu"
          @click.stop="toggleProfile"
        >
          <Avatar icon="pi pi-user" shape="circle" />
          <span>
            <strong>{{ authStore.userName || '관리자' }}</strong>
            <small>시스템 운영</small>
          </span>
          <i class="pi pi-angle-down profile-chevron" :class="{ open: profileOpen }" aria-hidden="true" />
        </button>

        <div v-if="profileOpen" class="profile-menu" role="menu">
          <div class="profile-menu-header">
            <strong>{{ authStore.userName || '관리자' }}</strong>
            <span>ADMIN</span>
          </div>
          <button type="button" role="menuitem" :disabled="loggingOut" @click="logout">
            <i class="pi pi-sign-out" />
            <span>로그아웃</span>
            <small v-if="loggingOut">처리 중</small>
          </button>
        </div>
      </div>
    </div>
  </header>
</template>


