export const adminMenu = [
  {
    key: 'dashboard',
    items: [
      { label: '대시보드', icon: 'pi pi-objects-column', to: '/admin/dashboard' },
    ],
  },
  {
    key: 'operations',
    label: '운영 관리',
    icon: 'pi pi-briefcase',
    items: [
      { label: '예약 관리', icon: 'pi pi-calendar', to: '/admin/reservations' },
      { label: '세차 내역', icon: 'pi pi-history', to: '/admin/wash-history' },
      { label: '번호판 인식 로그', icon: 'pi pi-camera', to: '/admin/plate-detection-logs' },
    ],
  },
  {
    key: 'equipment',
    label: '장비 관제',
    icon: 'pi pi-desktop',
    items: [
      {
        label: '예지보전',
        icon: 'pi pi-gauge',
        to: '/admin/predictive-maintenance',
        external: true,
        popup: { name: 'adminPredictiveMaintenance', width: 1500, height: 950 },
      },
      {
        label: '예지보전 데이터 로그',
        icon: 'pi pi-database',
        to: '/admin/predictive-maintenance/logs',
      },
    ],
  },
  {
    key: 'customers',
    label: '고객 관리',
    icon: 'pi pi-users',
    items: [
      { label: '회원 관리', icon: 'pi pi-user', to: '/admin/member-manage' },
      { label: '차량 관리', icon: 'pi pi-car', to: '/admin/vehicles' },
    ],
  },
  {
    key: 'business',
    label: '사업장 관리',
    icon: 'pi pi-building',
    items: [
      { label: '사업주 등록', icon: 'pi pi-user-plus', to: '/admin/owner-register' },
      { label: '세차장 관리', icon: 'pi pi-building-columns', to: '/admin/wash-sites' },
    ],
  },
  {
    key: 'billing',
    label: '결제·정산',
    icon: 'pi pi-credit-card',
    items: [
      { label: '결제 관리', icon: 'pi pi-credit-card', to: '/admin/payments' },
      { label: '정산 분석', icon: 'pi pi-chart-line', to: '/admin/settlement' },
      { label: '혜택 관리', icon: 'pi pi-gift', to: '/admin/benefits' },
    ],
  },
  {
    key: 'support',
    label: '고객 지원',
    icon: 'pi pi-comments',
    items: [
      { label: '문의 관리', icon: 'pi pi-comment', to: '/admin/inquiries' },
      { label: '챗봇 FAQ 관리', icon: 'pi pi-sparkles', to: '/admin/chatbot' },
    ],
  },
]
