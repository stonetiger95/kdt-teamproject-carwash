<script setup>
// Adapted from PrimeFaces Sakai Vue 5.0.0 AppMenuItem.vue (MIT).
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { openPopupWindow } from '@/admin/shared/browser/openPopupWindow'
import { useAdminLayout } from './layoutState'

defineOptions({ name: 'AdminMenuItem' })

const props = defineProps({
  item: { type: Object, default: () => ({}) },
  index: { type: Number, default: 0 },
  root: { type: Boolean, default: false },
})

const route = useRoute()
const { layoutState, closeMobileMenu } = useAdminLayout()
const open = ref(props.item.key === 'operations' || props.item.key === 'equipment')

const hasActiveChild = computed(() =>
  props.item.items?.some((child) => route.path === child.to),
)

const isActive = computed(() => route.path === props.item.to || hasActiveChild.value)

watch(hasActiveChild, (active) => {
  if (active) open.value = true
}, { immediate: true })

function itemClick(event) {
  if (props.item.disabled) {
    event.preventDefault()
    return
  }

  if (props.item.items) {
    event.preventDefault()
    open.value = !open.value
    layoutState.activePath = open.value ? props.item.key : null
    return
  }

  closeMobileMenu()
}

function externalItemClick(event) {
  itemClick(event)
  if (!props.item.popup || event.defaultPrevented) return
  event.preventDefault()
  openPopupWindow(props.item.to, props.item.popup)
}
</script>

<template>
  <li
    :class="{
      'layout-root-menuitem': root,
      'active-menuitem': isActive,
      'menu-heading-item': root && item.label,
    }"
  >
    <button
      v-if="root && item.label"
      class="layout-menuitem-root-button"
      type="button"
      :aria-expanded="open"
      @click="itemClick"
    >
      <i :class="item.icon" class="layout-menuitem-icon" />
      <span class="layout-menuitem-text">{{ item.label }}</span>
      <i class="pi pi-fw pi-angle-down layout-submenu-toggler" />
    </button>

    <a
      v-else-if="item.external"
      :href="item.to"
      :target="item.target || '_blank'"
      rel="noopener"
      :class="{ 'active-route': isActive }"
      @click="externalItemClick"
    >
      <i :class="item.icon" class="layout-menuitem-icon" />
      <span class="layout-menuitem-text">{{ item.label }}</span>
      <i class="pi pi-external-link external-icon" aria-label="새 창" />
    </a>

    <RouterLink
      v-else-if="item.to"
      :to="item.to"
      :class="{ 'active-route': isActive }"
      @click="itemClick"
    >
      <i :class="item.icon" class="layout-menuitem-icon" />
      <span class="layout-menuitem-text">{{ item.label }}</span>
    </RouterLink>

    <Transition name="layout-submenu">
      <ul v-if="item.items" v-show="!item.label || open" class="layout-submenu">
        <AdminMenuItem
          v-for="(child, childIndex) in item.items"
          :key="child.label + '_' + child.to"
          :item="child"
          :index="childIndex"
        />
      </ul>
    </Transition>
  </li>
</template>

