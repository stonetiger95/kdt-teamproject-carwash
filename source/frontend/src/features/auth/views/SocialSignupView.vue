<template>
    <NaverSignupView :provider="provider" />
</template>

<script setup>
import { computed } from "vue"
import { useRoute } from "vue-router"
import NaverSignupView from "./NaverSignupView.vue"

const route = useRoute()
const provider = computed(() => String(route.params.provider || ""))
</script>
