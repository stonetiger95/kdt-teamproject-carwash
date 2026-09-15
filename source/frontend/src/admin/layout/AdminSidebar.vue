<script setup>
// Adapted from PrimeFaces Sakai Vue 5.0.0 AppSidebar.vue (MIT).
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminMenu from './AdminMenu.vue'
import { useAdminLayout } from './layoutState'

const route = useRoute()
const sidebarRef = ref(null)
const { layoutState, isDesktop, closeMobileMenu } = useAdminLayout()
let outsideClickListener = null

watch(
  () => route.path,
  (path) => {
    layoutState.activePath = isDesktop() ? null : path
    closeMobileMenu()
  },
  { immediate: true },
)

watch(
  () => layoutState.overlayMenuActive,
  (active) => {
    if (active && isDesktop()) bindOutsideClickListener()
    else unbindOutsideClickListener()
  },
)

function bindOutsideClickListener() {
  if (outsideClickListener) return
  outsideClickListener = (event) => {
    const menuButton = document.querySelector('.layout-menu-button')
    const insideSidebar = sidebarRef.value?.contains(event.target)
    const insideButton = menuButton?.contains(event.target)
    if (!insideSidebar && !insideButton) closeMobileMenu()
  }
  document.addEventListener('click', outsideClickListener)
}

function unbindOutsideClickListener() {
  if (!outsideClickListener) return
  document.removeEventListener('click', outsideClickListener)
  outsideClickListener = null
}

onBeforeUnmount(unbindOutsideClickListener)
</script>

<template>
  <aside ref="sidebarRef" class="layout-sidebar">
    <div class="sidebar-context">
      <span class="status-dot" />
      <span>
        <small>통합 관제 상태</small>
        <strong>운영 준비</strong>
      </span>
    </div>
    <AdminMenu />
  </aside>
</template>

