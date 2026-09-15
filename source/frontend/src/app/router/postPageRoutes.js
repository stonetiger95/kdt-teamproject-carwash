export default [
    {
        path: "/postpage",
        name: "post-page",
        component: () =>
            import("@/features/postPage/views/PostPageView.vue")
    },
    {
        path: "/admin/postpage",
        redirect: "/postpage"
    }
]
