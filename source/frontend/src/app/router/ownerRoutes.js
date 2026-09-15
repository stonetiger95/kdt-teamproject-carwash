import OwnerLayout from "@/app/layouts/OwnerLayout.vue"
import { ROLE } from "@/shared/auth/roles"

export default [
    {
        path: "/owner",
        component: OwnerLayout,
            meta: {

            requiresAuth: true,

            role: ROLE.OWNER

        },

        children: [

            {
                path: "",
                redirect: {
                    name: "owner-dashboard"
                }
            },
            
            {
                path: "dashboard",
                name: "owner-dashboard",
                component: () =>
                    import("@/features/owner/views/OwnerDashboardView.vue")
            },

            {
                path: "inquiries",
                name: "owner-inquiries",
                component: () =>
                    import("@/features/board/views/InquiryListView.vue")
            },

            {
                path: "inquiries/create",
                name: "owner-inquiry-create",
                component: () =>
                    import("@/features/board/views/InquiryFormView.vue")
            },

            {
                path: "inquiries/detail",
                name: "owner-inquiry-detail-page",
                component: () =>
                    import("@/features/board/views/InquiryDetailView.vue")
            },

            {
                path: "inquiries/:inquiryId",
                name: "owner-inquiry-detail",
                component: () =>
                    import("@/features/board/views/InquiryDetailView.vue")
            },

            {
                path: "inquiries/:inquiryId/edit",
                name: "owner-inquiry-edit",
                component: () =>
                    import("@/features/board/views/InquiryFormView.vue")
            },

            {
                path: "reservations",
                name: "owner-reservations",
                component: () =>
                    import("@/features/reservation/views/OwnerReservationListView.vue"),
                props: {
                    viewerRole: "OWNER"
                }
            },
            {
                path: "payments",
                name: "owner-payments",
                component: () =>
                    import("@/features/payment/views/PaymentListView.vue"),
                props: {
                    viewerRole: "OWNER",
                    checkoutRouteName: ""
                }
            },
            {
                path: "wash-history",
                name: "owner-wash-history",
                component: () =>
                    import("@/features/washHistory/views/washHistoryListView.vue"),
                props: {
                    viewerRole: "OWNER",
                    detailRouteName: "owner-wash-history-detail"
                }
            },
            {
                path: "wash-history/:washHistoryId",
                name: "owner-wash-history-detail",
                component: () =>
                    import("@/features/washHistory/views/washHistoryDetailView.vue"),
                props: {
                    viewerRole: "OWNER",
                    listRouteName: "owner-wash-history"
                }
            },
            {
                path: "settlement",
                name: "owner-settlement",
                component: () =>
                    import("@/features/settlement/views/SettlementDashboardView.vue"),
                props: {
                    viewerRole: "OWNER"
                }
            },
            {
                path: "wash-sites",
                name: "owner-wash-sites",
                component: () =>
                    import("@/features/washSite/views/WashSiteManageView.vue"),
                props: {
                    viewerRole: "OWNER"
                }
            },
            {
                path: "business-hours/:washSiteId?",
                name: "owner-business-hours",
                component: () =>
                    import("@/features/businessHour/views/BusinessHourManageView.vue")
            },
            {
                path: "benefits",
                name: "owner-benefits",
                component: () =>
                    import("@/features/benefit/views/BenefitManageView.vue"),
                props: {
                    viewerRole: "OWNER"
                }
            },
            {
                path: "mypage",
                name: "owner-mypage",
                component: () =>
                    import(
                        "@/features/owner/views/OwnerMyPageView.vue"
                    )
            },

            {
                path: "mypage/password",
                name: "OwnerPasswordChange",
                component: () =>
                    import(
                        "@/features/owner/views/OwnerPasswordChangeView.vue"
                    )
            },

            {
                path: "devices",
                name: "owner-device-list",
                component: () =>
                    import("@/features/device/views/DeviceListView.vue")
            },

            {
                path:"devices/create",
                name:"owner-device-create",
                component:()=>
                    import(
                        "@/features/device/views/DeviceCreateView.vue"
                    )
            },

            {
                path: "devices/:deviceId",
                name: "owner-device-edit",
                component: () =>
                    import("@/features/device/views/DeviceEditView.vue")
            }
        ]
    }
]
