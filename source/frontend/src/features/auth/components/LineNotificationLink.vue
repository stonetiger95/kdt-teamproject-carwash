<template>
    <div class="line-notification-link">
        <div class="line-actions">
            <span :class="['line-status', { connected: linked }]">
                {{ statusText }}
            </span>

            <button
                type="button"
                class="line-link-button"
                :class="{ unlink: linked }"
                :disabled="loading || !available"
                @click="linked ? disconnect() : connect()"
            >
                {{ buttonText }}
            </button>
        </div>

        <div v-if="linkCommand && !linked" class="line-link-guide">
            <p>
                아래 연결 문구를 복사한 뒤 LINE 공식계정 1:1 채팅으로 보내세요.
            </p>
            <div class="line-command-row">
                <code>{{ linkCommand }}</code>
                <button type="button" class="line-copy-button" @click="copyCommand">
                    {{ copied ? "복사됨" : "복사" }}
                </button>
            </div>
            <a
                class="line-chat-link"
                :href="officialAccountUrl"
                target="_blank"
                rel="noopener noreferrer"
            >
                LINE 채팅 열기
            </a>
            <small>{{ expiresInMinutes }}분 안에 보내야 합니다.</small>
        </div>

        <span v-if="errorMessage" class="line-error">
            {{ errorMessage }}
        </span>
    </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import * as authApi from "@/features/auth/api/authApi"

defineProps({
    returnRoute: {
        type: String,
        required: false,
        default: ""
    }
})

const linked = ref(false)
const available = ref(false)
const loading = ref(true)
const errorMessage = ref("")
const linkCommand = ref("")
const officialAccountUrl = ref("")
const expiresInSeconds = ref(0)
const copied = ref(false)
let pollTimer = null

const statusText = computed(() => {
    if (loading.value) return "연결 상태 확인 중"
    if (!available.value) return "LINE 알림 준비 중"
    return linked.value ? "LINE 알림 연결됨" : "LINE 알림 연결 안 됨"
})

const buttonText = computed(() => {
    if (loading.value) return "처리 중..."
    if (!available.value) return "관리자 설정 필요"
    return linked.value ? "연결 해제" : "LINE 알림 연결"
})

const expiresInMinutes = computed(() =>
    Math.max(1, Math.ceil(expiresInSeconds.value / 60))
)

function apiMessage(error, fallback) {
    return error.response?.data?.message || fallback
}

function stopPolling() {
    if (pollTimer !== null) {
        window.clearInterval(pollTimer)
        pollTimer = null
    }
}

function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(async () => {
        await loadStatus(true)
        if (linked.value) {
            linkCommand.value = ""
            stopPolling()
        }
    }, 3000)
}

async function loadStatus(silent = false) {
    if (!silent) loading.value = true
    errorMessage.value = ""
    try {
        const response = await authApi.getLineNotificationStatus()
        available.value = Boolean(response?.available)
        linked.value = Boolean(response?.linked)
    } catch (error) {
        if (!silent) {
            errorMessage.value = apiMessage(
                error,
                "LINE 연결 상태를 확인하지 못했습니다."
            )
        }
    } finally {
        if (!silent) loading.value = false
    }
}

async function connect() {
    loading.value = true
    errorMessage.value = ""
    copied.value = false
    try {
        const response = await authApi.getLineNotificationAuthorization()
        linkCommand.value = response.command
        officialAccountUrl.value = response.officialAccountUrl
        expiresInSeconds.value = Number(response.expiresInSeconds || 600)
        startPolling()
    } catch (error) {
        errorMessage.value = apiMessage(
            error,
            "LINE 연결을 시작하지 못했습니다."
        )
    } finally {
        loading.value = false
    }
}

async function copyCommand() {
    try {
        await navigator.clipboard.writeText(linkCommand.value)
        copied.value = true
    } catch {
        errorMessage.value = "연결 문구를 선택하여 직접 복사해 주세요."
    }
}

async function disconnect() {
    loading.value = true
    errorMessage.value = ""
    try {
        await authApi.unlinkLineNotification()
        linked.value = false
        linkCommand.value = ""
        stopPolling()
    } catch (error) {
        errorMessage.value = apiMessage(
            error,
            "LINE 연결을 해제하지 못했습니다."
        )
    } finally {
        loading.value = false
    }
}

onMounted(loadStatus)
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.line-notification-link {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.line-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
}

.line-status {
    color: #64748b;
    font-size: 0.9rem;
}

.line-status.connected {
    color: #087f5b;
    font-weight: 700;
}

.line-link-button,
.line-copy-button,
.line-chat-link {
    border: 1px solid #06c755;
    border-radius: 8px;
    background: #06c755;
    color: #fff;
    cursor: pointer;
    font: inherit;
    font-weight: 700;
    text-decoration: none;
}

.line-link-button {
    width: auto !important;
    margin: 0 !important;
    padding: 9px 14px !important;
}

.line-link-button.unlink {
    border-color: #94a3b8 !important;
    background: #fff !important;
    color: #475569 !important;
}

.line-link-button:disabled {
    cursor: wait;
    opacity: 0.65;
}

.line-link-guide {
    padding: 14px;
    border: 1px solid #bbf7d0;
    border-radius: 10px;
    background: #f0fdf4;
    color: #334155;
}

.line-link-guide p {
    margin: 0 0 10px;
}

.line-command-row {
    display: flex;
    gap: 8px;
}

.line-command-row code {
    flex: 1;
    overflow-wrap: anywhere;
    padding: 9px 10px;
    border-radius: 7px;
    background: #fff;
    color: #0f172a;
}

.line-copy-button,
.line-chat-link {
    display: inline-block;
    padding: 8px 12px;
}

.line-chat-link {
    margin-top: 10px;
}

.line-link-guide small {
    display: block;
    margin-top: 8px;
    color: #64748b;
}

.line-error {
    color: #b42318;
    font-size: 0.85rem;
    text-align: right;
}

@media (max-width: 640px) {
    .line-actions {
        align-items: stretch;
        flex-direction: column;
    }

    .line-command-row {
        flex-direction: column;
    }

    .line-error {
        text-align: left;
    }
}
</style>
