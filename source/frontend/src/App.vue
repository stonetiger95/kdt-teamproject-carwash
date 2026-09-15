<script setup>
import { computed } from "vue"
import { useRoute } from "vue-router"
import ChatbotWidget from "@/features/chatbot/components/ChatbotWidget.vue"

const route = useRoute()
const showCustomerChatbot = computed(() =>
    !route.path.startsWith("/admin")
    && !route.path.startsWith("/owner")
    && !route.path.startsWith("/postpage")
)
</script>

<template>
  <RouterView />
  <ChatbotWidget v-if="showCustomerChatbot" />
</template>
