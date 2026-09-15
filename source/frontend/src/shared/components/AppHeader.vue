<template>
  <div class="app-layout">
    <header class="luxury-top-header">
      <div class="header-container">
        <div class="logo-area"></div>

        <nav ref="navBox" class="horizontal-nav-box" @keydown.esc="closeMenu">
          <RouterLink
            :to="dashboardTarget"
            :class="['nav-btn', 'home-nav-btn', { active: dashboardActive }]"
            active-class=""
          >
            HOME
          </RouterLink>
          <RouterLink
            v-if="isCustomer"
            :to="{ name: 'customer-wash-site-map' }"
            class="nav-btn"
          >
            세차장 찾기
          </RouterLink>
          <RouterLink :to="inquiryPath" class="nav-btn">문의사항</RouterLink>
          <RouterLink
            v-if="isOwner"
            :to="{ name: 'owner-reservations' }"
            class="nav-btn"
          >
            예약 관리
          </RouterLink>
          <div
            v-if="isOwner"
            :class="['nav-dropdown', { open: openMenu === 'washSite' }]"
            @mouseenter="openDropdown('washSite')"
            @mouseleave="closeMenu"
            @focusout="closeMenuOnFocusOut"
          >
            <button
              :class="['nav-btn', { active: washSiteMenuActive }]"
              type="button"
              @click="toggleMenu('washSite')"
            >
              세차장 관리 <span class="dropdown-arrow">⌄</span>
            </button>
            <div class="dropdown-menu">
              <RouterLink
                :to="{ name: 'owner-wash-sites' }"
                @click="closeMenu"
              >
                세차장 관리
              </RouterLink>
              <RouterLink
                :to="{ name: 'owner-business-hours' }"
                @click="closeMenu"
              >
                영업시간 관리
              </RouterLink>
              <RouterLink
                :to="{ name: 'owner-device-list' }"
                @click="closeMenu"
              >
                장치 관리
              </RouterLink>
            </div>
          </div>
          <div
            v-if="isAdmin"
            :class="['nav-dropdown', { open: openMenu === 'owner' }]"
            @mouseenter="openDropdown('owner')"
            @mouseleave="closeMenu"
            @focusout="closeMenuOnFocusOut"
          >
            <button
              :class="['nav-btn', { active: ownerMenuActive }]"
              type="button"
              @click="toggleMenu('owner')"
            >
              사업주 <span class="dropdown-arrow">▾</span>
            </button>
            <div class="dropdown-menu">
              <RouterLink :to="{ name: 'admin-owner-register' }" @click="closeMenu">
                사업주 등록
              </RouterLink>
              <RouterLink :to="{ name: 'admin-wash-sites' }" @click="closeMenu">
                사업장 관리
              </RouterLink>
            </div>
          </div>

          <div
            v-if="isAdmin"
            :class="['nav-dropdown', { open: openMenu === 'member' }]"
            @mouseenter="openDropdown('member')"
            @mouseleave="closeMenu"
            @focusout="closeMenuOnFocusOut"
          >
            <button
              :class="['nav-btn', { active: memberMenuActive }]"
              type="button"
              @click="toggleMenu('member')"
            >
              고객 <span class="dropdown-arrow">▾</span>
            </button>
            <div class="dropdown-menu">
              <RouterLink :to="{ name: 'admin-member-manage' }" @click="closeMenu">
                회원 관리
              </RouterLink>
              <RouterLink :to="{ name: 'admin-vehicles' }" @click="closeMenu">
                차량 관리
              </RouterLink>
            </div>
          </div>

          <RouterLink v-if="isAdmin" :to="{ name: 'admin-plate-detection-logs' }" class="nav-btn wide">차량 번호 인식 로그</RouterLink>
          <RouterLink
            v-if="isAdmin"
            :to="{ name: 'admin-predictive-maintenance' }"
            class="nav-btn"
            target="_blank"
            rel="noopener"
            @click="closeMenu"
          >
            예지보전
          </RouterLink>
          <RouterLink
            v-if="isAdmin"
            :to="{ name: 'admin-reservations' }"
            class="nav-btn"
          >
            예약 관리
          </RouterLink>
          <div
            v-if="isCustomer"
            :class="['nav-dropdown', { open: openMenu === 'reservation' }]"
            @mouseenter="openDropdown('reservation')"
            @mouseleave="closeMenu"
            @focusout="closeMenuOnFocusOut"
          >
            <button
              :class="['nav-btn', { active: reservationMenuActive }]"
              type="button"
              @click="toggleMenu('reservation')"
            >
              세차 예약 <span class="dropdown-arrow">▾</span>
            </button>
            <div class="dropdown-menu">
              <RouterLink :to="{ name: 'customer-reservation-create' }" @click="closeMenu">
                예약하기
              </RouterLink>
              <RouterLink :to="{ name: 'customer-reservations' }" @click="closeMenu">
                내 예약
              </RouterLink>
            </div>
          </div>
          <div
            v-if="isCustomer"
            :class="['nav-dropdown', { open: openMenu === 'vehicle' }]"
            @mouseenter="openDropdown('vehicle')"
            @mouseleave="closeMenu"
            @focusout="closeMenuOnFocusOut"
          >
            <button
              :class="['nav-btn', { active: vehicleMenuActive }]"
              type="button"
              @click="toggleMenu('vehicle')"
            >
              차량 정보 <span class="dropdown-arrow">▾</span>
            </button>
            <div class="dropdown-menu">
              <RouterLink :to="{ name: 'customer-vehicles' }" @click="closeMenu">
                차량 조회
              </RouterLink>
              <RouterLink :to="{ name: 'customer-vehicles-registration' }" @click="closeMenu">
                차량 등록
              </RouterLink>
            </div>
          </div>
          <RouterLink v-if="isOwner" :to="{ name: 'owner-mypage' }" class="nav-btn">마이페이지</RouterLink>
          <RouterLink v-if="isCustomer" to="/customer/mypage" class="nav-btn">마이페이지</RouterLink>
        </nav>

        <div class="user-utility-zone">
          <span class="user-name-tag">{{ authStore.userName }}님</span>
          <button @click="logout" class="btn-header-logout">
            Logout
          </button>
        </div>
      </div>
    </header>

    <div
      v-if="authStore.isSuspended"
      class="suspended-status-notice"
      role="status"
    >
      <strong>현재 계정은 정지 상태입니다.</strong>
      <span>문의와 내역 조회, 마이페이지 조회만 이용할 수 있습니다.</span>
    </div>

    <main class="page-content-area">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { useRoute, useRouter, RouterView, RouterLink } from "vue-router"
