import AdminLayout from '@/admin/layout/AdminLayout.vue'
import DashboardPreview from '@/admin/views/DashboardPreview.vue'
import { ROLE } from '@/shared/auth/roles'

export default [
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/admin/features/auth/views/AdminLoginView.vue'),
    meta: { public: true, adminSakai: true, title: '관리자 로그인' },
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, role: ROLE.ADMIN, adminSakai: true },
    children: [
      { path: '', redirect: { name: 'admin-dashboard' } },
      { path: 'dashboard', name: 'admin-dashboard', component: DashboardPreview, meta: { title: '대시보드', breadcrumb: ['대시보드'] } },
      { path: 'predictive-maintenance', name: 'admin-predictive-maintenance', component: () => import('@/admin/features/predictiveMaintenance/views/PredictiveMaintenanceView.vue'), meta: { title: '예지보전', breadcrumb: ['장비 관제', '예지보전'] } },
      { path: 'predictive-maintenance/logs', name: 'admin-predictive-maintenance-logs', component: () => import('@/admin/features/predictiveMaintenance/views/PredictiveMaintenanceLogView.vue'), meta: { title: '예지보전 데이터 로그', breadcrumb: ['장비 관제', '예지보전', '데이터 로그'] } },
      { path: 'inquiries', name: 'admin-inquiries', component: () => import('@/admin/features/inquiry/views/InquiryManagementView.vue'), meta: { title: '문의 관리', breadcrumb: ['고객 지원', '문의 관리'] } },
      { path: 'chatbot', name: 'admin-chatbot', component: () => import('@/admin/features/chatbot/views/ChatbotFaqManagementView.vue'), meta: { title: '챗봇 FAQ 관리', breadcrumb: ['고객 지원', '챗봇 FAQ 관리'] } },
      { path: 'inquiries/:inquiryId', name: 'admin-inquiry-detail', component: () => import('@/admin/features/inquiry/views/InquiryManagementView.vue'), meta: { title: '문의 상세', breadcrumb: ['고객 지원', '문의 관리', '상세'] } },
      { path: 'inquiries/detail', name: 'admin-inquiry-detail-page', redirect: (to) => ({ name: 'admin-inquiries', query: to.query }) },
      { path: 'plate-detection-logs', name: 'admin-plate-detection-logs', component: () => import('@/admin/features/plateDetectionLog/views/PlateDetectionLogView.vue'), meta: { title: '번호판 인식 로그', breadcrumb: ['운영 관리', '번호판 인식 로그'] } },
      { path: 'wash-sites', name: 'admin-wash-sites', component: () => import('@/admin/features/washSite/views/WashSiteManagementView.vue'), meta: { title: '세차장 관리', breadcrumb: ['사업장 관리', '세차장 관리'] } },
      { path: 'owners/:ownerId/wash-sites', name: 'admin-owner-wash-sites', redirect: (to) => ({ name: 'admin-wash-sites', query: { ownerId: to.params.ownerId } }) },
      { path: 'owner-register', name: 'admin-owner-register', component: () => import('@/admin/features/ownerRegistration/views/OwnerRegistrationView.vue'), meta: { title: '사업주 등록', breadcrumb: ['사업장 관리', '사업주 등록'] } },
      { path: 'member-manage', name: 'admin-member-manage', component: () => import('@/admin/features/memberVehicle/views/MemberVehicleManagementView.vue'), meta: { title: '회원 관리', breadcrumb: ['고객 관리', '회원 관리'] } },
      { path: 'vehicles', name: 'admin-vehicles', component: () => import('@/admin/features/memberVehicle/views/MemberVehicleManagementView.vue'), meta: { title: '차량 관리', breadcrumb: ['고객 관리', '차량·블랙리스트'] } },
      { path: 'vehicles/:plateNumber', name: 'admin-vehicle-detail', component: () => import('@/admin/features/memberVehicle/views/MemberVehicleManagementView.vue'), meta: { title: '차량 상세', breadcrumb: ['고객 관리', '차량·블랙리스트', '상세'] } },
      { path: 'payments', name: 'admin-payments', component: () => import('@/admin/features/billing/views/BillingManagementView.vue'), meta: { title: '결제 관리', breadcrumb: ['결제·정산', '결제 관리'] } },
      { path: 'settlement', name: 'admin-settlement', component: () => import('@/admin/features/billing/views/BillingManagementView.vue'), meta: { title: '정산 분석', breadcrumb: ['결제·정산', '정산 분석'] } },
      { path: 'benefits', name: 'admin-benefits', component: () => import('@/admin/features/benefit/views/BenefitManagementView.vue'), meta: { title: '혜택 관리', breadcrumb: ['결제·정산', '혜택 관리'] } },
      { path: 'reservations', name: 'admin-reservations', component: () => import('@/admin/features/operations/views/OperationsHistoryView.vue'), meta: { title: '예약 관리', breadcrumb: ['운영 관리', '예약 관리'] } },
      { path: 'wash-history', name: 'admin-wash-history', component: () => import('@/admin/features/operations/views/OperationsHistoryView.vue'), meta: { title: '세차 내역', breadcrumb: ['운영 관리', '세차 내역'] } },
      { path: 'wash-history/:washHistoryId', name: 'admin-wash-history-detail', component: () => import('@/admin/features/operations/views/OperationsHistoryView.vue'), meta: { title: '세차 상세', breadcrumb: ['운영 관리', '세차 내역', '상세'] } },
    ],
  },
]
