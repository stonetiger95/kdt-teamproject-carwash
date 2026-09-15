import axiosClient from "@/shared/api/axiosClient"

export function getChatbotFaqQuestions() {
    return axiosClient.get("/api/public/chatbot/faqs")
}

export function sendChatbotMessage(message, history = [], location = null) {
    return axiosClient.post(
        "/api/public/chatbot/messages",
        {
            message,
            history,
            latitude: location?.latitude ?? null,
            longitude: location?.longitude ?? null
        }
    )
}
