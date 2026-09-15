// Adapted from PrimeFaces Sakai Vue 5.0.0 layout/composables/layout.js (MIT).
import { computed, reactive } from 'vue'

const THEME_STORAGE_KEY = 'adminColorTheme'
const savedTheme = localStorage.getItem(THEME_STORAGE_KEY)
const initialDarkTheme = savedTheme
  ? savedTheme === 'dark'
  : window.matchMedia?.('(prefers-color-scheme: dark)').matches === true

const layoutConfig = reactive({
  preset: 'Aura',
  primary: 'blue',
  surface: null,
  darkTheme: initialDarkTheme,
  menuMode: 'static',
})

document.documentElement.classList.toggle('app-dark', layoutConfig.darkTheme)

const layoutState = reactive({
  staticMenuInactive: false,
  overlayMenuActive: false,
  mobileMenuActive: false,
  menuHoverActive: false,
  activePath: null,
})

export function useAdminLayout() {
  const isDesktop = () => window.innerWidth > 991

  const toggleMenu = () => {
    if (isDesktop()) {
      if (layoutConfig.menuMode === 'static') {
        layoutState.staticMenuInactive = !layoutState.staticMenuInactive
      } else {
        layoutState.overlayMenuActive = !layoutState.overlayMenuActive
      }
    } else {
      layoutState.mobileMenuActive = !layoutState.mobileMenuActive
    }
  }

  const closeMobileMenu = () => {
    layoutState.mobileMenuActive = false
    layoutState.overlayMenuActive = false
    layoutState.menuHoverActive = false
  }

  const toggleDarkMode = () => {
    layoutConfig.darkTheme = !layoutConfig.darkTheme
    document.documentElement.classList.toggle('app-dark', layoutConfig.darkTheme)
    localStorage.setItem(THEME_STORAGE_KEY, layoutConfig.darkTheme ? 'dark' : 'light')
  }

  const containerClass = computed(() => ({
    'layout-overlay': layoutConfig.menuMode === 'overlay',
    'layout-static': layoutConfig.menuMode === 'static',
    'layout-overlay-active': layoutState.overlayMenuActive,
    'layout-mobile-active': layoutState.mobileMenuActive,
    'layout-static-inactive': layoutState.staticMenuInactive,
  }))

  return {
    layoutConfig,
    layoutState,
    containerClass,
    toggleMenu,
    toggleDarkMode,
    closeMobileMenu,
    isDesktop,
  }
}