import { useAuthStore } from "@/features/auth/stores/authStore"
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { ROLE } from "../auth/roles"

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const openMenu = ref("")
const navBox = ref(null)

const isCustomer = computed(() => authStore.role === ROLE.CUSTOMER)
const isOwner = computed(() => authStore.role === ROLE.OWNER)
const isAdmin = computed(() => authStore.role === ROLE.ADMIN)

const ownerMenuActive = computed(() => [
    "admin-owner-register",
    "admin-wash-sites",
    "admin-owner-wash-sites"
].includes(String(route.name || "")))

const memberMenuActive = computed(() => [
    "admin-member-manage",
    "admin-vehicles",
    "admin-vehicle-detail"
].includes(String(route.name || "")))

const vehicleMenuActive = computed(() => [
    "customer-vehicles",
    "customer-vehicles-registration"
].includes(String(route.name || "")))

const reservationMenuActive = computed(() => [
    "customer-reservation-create",
    "customer-reservations"
].includes(String(route.name || "")))

const washSiteMenuActive = computed(() => [
    "owner-wash-sites",
    "owner-business-hours",
    "owner-device-list",
    "owner-device-create",
    "owner-device-edit"
].includes(String(route.name || "")))

const dashboardTarget = computed(() => {
    switch (authStore.role) {
        case ROLE.ADMIN:
            return { name: "admin-dashboard" }
        case ROLE.OWNER:
            return { name: "owner-dashboard" }
        default:
            return authStore.isSuspended
                ? { name: "customer-wash-history" }
                : { name: "customer-dashboard" }
    }
})

const dashboardActive = computed(() =>
    String(route.name || "") === String(dashboardTarget.value.name || "")
)

const inquiryPath = computed(() => {
    switch (authStore.role) {
        case ROLE.ADMIN:
            return "/admin/inquiries"
        case ROLE.OWNER:
            return "/owner/inquiries"
        default:
            return "/customer/inquiries"
    }
})

async function logout() {
    await authStore.logout()
    await router.replace({ name: "home" })
}

function toggleMenu(menuName) {
    openMenu.value = openMenu.value === menuName
        ? ""
        : menuName
}

function openDropdown(menuName) {
    openMenu.value = menuName
}

function closeMenu() {
    openMenu.value = ""
}

function closeMenuOnFocusOut(event) {
    if (!event.currentTarget.contains(event.relatedTarget)) closeMenu()
}

function closeMenuOnOutsidePointer(event) {
    if (!navBox.value?.contains(event.target)) closeMenu()
}

watch(() => route.fullPath, closeMenu)

