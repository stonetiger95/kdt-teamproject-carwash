import AuthLayout from "../layouts/AuthLayout.vue"

export default [

    {
        path: "/",
        component: AuthLayout,

        children: [

            {
                path: "",
                redirect: "/home"
            },

            {
                path: "home",
                name: "home",
                component: () =>
                    import(
                        "@/features/home/views/HomeView.vue"
                    )
            },

            {
                path: "wash-sites/map",
                name: "public-wash-site-map",
                component: () =>
                    import(
                        "@/features/washSite/views/WashSiteMapView.vue"
                    )
            },

            {
                path: "non-member-reservations/new",
                name: "non-member-reservation-create",
                component: () =>
                    import(
                        "@/features/nonMemberReservation/views/NonMemberReservationCreateView.vue"
                    )
            },

            {
                path: "non-member-reservations/lookup",
                name: "non-member-reservation-lookup",
                component: () =>
                    import(
                        "@/features/nonMemberReservation/views/NonMemberReservationLookupView.vue"
                    )
            },

            {
                path: "non-member-reservations/:reservationId",
                name: "non-member-reservation-detail",
                component: () =>
                    import(
                        "@/features/nonMemberReservation/views/NonMemberReservationLookupView.vue"
                    )
            },

            {
                path: "non-member-wash-history",
                name: "non-member-wash-history",
                component: () =>
                    import(
                        "@/features/washHistory/views/NonMemberWashHistoryView.vue"
                    )
            },

            {
                path: "non-member-payment/:washHistoryId",
                name: "non-member-payment-checkout",
                component: () =>
                    import(
                        "@/features/payment/views/NonMemberPaymentCheckoutView.vue"
                    )
            },

            {
                path: "non-member-payments/:washHistoryId",
                name: "non-member-payment-list",
                component: () =>
                    import(
                        "@/features/payment/views/NonMemberPaymentListView.vue"
                    )
            },

            {
                path: "login",
                name: "login",
                component: () =>
                    import(
                        "@/features/auth/views/LoginView.vue"
                    )
            },

            {
                path: "auth/naver/callback",
                name: "naver-login-callback",
                component: () =>
                    import(
                        "@/features/auth/views/NaverCallbackView.vue"
                    )
            },

            {
                path: "auth/naver/signup",
                name: "naver-signup",
                component: () =>
                    import(
                        "@/features/auth/views/NaverSignupView.vue"
                    )
            },

            {
                path: "auth/:provider(google|kakao)/callback",
                name: "social-login-callback",
                component: () =>
                    import(
                        "@/features/auth/views/SocialCallbackView.vue"
                    )
            },

            {
                path: "auth/:provider(google|kakao)/signup",
                name: "social-signup",
                component: () =>
                    import(
                        "@/features/auth/views/SocialSignupView.vue"
                    )
            },

            {
                path: "forgot-password",
                name: "forgot-password",
                component: () =>
                    import(
                        "@/features/auth/views/ForgotPasswordView.vue"
                    )
            },

            {
                path: "reset-password",
                name: "reset-password",
                component: () =>
                    import(
                        "@/features/auth/views/ResetPasswordView.vue"
                    )
            },

            {
                path: "customer-signup",
                name: "customer-signup",
                component: () =>
                    import(
                        "@/features/auth/views/CustomerSignupView.vue"
                    )
            },

            {
                path: "owner-register",
                name: "owner-register",
                redirect: {
                    name: "admin-owner-register"
                }
            }
        ]
    }

]
