<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminSidebar from './AdminSidebar.vue'
import AdminFooter from './AdminFooter.vue'
import AdminTopbar from './AdminTopbar.vue'
import { useAdminLayout } from './layoutState'

const route = useRoute()
const { containerClass, closeMobileMenu } = useAdminLayout()
const breadcrumb = computed(() => route.meta.breadcrumb ?? ['관리자'])

watch(() => route.fullPath, closeMobileMenu)
onBeforeUnmount(closeMobileMenu)
</script>

<template>
  <div class="layout-wrapper" :class="containerClass">
    <AdminTopbar />
    <AdminSidebar />

    <main class="layout-main-container">
      <div class="layout-breadcrumb" aria-label="현재 위치">
        <RouterLink to="/admin/dashboard" aria-label="대시보드"><i class="pi pi-home" aria-hidden="true" /></RouterLink>
        <template v-for="item in breadcrumb" :key="item">
          <i class="pi pi-angle-right breadcrumb-separator" aria-hidden="true" />
          <span>{{ item }}</span>
        </template>
      </div>
      <div class="layout-main">
        <RouterView />
      </div>
      <AdminFooter />
    </main>

    <button
      class="layout-mask"
      type="button"
      aria-label="메뉴 닫기"
      @click="closeMobileMenu"
    />
  </div>
</template>