onMounted(() => document.addEventListener("pointerdown", closeMenuOnOutsidePointer))
onBeforeUnmount(() => document.removeEventListener("pointerdown", closeMenuOnOutsidePointer))
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background-color: #f8fafc;
  display: flex;
  flex-direction: column;
  font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.luxury-top-header {
  width: 100%;
  position: sticky;
  top: 0;
  left: 0;
  z-index: 1000;
  background-color: #ffffff;
  border-bottom: 1px solid #eef2f6;
  box-shadow: 0 4px 15px rgba(148, 163, 184, 0.05);
}

.header-container {
  position: relative;
  max-width: var(--app-shell-width, 1340px);
  height: 70px;
  margin: 0 auto;
  padding: 0 2rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo-area {
  width: 150px;
  flex: 0 0 150px;
}

.horizontal-nav-box {
  position: absolute;
  left: 45%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 5px;
  border-radius: 14px;
  background-color: #f1f5f9;
}

.nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 112px;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 10px;
  color: #64748b;
  font-size: 0.9rem;
  font-weight: 700;
  line-height: 1.25;
  text-align: center;
  text-decoration: none;
  word-break: keep-all;
  white-space: nowrap;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.nav-btn.wide {
  min-width: 178px;
}

.nav-btn:hover {
  color: #0a1931;
  background-color: rgba(255, 255, 255, 0.72);
}

.nav-btn:focus {
  outline: none;
}

.nav-btn:focus-visible {
  outline: 2px solid #7897bd;
  outline-offset: 2px;
}

.router-link-active.nav-btn:not(.home-nav-btn),
.nav-btn.active {
  color: #ffffff;
  background: linear-gradient(135deg, #1c3d70 0%, #0a1931 100%);
  box-shadow: 0 4px 10px rgba(10, 25, 49, 0.15);
}

.nav-dropdown {
  position: relative;
}

.nav-dropdown::after {
  position: absolute;
  top: 100%;
  left: 0;
  width: 100%;
  height: 12px;
  content: "";
}

.nav-dropdown > .nav-btn {
  gap: 7px;
  border: 0;
  background-color: transparent;
  cursor: pointer;
}

.nav-dropdown > .nav-btn:hover {
  background-color: rgba(255, 255, 255, 0.72);
}

.nav-dropdown > .nav-btn.active {
  background: linear-gradient(135deg, #1c3d70 0%, #0a1931 100%);
}

.dropdown-arrow {
  color: currentColor;
  font-size: 11px;
  transition: transform 0.18s ease;
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 10px);
  left: 50%;
  z-index: 1100;
  display: none;
  min-width: 150px;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 14px 30px rgba(10, 25, 49, 0.16);
  transform: translateX(-50%);
}

.nav-dropdown.open .dropdown-menu {
  display: grid;
  gap: 4px;
}

.nav-dropdown.open .dropdown-arrow {
  transform: rotate(180deg);
}

.dropdown-menu a {
  padding: 10px 12px;
  border-radius: 7px;
  color: #475569;
  font-size: 0.88rem;
  font-weight: 800;
  text-align: center;
  text-decoration: none;
  white-space: nowrap;
}

.dropdown-menu a:hover,
.dropdown-menu a.router-link-active {
  color: #0a1931;
  background: #eef3f9;
}

.user-utility-zone {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-left: auto;
}

.user-name-tag {
  color: #334155;
  font-size: 0.85rem;
  font-weight: 700;
  white-space: nowrap;
}

.suspended-status-notice {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 20px;
  border-bottom: 1px solid #fecaca;
  color: #991b1b;
  background: #fff5f5;
  font-size: 0.84rem;
  line-height: 1.4;
  text-align: center;
  box-sizing: border-box;
}

.suspended-status-notice strong {
  font-weight: 900;
}

.suspended-status-notice span {
  color: #b45353;
}

.btn-header-logout {
  padding: 6px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #94a3b8;
  background-color: transparent;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-header-logout:hover {
  color: #ef4444;
  border-color: #ef4444;
  background-color: rgba(239, 68, 68, 0.04);
}

.page-content-area {
  flex: 1;
  max-width: var(--app-content-width, 1280px);
  width: 100%;
  margin: 0 auto;
  padding: 3rem 2rem;
  box-sizing: border-box;
}

@media (max-width: 1060px) {
  .header-container {
    height: auto;
    min-height: 70px;
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px 1rem;
  }

  .logo-area {
    display: none;
  }

  .horizontal-nav-box {
    position: static;
    order: 2;
    width: 100%;
    overflow: visible;
    transform: none;
    justify-content: flex-start;
  }

  .user-utility-zone {
    margin-left: auto;
  }

  .suspended-status-notice {
    flex-wrap: wrap;
    gap: 2px 6px;
    min-height: 44px;
    padding: 7px 16px;
  }
}
</style>
