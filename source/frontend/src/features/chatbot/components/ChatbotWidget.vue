<template>
    <div class="chatbot-shell">
        <button
            v-if="!open"
            class="chatbot-launcher"
            type="button"
            aria-label="고객 안내 챗봇 열기"
            @click="open = true"
        >
            <strong aria-hidden="true">Chat</strong>
        </button>

        <section
            v-else
            class="chatbot-panel"
            aria-label="Auto Wash 고객 안내 챗봇"
        >
            <header>
                <div>
                    <strong>Auto Wash 챗봇</strong>
                    <small>챗봇 답변 · 검증된 빠른 안내</small>
                </div>
                <button
                    type="button"
                    aria-label="챗봇 닫기"
                    @click="open = false"
                >×</button>
            </header>

            <section v-if="suggestions.length" class="chatbot-suggestions">
                <strong>자주 묻는 질문</strong>
                <div>
                    <button
                        v-for="suggestion in suggestions"
                        :key="suggestion"
                        type="button"
                        :disabled="sending"
                        @click="sendSuggestion(suggestion)"
                    >
                        {{ suggestion }}
                    </button>
                </div>
            </section>

            <div
                ref="messageList"
                class="chatbot-messages"
                aria-live="polite"
            >
                <article
                    v-for="(item, index) in messages"
                    :key="index"
                    :class="[
                        'chatbot-message',
                        item.role,
                        { 'with-map': Boolean(item.washSite) }
                    ]"
                >
                    <span
                        v-if="sourceLabel(item.source)"
                        class="chatbot-message-source"
                    >
                        {{ sourceLabel(item.source) }}
                    </span>
                    <span>{{ item.content }}</span>
                    <button
                        v-if="item.action === LOCATION_PERMISSION_ACTION"
                        class="chatbot-location-button"
                        type="button"
                        :disabled="sending"
                        @click="provideCurrentLocation(item)"
                    >
                        현재 위치 제공
                    </button>
                    <ChatbotWashSiteMap
                        v-if="item.washSite"
                        :site="item.washSite"
                    />
                </article>
                <p v-if="sending" class="chatbot-message assistant pending">
                    답변을 확인하고 있어요…
                </p>
            </div>

            <form @submit.prevent="sendMessage">
                <input
                    v-model="draft"
                    type="text"
                    maxlength="1000"
                    autocomplete="off"
                    placeholder="챗봇에게 질문하세요"
                    :disabled="sending"
                    aria-label="챗봇 질문"
                >
                <button
                    type="submit"
                    :disabled="sending || !draft.trim()"
                >
                    전송
                </button>
            </form>

            <footer>
                개인정보·비밀번호·카드정보는 입력하지 마세요.
            </footer>
        </section>
    </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from "vue"
import * as chatbotApi from "@/features/chatbot/api/chatbotApi"
import ChatbotWashSiteMap from "./ChatbotWashSiteMap.vue"

const LOCATION_PERMISSION_ACTION = "REQUEST_LOCATION"
const open = ref(false)
const draft = ref("")
const sending = ref(false)
const messageList = ref(null)
const cachedLocation = ref(null)
const pinnedSuggestions = ["근처 세차장"]
const fallbackSuggestions = [
    "근처 세차장",
    "예약 시간이 아니어도 입차할 수 있나요?",
    "내 예약은 어디서 확인하나요?",
    "결제 방법이 궁금해요",
    "세차장 운영시간과 가격은 어디서 확인하나요?"
]
const suggestions = ref(buildSuggestions())
const messages = ref([
    {
        role: "assistant",
        source: "INTRO",
        content: "안녕하세요. Auto Wash 이용 방법을 안내해드릴게요."
    }
])

onMounted(loadFaqQuestions)

async function loadFaqQuestions() {
    try {
        const questions = await chatbotApi.getChatbotFaqQuestions()
        const available = Array.isArray(questions)
            ? questions.filter(question => typeof question === "string" && question.trim())
            : []
        suggestions.value = buildSuggestions(available)
    } catch {
        suggestions.value = buildSuggestions()
    }
}

function buildSuggestions(available = []) {
    return [...pinnedSuggestions, ...available, ...fallbackSuggestions]
        .map(question => question.trim())
        .filter((question, index, all) => question && all.indexOf(question) === index)
        .slice(0, 6)
}

async function sendSuggestion(value) {
    if (sending.value) {
        return
    }
    draft.value = value
    await sendMessage()
}

