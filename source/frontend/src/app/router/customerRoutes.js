import CustomerLayout from "@/app/layouts/CustomerLayout.vue"
import { ROLE } from "@/shared/auth/roles"



export default [
    {
        path: "/customer",
        component: CustomerLayout,
            meta: {

            requiresAuth: true,

            role: ROLE.CUSTOMER
        },

        children: [

            {
                path: "",
                redirect: {
                    name: "customer-dashboard"
                }
            },

            {
                path: "dashboard",
                name: "customer-dashboard",
                component: () =>
                    import("@/features/customer/views/CustomerDashboardView.vue"),
                beforeEnter: to => {
                    const billingCardResult = String(to.query.billingCard || "")

                    if (["success", "fail"].includes(billingCardResult)) {
                        return {
                            name: "customer-billing-cards",
                            query: { billingCard: billingCardResult }
                        }
                    }
                }
            },

            {
                path: "billing-cards",
                name: "customer-billing-cards",
                component: () =>
                    import("@/features/customer/views/BillingCardManageView.vue")
            },

            {
                path: "wash-sites/map",
                name: "customer-wash-site-map",
                component: () =>
                    import("@/features/washSite/views/WashSiteMapView.vue")
            },
            {
                path: "benefits",
                name: "customer-benefits",
                component: () =>
                    import("@/features/benefit/views/CustomerBenefitView.vue")
            },

            {
                path: "inquiries",
                name: "customer-inquiries",
                component: () =>
                    import("@/features/board/views/InquiryListView.vue")
            },

            {
                path: "inquiries/create",
                name: "customer-inquiry-create",
                component: () =>
                    import("@/features/board/views/InquiryFormView.vue")
            },

            {
                path: "inquiries/detail",
                name: "customer-inquiry-detail-page",
                component: () =>
                    import("@/features/board/views/InquiryDetailView.vue")
            },

            {
                path: "inquiries/:inquiryId",
                name: "customer-inquiry-detail",
                component: () =>
                    import("@/features/board/views/InquiryDetailView.vue")
            },

            {
                path: "inquiries/:inquiryId/edit",
                name: "customer-inquiry-edit",
                component: () =>
                    import("@/features/board/views/InquiryFormView.vue")
            },

            {
                path: "payments",
                name: "customer-payments",
                component: () =>
                    import("@/features/payment/views/PaymentListView.vue"),
                props: {
                    viewerRole: "CUSTOMER",
                    checkoutRouteName: "customer-payment-checkout"
                }
            },
            {
                path: "payments/checkout",
                name: "customer-payment-checkout",
                component: () =>
                    import("@/features/payment/views/PaymentCheckoutView.vue")
            },
            {
                path: "payments/success",
                name: "customer-payment-success",
                component: () =>
                    import("@/features/payment/views/PaymentSuccessView.vue")
            },
            {
                path: "payments/fail",
                name: "customer-payment-fail",
                component: () =>
                    import("@/features/payment/views/PaymentFailView.vue")
            },
            {
                path: "reservations/new",
                name: "customer-reservation-create",
                component: () =>
                    import("@/features/reservation/views/ReservationCreateView.vue")
            },
            {
                path: "reservations",
                name: "customer-reservations",
                component: () =>
                    import("@/features/reservation/views/ReservationListView.vue")
            },
            {
                path: "vehicles",
                name: "customer-vehicles",
                component: () =>
                    import("@/features/vehicle/views/CustomerVehicleList.vue")
            },
            {
                path: "vehicles/registration",
                name: "customer-vehicles-registration",
                component: () =>
                    import("@/features/vehicle/views/CustomerVehicleRegister.vue")
            },
            {
                path: "wash-history",
                name: "customer-wash-history",
                component: () =>
                    import("@/features/washHistory/views/washHistoryListView.vue"),
                props: {
                    viewerRole: "CUSTOMER",
                    detailRouteName: "customer-wash-history-detail"
                }
            },
            {
                path: "wash-history/:washHistoryId",
                name: "customer-wash-history-detail",
                component: () =>
                    import("@/features/washHistory/views/washHistoryDetailView.vue"),
                props: {
                    viewerRole: "CUSTOMER",
                    listRouteName: "customer-wash-history"
                }
            },

            {
                path: "mypage",
                name: "customer-mypage",
                component: () =>
                    import("@/features/customer/views/CustomerMyPageView.vue")
            },

            {
                path: "mypage/password",
                name: "CustomerPasswordChange",
                component: () =>
                    import(
                        "@/features/customer/views/CustomerPasswordChangeView.vue"
                    )
            },
        ]
    }
]
