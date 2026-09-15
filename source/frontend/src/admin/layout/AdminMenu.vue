<script setup>
import AdminMenuItem from './AdminMenuItem.vue'
import { adminMenu } from './adminMenu'
</script>

<template>
  <ul class="layout-menu" aria-label="관리자 메뉴">
    <AdminMenuItem
      v-for="(item, index) in adminMenu"
      :key="item.key"
      :item="item"
      :index="index"
      root
    />
  </ul>
</template>