async function sendMessage() {
    const question = draft.value.trim()
    if (!question || sending.value) {
        return
    }

    const history = messages.value
        .slice(1)
        .slice(-8)
        .map(item => ({ role: item.role, content: item.content }))

    messages.value.push({ role: "user", content: question })
    draft.value = ""
    await scrollToLatest()

    if (isNearbyWashSiteQuestion(question) && !cachedLocation.value) {
        messages.value.push({
            role: "assistant",
            source: "WASH_SITE",
            content: "근처 세차장과 거리를 확인하려면 현재 위치가 필요합니다. 아래 버튼을 눌러 위치 정보 제공을 허용해주세요.",
            action: LOCATION_PERMISSION_ACTION,
            question,
            history
        })
        await scrollToLatest()
        return
    }

    await requestChatbotAnswer(question, history, cachedLocation.value)
}

async function provideCurrentLocation(message) {
    if (sending.value) {
        return
    }

    sending.value = true
    message.action = null

    try {
        const location = await getCurrentLocation()
        cachedLocation.value = location
        message.content = "현재 위치를 확인했습니다. 가까운 세차장을 찾고 있어요."
        await requestChatbotAnswer(message.question, message.history, location)
    } catch (error) {
        message.content = locationErrorMessage(error)
    } finally {
        sending.value = false
        await scrollToLatest()
    }
}

async function requestChatbotAnswer(question, history, location = null) {
    sending.value = true
    try {
        const response = await chatbotApi.sendChatbotMessage(question, history, location)
        messages.value.push({
            role: "assistant",
            source: response.source,
            content: response.answer || "답변을 확인하지 못했습니다.",
            washSite: response.washSite || null
        })
    } catch (error) {
        messages.value.push({
            role: "assistant",
            source: "FALLBACK",
            content: error.response?.data?.message || "잠시 후 다시 질문해주세요."
        })
    } finally {
        sending.value = false
        await scrollToLatest()
    }
}

function isNearbyWashSiteQuestion(question) {
    return /(근처|가까운|주변)\s*세차장/.test(question)
}

function getCurrentLocation() {
    if (!window.isSecureContext) {
        return Promise.reject({ code: "INSECURE_CONTEXT" })
    }
    if (!navigator.geolocation) {
        return Promise.reject({ code: "UNSUPPORTED" })
    }

    return new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(
            position => resolve({
                latitude: position.coords.latitude,
                longitude: position.coords.longitude
            }),
            reject,
            {
                enableHighAccuracy: false,
                timeout: 7000,
                maximumAge: 300000
            }
        )
    })
}

function locationErrorMessage(error) {
    if (error?.code === 1) {
        return "위치 권한이 거부되었습니다. 주소창 왼쪽의 사이트 설정에서 위치 권한을 허용한 뒤 다시 '근처 세차장'을 입력해주세요."
    }
    if (error?.code === 3) {
        return "현재 위치 확인 시간이 초과되었습니다. 잠시 후 다시 '근처 세차장'을 입력해주세요."
    }
    if (error?.code === "INSECURE_CONTEXT") {
        return "위치 기능은 HTTPS 주소에서만 사용할 수 있습니다. HTTPS로 접속한 뒤 다시 시도해주세요."
    }
    return "현재 위치를 확인할 수 없습니다. 브라우저의 위치 서비스를 켠 뒤 다시 시도해주세요."
}

function sourceLabel(source) {
    return {
        TEMPLATE: "빠른 안내",
        GPT: "챗봇 답변",
        TOOL: "내 정보",
        WASH_SITE: "세차장 안내",
        FALLBACK: "기본 안내"
    }[source] || ""
}

async function scrollToLatest() {
    await nextTick()
    if (messageList.value) {
        messageList.value.scrollTop = messageList.value.scrollHeight
    }
}
</script>

<style scoped>
.chatbot-shell {
    position: fixed;
    right: 24px;
    bottom: 24px;
    z-index: 1200;
    font-family: Inter, Pretendard, "Noto Sans KR", sans-serif;
}

.chatbot-launcher {
    display: grid;
    place-items: center;
    width: 58px;
    height: 58px;
    padding: 0;
    border: 0;
    border-radius: 999px;
    color: #fff;
    background: #0b2f61;
    box-shadow: 0 12px 30px rgba(15, 43, 82, .28);
    cursor: pointer;
}

.chatbot-launcher strong {
    font-size: 15px;
    font-weight: 900;
}

