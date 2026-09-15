import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/features/auth/stores/authStore'
import { ROLE } from '@/shared/auth/roles'

import authRoutes from './authRoutes'
import customerRoutes from './customerRoutes'
import ownerRoutes from './ownerRoutes'
import adminRoutes from './adminRoutes'
import postPageRoutes from './postPageRoutes'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...authRoutes,
    ...customerRoutes,
    ...ownerRoutes,
    ...adminRoutes,
    ...postPageRoutes,
  ],
})

const ROLE_HOME_PATH = {
  ROLE_CUSTOMER: '/customer/dashboard',
  ROLE_OWNER: '/owner/dashboard',
  ROLE_ADMIN: '/admin/dashboard',
}

const PATH_ROLE = [
  { path: '/customer', role: ROLE.CUSTOMER },
  { path: '/owner', role: ROLE.OWNER },
  { path: '/admin', role: ROLE.ADMIN },
]

function getRequiredRole(path) {
  const matched = PATH_ROLE.find((route) =>
    path === route.path || path.startsWith(`${route.path}/`),
  )
  return matched?.role || ''
}

router.beforeEach((to) => {
  const authStore = useAuthStore()
  const accessToken = localStorage.getItem('accessToken')
  const role = localStorage.getItem('role')
  const requiredRole = getRequiredRole(to.path)

  if (to.name === 'admin-login') {
    if (accessToken && role === ROLE.ADMIN) return { name: 'admin-dashboard' }
    return true
  }

  if (requiredRole && !accessToken) {
    if (requiredRole === ROLE.ADMIN) {
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
    return '/home'
  }

  if (requiredRole && role !== requiredRole) {
    return ROLE_HOME_PATH[role] || '/home'
  }

  if (
    role === ROLE.CUSTOMER
    && authStore.isSuspended
    && to.meta.feature
    && !authStore.canUseFeature(to.meta.feature)
  ) {
    window.alert('정지회원은 해당 기능을 이용할 수 없습니다.')
    return { name: 'customer-wash-history' }
  }

  return true
})

router.afterEach((to) => {
  const adminActive = to.matched.some((record) => record.meta.adminSakai)
  const savedTheme = localStorage.getItem('adminColorTheme')
  const darkTheme = savedTheme
    ? savedTheme === 'dark'
    : window.matchMedia?.('(prefers-color-scheme: dark)').matches === true

  document.documentElement.classList.toggle('admin-route-active', adminActive)
  document.documentElement.classList.toggle('app-dark', adminActive && darkTheme)
})

export default router