.chatbot-panel {
    display: grid;
    grid-template-rows: auto auto minmax(190px, 1fr) auto auto;
    width: min(370px, calc(100vw - 32px));
    height: min(590px, calc(100vh - 48px));
    overflow: hidden;
    border: 1px solid #d7e2f1;
    border-radius: 18px;
    background: #fff;
    box-shadow: 0 18px 55px rgba(15, 43, 82, .26);
}

.chatbot-panel > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 17px 18px;
    color: #fff;
    background: linear-gradient(135deg, #082857, #155da8);
}

.chatbot-panel header div {
    display: grid;
    gap: 4px;
}

.chatbot-panel header strong {
    font-size: 16px;
}

.chatbot-panel header small {
    color: #dbeafe;
    font-size: 11px;
}

.chatbot-panel header button {
    border: 0;
    color: #fff;
    background: transparent;
    font-size: 26px;
    cursor: pointer;
}

.chatbot-messages {
    display: flex;
    flex-direction: column;
    gap: 10px;
    overflow-y: auto;
    padding: 18px;
    background: #f4f7fb;
}

.chatbot-message {
    max-width: 85%;
    margin: 0;
    padding: 11px 13px;
    border-radius: 14px;
    color: #21324a;
    background: #fff;
    box-shadow: 0 2px 8px rgba(15, 43, 82, .06);
    font-size: 13px;
    line-height: 1.55;
    white-space: pre-wrap;
}

.chatbot-message.user {
    align-self: flex-end;
    color: #fff;
    background: #155da8;
    border-bottom-right-radius: 4px;
}

.chatbot-message.assistant {
    align-self: flex-start;
    border-bottom-left-radius: 4px;
}

.chatbot-message.with-map {
    width: 92%;
    max-width: 92%;
}

.chatbot-message.pending {
    color: #667085;
}

.chatbot-message-source {
    display: block;
    margin-bottom: 4px;
    color: #155da8;
    font-size: 9px;
    font-weight: 900;
    letter-spacing: .04em;
}

.chatbot-location-button {
    display: block;
    width: 100%;
    margin-top: 10px;
    padding: 9px 12px;
    border: 0;
    border-radius: 9px;
    color: #fff;
    background: #155da8;
    font: inherit;
    font-weight: 800;
    cursor: pointer;
}

.chatbot-location-button:disabled {
    opacity: .55;
    cursor: default;
}

.chatbot-suggestions {
    padding: 10px 12px;
    border-bottom: 1px solid #e5ebf3;
    background: #fff;
}

.chatbot-suggestions > strong {
    display: block;
    margin-bottom: 7px;
    color: #52657f;
    font-size: 10px;
}

.chatbot-suggestions > div {
    display: flex;
    flex-wrap: wrap;
    gap: 7px;
    padding-bottom: 2px;
}

.chatbot-suggestions button {
    flex: 1 1 calc(50% - 7px);
    min-width: 0;
    max-width: 100%;
    padding: 7px 10px;
    border: 1px solid #b9cee8;
    border-radius: 999px;
    color: #164d88;
    background: #f4f8fd;
    font-size: 11px;
    line-height: 1.35;
    white-space: normal;
    cursor: pointer;
}

.chatbot-suggestions button:disabled {
    opacity: .55;
    cursor: default;
}

.chatbot-panel form {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 8px;
    padding: 12px;
    border-top: 1px solid #e5ebf3;
}

.chatbot-panel form input {
    min-width: 0;
    padding: 11px 12px;
    border: 1px solid #c8d5e5;
    border-radius: 10px;
    outline: none;
}

.chatbot-panel form input:focus {
    border-color: #2f78c7;
    box-shadow: 0 0 0 3px rgba(47, 120, 199, .13);
}

.chatbot-panel form button {
    padding: 0 14px;
    border: 0;
    border-radius: 10px;
    color: #fff;
    background: #0b2f61;
    font-weight: 700;
    cursor: pointer;
}

.chatbot-panel form button:disabled {
    opacity: .5;
    cursor: default;
}

.chatbot-panel footer {
    padding: 0 14px 12px;
    color: #7b8797;
    font-size: 10px;
    text-align: center;
}

@media (max-width: 560px) {
    .chatbot-shell {
        right: 12px;
        bottom: 12px;
    }

    .chatbot-panel {
        width: calc(100vw - 24px);
        height: min(620px, calc(100vh - 24px));
    }
}
</style>
