<template>
    <main class="post-page">
        <header class="page-header">
            <div>
                <p class="eyebrow">PUBLIC DEVICE TEST CONSOLE</p>
                <h1>장비 이벤트 테스트</h1>
            </div>

            <a
                class="home-link"
                href="/home"
            >
                프로젝트 홈
            </a>
        </header>

        <section
            class="operation-strip"
            aria-label="장비 이벤트 선택"
        >
            <button
                v-for="(operation, index) in operationOptions"
                :key="operation.key"
                :class="['operation-button', { active: selectedOperation === operation.key }]"
                type="button"
                @click="selectedOperation = operation.key"
            >
                <span>{{ String(index + 1).padStart(2, "0") }}</span>
                <strong>{{ operation.label }}</strong>
                <small>{{ operation.endpoint }}</small>
            </button>
        </section>

        <div class="workspace-grid">
            <section class="request-panel">
                <div class="panel-heading">
                    <div>
                        <span class="panel-kicker">REQUEST</span>
                        <h2>{{ currentOperation.label }}</h2>
                    </div>
                    <span class="method-badge">POST</span>
                </div>

                <form @submit.prevent="submitEvent">
                    <label class="field-label">
                        <span>장비 ID</span>
                        <input
                            v-model="currentDeviceId"
                            min="1"
                            inputmode="numeric"
                            type="number"
                            placeholder="장비 ID 입력"
                            required
                        >
                        <small>{{ currentOperation.deviceHint }}</small>
                    </label>

                    <label
                        :class="['upload-zone', { dragging: isDragging, filled: imageFile }]"
                        @dragenter.prevent="isDragging = true"
                        @dragover.prevent="isDragging = true"
                        @dragleave.prevent="isDragging = false"
                        @drop.prevent="handleDrop"
                    >
                        <input
                            ref="fileInput"
                            accept="image/jpeg,image/png"
                            type="file"
                            @change="handleFileChange"
                        >

                        <img
                            v-if="previewUrl"
                            :src="previewUrl"
                            alt="전송할 차량 사진 미리보기"
                        >

                        <div v-else>
                            <strong>차량 사진 선택</strong>
                            <span>JPEG 또는 PNG, 최대 10MB</span>
                        </div>
                    </label>

                    <div
                        v-if="imageFile"
                        class="file-meta"
                    >
                        <div>
                            <strong>{{ imageFile.name }}</strong>
                            <span>{{ formatFileSize(imageFile.size) }}</span>
                        </div>
                        <button
                            class="text-button"
                            type="button"
                            @click="clearImage"
                        >
                            제거
                        </button>
                    </div>

                    <p
                        v-if="validationMessage"
                        class="validation-message"
                    >
                        {{ validationMessage }}
                    </p>

                    <div class="request-meta">
                        <span>대상</span>
                        <code>{{ backendUrl }}{{ currentOperation.endpoint }}</code>
                    </div>

                    <button
                        class="submit-button"
                        type="submit"
                        :disabled="loading || !canSubmit"
                    >
                        {{ loading ? "요청 처리 중" : `${currentOperation.shortLabel} 요청 보내기` }}
                    </button>
                </form>
            </section>

            <section class="response-panel">
                <div class="panel-heading">
                    <div>
                        <span class="panel-kicker">RESPONSE</span>
                        <h2>처리 결과</h2>
                    </div>
                    <span
                        v-if="lastResult"
                        :class="['response-status', responseSucceeded ? 'success' : 'failure']"
                    >
                        {{ lastResult.status || "ERR" }}
                    </span>
                </div>

                <div
                    v-if="lastResult"
                    class="response-content"
                >
                    <div class="response-summary">
                        <div>
                            <span>이벤트</span>
                            <strong>{{ lastResult.operationLabel }}</strong>
                        </div>
                        <div>
                            <span>처리시간</span>
                            <strong>{{ lastResult.elapsedMs }} ms</strong>
                        </div>
                        <div>
                            <span>요청시각</span>
                            <strong>{{ lastResult.requestedAt }}</strong>
                        </div>
                    </div>

                    <div
                        v-if="responseOutcome"
                        :class="['reservation-outcome', responseOutcome.tone]"
                    >
                        <div>
                            <span>{{ responseOutcome.label }}</span>
                            <strong>{{ responseOutcome.title }}</strong>
                        </div>
                        <p>{{ responseOutcome.detail }}</p>
                    </div>

                    <dl
                        v-if="responseHighlights.length"
                        class="highlight-list"
                    >
                        <template
                            v-for="item in responseHighlights"
                            :key="item.key"
                        >
                            <dt>{{ item.label }}</dt>
                            <dd>{{ formatResponseValue(item.value) }}</dd>
                        </template>
                    </dl>

                    <div class="json-heading">
                        <span>응답 본문</span>
                        <button
                            class="text-button"
                            type="button"
                            @click="copyResponse"
                        >
                            {{ copied ? "복사됨" : "JSON 복사" }}
                        </button>
                    </div>
                    <pre>{{ formattedResponse }}</pre>
                </div>

                <div
                    v-else
                    class="response-empty"
                >
                    <strong>아직 요청 기록이 없습니다.</strong>
                    <span>이벤트, 장비 ID와 차량 사진을 선택해 요청을 보내세요.</span>
                </div>
            </section>
        </div>

        <section class="wash-complete-console">
            <div class="wash-complete-heading">
                <span class="panel-kicker">DEMO COMPLETION</span>
                <h2>세차·결제 완료 처리</h2>
                <p>
                    세차·결제는 선택한 내역에 적용됩니다. 테스트 상태 처리는 열려 있는 모든 입차·세차 상태를 한 번에 종료합니다.
                </p>
            </div>

            <label class="compact-field">
                <span>세차내역 ID</span>
                <input
                    v-model="monitor.washHistoryId"
                    min="1"
                    inputmode="numeric"
                    type="number"
                    placeholder="세차 시 자동 입력"
                >
            </label>

            <div class="completion-action-buttons">
                <button
                    class="action-button cleanup-button"
                    type="button"
                    :disabled="actionLoading"
                    @click="submitVehicleCleanup"
                >
                    테스트 상태 처리
                </button>
                <button
                    class="action-button wash-complete-button"
                    type="button"
                    :disabled="actionLoading"
                    @click="submitWashComplete"
                >
                    세차 완료
                </button>
                <button
                    class="action-button payment-complete-button"
                    type="button"
                    :disabled="actionLoading"
                    @click="submitWalkInPaymentComplete"
                >
                    결제 완료
                </button>
            </div>

            <p
                v-if="actionMessage"
                :class="['completion-action-message', actionMessage.ok ? 'success' : 'failure']"
            >
                {{ actionMessage.text }}
            </p>
        </section>

        <section class="payment-console">
            <div class="history-heading">
                <div>
                    <span class="panel-kicker">LIVE PAYMENT &amp; WALLET</span>
                    <h2>결제·지갑 실시간 테스트</h2>
                </div>

                <label class="polling-toggle">
                    <input
                        v-model="livePolling"
                        type="checkbox"
                    >
                    <span>2초 자동 갱신</span>
                </label>
            </div>

            <div class="monitor-controls">
                <label class="compact-field">
                    <span>고객 ID</span>
                    <input
                        v-model="monitor.customerId"
                        min="1"
                        inputmode="numeric"
                        type="number"
                        placeholder="예: 1"
                    >
                </label>

                <label class="compact-field">
                    <span>세차내역 ID</span>
                    <input
                        v-model="monitor.washHistoryId"
                        min="1"
                        inputmode="numeric"
                        type="number"
                        placeholder="선택 입력"
                    >
                </label>

                <label class="compact-field">
                    <span>잔액 비교 금액</span>
                    <input
                        v-model="monitor.requiredAmount"
                        min="1"
                        step="1"
                        inputmode="numeric"
                        type="number"
                        placeholder="세차내역이 없을 때 입력"
                    >
                </label>

                <button
                    class="refresh-button"
                    type="button"
                    :disabled="stateLoading || !hasMonitorCustomer"
                    title="현재 결제 및 지갑 상태 새로고침"
                    @click="refreshState()"
                >
                    {{ stateLoading ? "조회 중" : "상태 조회" }}
                </button>
            </div>

            <p
                v-if="stateError"
                class="validation-message monitor-message"
            >
                {{ stateError }}
            </p>

            <div
                v-if="paymentState"
                class="status-grid"
            >
                <article>
                    <span>현재 잔액</span>
                    <strong>{{ formatMoney(paymentState.balance) }}</strong>
                    <small>{{ paymentState.customerName || `고객 #${paymentState.customerId}` }}</small>
                </article>
                <article :class="balanceStatusClass">
                    <span>잔액 판정</span>
                    <strong>{{ balanceStatusLabel }}</strong>
                    <small>{{ paymentState.requiredAmount == null ? "금액 또는 세차내역 입력" : `필요 ${formatMoney(paymentState.requiredAmount)}` }}</small>
                </article>
                <article>
                    <span>자동결제</span>
                    <strong>{{ autoPaymentStatusLabel }}</strong>
                    <small>{{ autoPaymentStatusDetail }}</small>
                </article>
                <article>
                    <span>세차 결제상태</span>
                    <strong>{{ paymentState.washPaymentStatus || "내역 미선택" }}</strong>
                    <small>{{ paymentState.washStatus || "세차상태 없음" }}</small>
                </article>
                <article>
                    <span>최근 결제 결과</span>
                    <strong>{{ paymentState.latestPaymentStatus || "기록 없음" }}</strong>
                    <small>{{ paymentState.latestPaymentType || "결제수단 없음" }}</small>
                </article>
                <article>
                    <span>최근 결제 금액</span>
                    <strong>{{ formatMoney(paymentState.latestPaymentAmount) }}</strong>
                    <small>{{ formatDateTime(paymentState.latestPaidAt) }}</small>
                </article>
            </div>

            <div
                v-else-if="!stateError"
                class="monitor-empty"
            >
                고객 ID를 입력하면 잔액과 최근 결제 결과를 실시간으로 확인할 수 있습니다.
            </div>

            <p
                v-if="actionMessage"
                :class="['action-message', actionMessage.ok ? 'success' : 'failure']"
            >
                {{ actionMessage.text }}
            </p>

            <div class="payment-actions">
                <form
                    class="action-tool"
                    @submit.prevent="submitCharge"
                >
                    <div>
                        <span class="panel-kicker">CHARGE</span>
                        <h3>지갑 충전 반영</h3>
                    </div>
                    <label class="compact-field">
                        <span>충전 금액</span>
                        <input
                            v-model="charge.amount"
                            min="1"
                            step="1"
                            type="number"
                            required
                        >
                    </label>
                    <div class="segmented-control" aria-label="충전 결과">
                        <label>
                            <input v-model="charge.result" type="radio" value="SUCCESS">
                            <span>성공</span>
                        </label>
                        <label>
                            <input v-model="charge.result" type="radio" value="FAILED">
                            <span>실패</span>
                        </label>
                    </div>
                    <button
                        class="action-button"
                        type="submit"
                        :disabled="actionLoading || !canRunCharge"
                    >
                        충전 이벤트 전송
                    </button>
                </form>

                <form
                    class="action-tool"
                    @submit.prevent="submitAutoPayment"
                >
                    <div>
                        <span class="panel-kicker">AUTO PAYMENT</span>
                        <h3>자동결제 실행</h3>
                    </div>
                    <p>
                        현재 지갑 잔액을 확인하고 부족하면 등록 카드 자동충전 후 세차 금액을 차감합니다.
                    </p>
                    <dl class="action-context">
                        <dt>고객 ID</dt>
                        <dd>{{ monitor.customerId || "-" }}</dd>
                        <dt>세차내역 ID</dt>
                        <dd>{{ monitor.washHistoryId || "-" }}</dd>
                    </dl>
                    <button
                        class="action-button"
                        type="submit"
                        :disabled="actionLoading || !canRunAutoPayment"
                    >
                        자동결제 실행
                    </button>
                </form>

                <form
                    class="action-tool"
                    @submit.prevent="submitOnsitePayment"
                >
                    <div>
                        <span class="panel-kicker">ONSITE PAYMENT</span>
                        <h3>현장결제 결과</h3>
                    </div>
                    <div class="onsite-fields">
                        <label class="compact-field">
                            <span>결제 금액</span>
                            <input
                                v-model="onsite.amount"
                                min="1"
                                step="1"
                                type="number"
                                placeholder="코스 금액"
                            >
                        </label>
                        <label class="compact-field">
                            <span>결제 수단</span>
                            <select v-model="onsite.paymentType">
                                <option value="QR">QR</option>
                                <option value="CARD">카드</option>
                                <option value="CASH">현금</option>
                            </select>
                        </label>
                    </div>
                    <div class="segmented-control" aria-label="현장결제 결과">
                        <label>
                            <input v-model="onsite.result" type="radio" value="SUCCESS">
                            <span>성공</span>
                        </label>
                        <label>
                            <input v-model="onsite.result" type="radio" value="FAILED">
                            <span>실패</span>
                        </label>
                    </div>
                    <button
                        class="action-button"
                        type="submit"
                        :disabled="actionLoading || !monitor.washHistoryId"
                    >
                        현장결제 전송
                    </button>
                </form>
            </div>
        </section>

        <section class="history-section">
            <div class="history-heading">
                <div>
                    <span class="panel-kicker">SESSION LOG</span>
                    <h2>요청 기록</h2>
                </div>
                <button
                    class="text-button"
                    type="button"
                    :disabled="history.length === 0"
                    @click="clearHistory"
                >
                    기록 비우기
                </button>
            </div>

            <div
                v-if="history.length"
                class="history-table-wrap"
            >
                <table>
                    <thead>
                        <tr>
                            <th>시각</th>
                            <th>이벤트</th>
                            <th>장비 ID</th>
                            <th>상태</th>
                            <th>차량번호</th>
                            <th>결과</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr
                            v-for="item in history"
                            :key="item.id"
                            @click="lastResult = item"
                        >
                            <td>{{ item.requestedAt }}</td>
                            <td>{{ item.operationLabel }}</td>
                            <td>{{ item.deviceId }}</td>
                            <td>
                                <span :class="['table-status', item.ok ? 'success' : 'failure']">
                                    {{ item.status || "ERR" }}
                                </span>
                            </td>
                            <td>{{ item.data?.detectedPlateNumber || item.data?.plateNumber || "-" }}</td>
                            <td>{{ item.data?.message || item.statusText || "-" }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <p
                v-else
                class="history-empty"
            >
                현재 브라우저 세션에 저장된 요청이 없습니다.
            </p>
        </section>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue"
import {
    EQUIPMENT_OPERATIONS,
    getPostPageBackendUrl,
    getTestConsoleState,
    postEquipmentEvent,
    retryTestAutoPayment,
    simulateTestCharge,
    simulateTestOnsitePayment,
    completeTestWash,
    completeTestWalkInPayment,
    cleanupTestVehicle,
} from "../api/postPageApi"

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png"]

const operationOptions = Object.values(EQUIPMENT_OPERATIONS)
const selectedOperation = ref("ENTRY")
const deviceIds = reactive({
    DETECT: 1,
    ENTRY: 1,
    WASH: 2,
    EXIT: 3
})
const imageFile = ref(null)
const previewUrl = ref("")
const fileInput = ref(null)
const validationMessage = ref("")
const isDragging = ref(false)
const loading = ref(false)
const lastResult = ref(null)
const history = ref([])
const copied = ref(false)
const backendUrl = getPostPageBackendUrl()
const monitor = reactive({
    customerId: "",
    washHistoryId: "",
    requiredAmount: ""
})
const charge = reactive({
    amount: 10000,
    result: "SUCCESS"
})
const onsite = reactive({
    amount: "",
    paymentType: "QR",
    result: "SUCCESS"
})
const paymentState = ref(null)
const stateLoading = ref(false)
const stateError = ref("")
const livePolling = ref(true)
const actionLoading = ref(false)
const actionMessage = ref(null)
let pollingTimer = null

const currentOperation = computed(() => {
    return EQUIPMENT_OPERATIONS[selectedOperation.value]
})

const currentDeviceId = computed({
    get() {
        return deviceIds[selectedOperation.value]
    },
    set(value) {
        deviceIds[selectedOperation.value] = value
    }
})

const canSubmit = computed(() => {
    return Number(currentDeviceId.value) > 0 && !!imageFile.value
})

const hasMonitorCustomer = computed(() => {
    return Number(monitor.customerId) > 0
})

const canRunWashPayment = computed(() => {
    return hasMonitorCustomer.value && Number(monitor.washHistoryId) > 0
})

const canRunCharge = computed(() => {
    return hasMonitorCustomer.value
        && Number(charge.amount) > 0
        && paymentState.value?.prepaidSchemaReady !== false
})

const canRunAutoPayment = computed(() => {
    return canRunWashPayment.value
        && paymentState.value?.prepaidSchemaReady !== false
})

const canCompleteWash = computed(() => {
    return Number(monitor.washHistoryId) > 0
})

const autoPaymentStatusLabel = computed(() => {
    if (paymentState.value?.prepaidSchemaReady === false) {
        return "DB 확인 필요"
    }

    return paymentState.value?.autoPayEnabled ? "사용 가능" : "사용 불가"
})

const autoPaymentStatusDetail = computed(() => {
    if (paymentState.value?.prepaidSchemaReady === false) {
        return "선불결제 스키마 불일치"
    }

    if (paymentState.value?.billingCardTableReady === false) {
        return "자동결제 카드 테이블 없음"
    }

    return paymentState.value?.billingCardRegistered
        ? "카드 등록됨"
        : "등록 카드 없음"
})

const balanceStatusLabel = computed(() => {
    if (paymentState.value?.enoughBalance === null
            || paymentState.value?.enoughBalance === undefined) {
        return "기준 없음"
    }

    return paymentState.value.enoughBalance ? "충분" : "부족"
})

const balanceStatusClass = computed(() => {
    if (paymentState.value?.enoughBalance === true) {
        return "status-good"
    }

    if (paymentState.value?.enoughBalance === false) {
        return "status-warn"
    }

    return ""
})

const formattedResponse = computed(() => {
    return JSON.stringify(lastResult.value?.data ?? {}, null, 2)
})

const responseSucceeded = computed(() => {
    const data = lastResult.value?.data

    if (!lastResult.value?.ok) {
        return false
    }

    if (typeof data?.accepted === "boolean") {
        return data.accepted
    }

    if (typeof data?.barrierOpen === "boolean") {
        return data.barrierOpen
    }

    if (typeof data?.success === "boolean") {
        return data.success
    }

    return true
})

const responseOutcome = computed(() => {
    const data = lastResult.value?.data

    if (!data || typeof data !== "object" || Array.isArray(data)) {
        return null
    }

    if (data.eventType === "WASH_REDIRECT") {
        return {
            tone: "warning",
            label: "세차기 재배정",
            title: `세차기 #${data.assignedDeviceId}로 이동`,
            detail: data.message || "안내된 세차기에서 다시 인식해 주세요."
        }
    }

    if (String(data.eventType || "").includes("DENIED")) {
        return {
            tone: "failure",
            label: "처리 거부",
            title: data.reservationStatus || data.eventType,
            detail: data.message || "예약 및 차량 상태를 확인해 주세요."
        }
    }

    if (data.accepted === true) {
        return {
            tone: "success",
            label: "세차 승인",
            title: data.reservationStatus === "FULFILLED"
                ? "예약 사용 완료"
                : "세차 시작 가능",
            detail: data.message || "세차 시작이 승인되었습니다."
        }
    }

    if (data.barrierOpen === true) {
        return {
            tone: "success",
            label: "차단기 개방",
            title: data.reservationMatched ? "예약 차량 확인" : "통과 승인",
            detail: data.assignedDeviceId
                ? `배정 세차기 #${data.assignedDeviceId}로 이동해 주세요.`
                : (data.message || "차단기가 열렸습니다.")
        }
    }

    return null
})

const responseHighlights = computed(() => {
    const data = lastResult.value?.data

    if (!data || typeof data !== "object" || Array.isArray(data)) {
        return []
    }

    const fields = [
        ["detectedPlateNumber", "인식 차량번호"],
        ["plateNumber", "차량번호"],
        ["plateDetectionLogId", "탐지 로그 ID"],
        ["entryExitId", "입출차 ID"],
        ["washHistoryId", "세차내역 ID"],
        ["reservationMatched", "예약 일치"],
        ["reservationId", "예약 ID"],
        ["reservationStatus", "예약 상태"],
        ["reservedDeviceId", "예약 세차기 ID"],
        ["assignedDeviceId", "배정 세차기 ID"],
        ["deviceId", "인식 세차기 ID"],
        ["customerType", "고객구분"],
        ["paymentStatus", "결제상태"],
        ["washStatus", "세차상태"],
        ["accepted", "세차 승인"],
        ["autoPaymentSuccess", "자동결제 성공"],
        ["paidAmount", "결제 금액"],
        ["walletBalanceAfter", "결제 후 잔액"],
        ["barrierOpen", "차단기 열림"],
        ["eventType", "이벤트 결과"],
        ["message", "메시지"]
    ]

    return fields
        .filter(([key]) => data[key] !== null && data[key] !== undefined)
        .map(([key, label]) => ({ key, label, value: data[key] }))
})

function handleFileChange(event) {
    setImage(event.target.files?.[0])
}

function handleDrop(event) {
    isDragging.value = false
    setImage(event.dataTransfer?.files?.[0])
}

function setImage(file) {
    validationMessage.value = ""

    if (!file) {
        return
    }

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
        validationMessage.value = "JPEG 또는 PNG 이미지만 선택할 수 있습니다."
        return
    }

    if (file.size > MAX_FILE_SIZE) {
        validationMessage.value = "이미지 파일은 10MB를 초과할 수 없습니다."
        return
    }

    revokePreviewUrl()
    imageFile.value = file
    previewUrl.value = URL.createObjectURL(file)
}

function clearImage() {
    revokePreviewUrl()
    imageFile.value = null
    validationMessage.value = ""

    if (fileInput.value) {
        fileInput.value.value = ""
    }
}

async function submitEvent() {
    validationMessage.value = ""

    if (!canSubmit.value) {
        validationMessage.value = "장비 ID와 차량 사진을 입력해 주세요."
        return
    }

    loading.value = true
    copied.value = false

    const operation = currentOperation.value
    const result = await postEquipmentEvent(
        selectedOperation.value,
        Number(currentDeviceId.value),
        imageFile.value
    )

    const logItem = {
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        ...result,
        operationKey: operation.key,
        operationLabel: operation.label,
        endpoint: operation.endpoint,
        deviceId: Number(currentDeviceId.value),
        fileName: imageFile.value.name,
        requestedAt: new Date().toLocaleTimeString("ko-KR", {
            hour12: false
        })
    }

    lastResult.value = logItem
    history.value.unshift(logItem)
    loading.value = false

    applyDetectedContext(result.data)

    if (hasMonitorCustomer.value) {
        await refreshState(true)
    }
}

async function refreshState(silent = false) {
    if (!hasMonitorCustomer.value || stateLoading.value) {
        if (!silent && !hasMonitorCustomer.value) {
            stateError.value = "조회할 고객 ID를 입력해 주세요."
        }
        return
    }

    stateLoading.value = true

    const result = await getTestConsoleState({
        customerId: Number(monitor.customerId),
        washHistoryId: toOptionalNumber(monitor.washHistoryId),
        requiredAmount: toOptionalNumber(monitor.requiredAmount)
    })

    if (result.ok) {
        paymentState.value = result.data
        stateError.value = ""
    } else {
        stateError.value = getResponseMessage(result.data, "결제·지갑 상태 조회에 실패했습니다.")
    }

    stateLoading.value = false
}

async function submitCharge() {
    if (!hasMonitorCustomer.value || Number(charge.amount) <= 0) {
        actionMessage.value = {
            ok: false,
            text: "고객 ID와 0원보다 큰 충전 금액을 입력해 주세요."
        }
        return
    }

    await runPaymentAction("지갑 충전", () => {
        return simulateTestCharge({
            customerId: Number(monitor.customerId),
            amount: Number(charge.amount),
            result: charge.result
        })
    })
}

async function submitAutoPayment() {
    if (!canRunWashPayment.value) {
        actionMessage.value = {
            ok: false,
            text: "고객 ID와 세차내역 ID를 입력해 주세요."
        }
        return
    }

    await runPaymentAction("자동결제", () => {
        return retryTestAutoPayment({
            customerId: Number(monitor.customerId),
            washHistoryId: Number(monitor.washHistoryId)
        })
    })
}

async function submitOnsitePayment() {
    if (Number(monitor.washHistoryId) <= 0) {
        actionMessage.value = {
            ok: false,
            text: "현장결제를 적용할 세차내역 ID를 입력해 주세요."
        }
        return
    }

    await runPaymentAction("현장결제", () => {
        return simulateTestOnsitePayment({
            washHistoryId: Number(monitor.washHistoryId),
            amount: toOptionalNumber(onsite.amount),
            paymentType: onsite.paymentType,
            result: onsite.result
        })
    })
}

async function runPaymentAction(operationLabel, request) {
    if (actionLoading.value) {
        return
    }

    actionLoading.value = true
    actionMessage.value = null

    const result = await request()
    const logItem = {
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        ...result,
        operationKey: result.data?.action || "PAYMENT",
        operationLabel,
        endpoint: "/api/admin/test-console",
        deviceId: "-",
        fileName: "-",
        requestedAt: new Date().toLocaleTimeString("ko-KR", {
            hour12: false
        })
    }

    lastResult.value = logItem
    history.value.unshift(logItem)
    applyDetectedContext(result.data)

    if (result.data?.state) {
        paymentState.value = result.data.state
    }

    actionMessage.value = {
        ok: result.ok && result.data?.result === "SUCCESS",
        text: getResponseMessage(
            result.data,
            result.ok ? `${operationLabel} 처리가 완료되었습니다.` : `${operationLabel} 처리에 실패했습니다.`
        )
    }

    actionLoading.value = false
    await refreshState(true)
}

function applyDetectedContext(data) {
    if (!data || typeof data !== "object") {
        return
    }

    if (Number(data.customerId) > 0) {
        monitor.customerId = String(data.customerId)
    }

    if (Number(data.washHistoryId) > 0) {
        monitor.washHistoryId = String(data.washHistoryId)
    }

    if (Number(data.assignedDeviceId) > 0) {
        deviceIds.WASH = Number(data.assignedDeviceId)
    }
}

async function copyResponse() {
    try {
        await navigator.clipboard.writeText(formattedResponse.value)
        copied.value = true
    } catch {
        copied.value = false
    }
}

function clearHistory() {
    history.value = []
    lastResult.value = null
}

function formatFileSize(size) {
    if (size < 1024 * 1024) {
        return `${Math.ceil(size / 1024)} KB`
    }

    return `${(size / (1024 * 1024)).toFixed(2)} MB`
}

function formatResponseValue(value) {
    if (typeof value === "boolean") {
        return value ? "예" : "아니오"
    }

    return String(value)
}

function formatMoney(value) {
    if (value === null || value === undefined || value === "") {
        return "-"
    }

    return `${Number(value).toLocaleString("ko-KR")}원`
}

function formatDateTime(value) {
    if (!value) {
        return "승인 시각 없음"
    }

    return new Date(value).toLocaleString("ko-KR", {
        hour12: false
    })
}

function toOptionalNumber(value) {
    if (value === null || value === undefined || value === "") {
        return null
    }

    const number = Number(value)
    return Number.isFinite(number) ? number : null
}

function getResponseMessage(data, fallback) {
    if (typeof data === "string" && data.trim()) {
        return data
    }

    return data?.message || data?.detail || data?.error || fallback
}

function revokePreviewUrl() {
    if (previewUrl.value) {
        URL.revokeObjectURL(previewUrl.value)
        previewUrl.value = ""
    }
}

async function submitWashComplete() {
    if (!canCompleteWash.value) {
        actionMessage.value = {
            ok: false,
            text: "완료 처리할 세차내역 ID를 입력해 주세요."
        }
        return
    }

    await runPaymentAction("세차 완료", () => {
        return completeTestWash({
            washHistoryId: Number(monitor.washHistoryId)
        })
    })
}

async function submitWalkInPaymentComplete() {
    if (!canCompleteWash.value) {
        actionMessage.value = {
            ok: false,
            text: "결제 완료 처리할 세차내역 ID를 입력해 주세요."
        }
        return
    }

    await runPaymentAction("비회원 결제 완료", () => {
        return completeTestWalkInPayment({
            washHistoryId: Number(monitor.washHistoryId)
        })
    })
}

async function submitVehicleCleanup() {
    await runPaymentAction("전체 테스트 상태 처리", cleanupTestVehicle)
}

onMounted(() => {
    pollingTimer = window.setInterval(() => {
        if (livePolling.value && hasMonitorCustomer.value) {
            refreshState(true)
        }
    }, 2000)
})

onBeforeUnmount(() => {
    revokePreviewUrl()

    if (pollingTimer) {
        window.clearInterval(pollingTimer)
    }
})
</script>

<style scoped>
.post-page {
    min-height: max(100svh, var(--app-shell-height, 640px));
    box-sizing: border-box;
    padding: 28px;
    background: #f4f6f8;
    color: #17202a;
    letter-spacing: 0;
}

.page-header,
.panel-heading,
.history-heading,
.file-meta,
.json-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.page-header {
    max-width: var(--app-content-width, 1280px);
    margin: 0 auto 22px;
}

.eyebrow,
.panel-kicker {
    margin: 0 0 6px;
    color: #536171;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0;
}

.page-header h1,
.panel-heading h2,
.history-heading h2 {
    margin: 0;
}

.page-header h1 {
    font-size: 28px;
}

.panel-heading h2,
.history-heading h2 {
    font-size: 19px;
}

.home-link,
.submit-button {
    display: inline-flex;
    min-height: 42px;
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 6px;
    font: inherit;
    font-weight: 800;
    text-decoration: none;
    cursor: pointer;
}

.home-link {
    padding: 0 16px;
    border: 1px solid #c6ced8;
    background: #ffffff;
    color: #25313d;
}

.operation-strip {
    display: grid;
    max-width: var(--app-content-width, 1280px);
    margin: 0 auto 18px;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    border: 1px solid #cfd6df;
    background: #ffffff;
}

.operation-button {
    display: grid;
    min-width: 0;
    padding: 16px;
    border: 0;
    border-right: 1px solid #d8dee6;
    background: #ffffff;
    color: #485563;
    text-align: left;
    cursor: pointer;
}

.operation-button:last-child {
    border-right: 0;
}

.operation-button.active {
    box-shadow: inset 0 -4px 0 #1769aa;
    background: #eef6fc;
    color: #123f61;
}

.operation-button span {
    font-size: 11px;
    font-weight: 800;
}

.operation-button strong {
    margin-top: 4px;
    font-size: 16px;
}

.operation-button small {
    overflow: hidden;
    margin-top: 5px;
    color: #6a7785;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.workspace-grid {
    display: grid;
    max-width: var(--app-content-width, 1280px);
    margin: 0 auto;
    grid-template-columns: minmax(360px, 0.85fr) minmax(440px, 1.15fr);
    border: 1px solid #cfd6df;
    background: #ffffff;
}

.request-panel,
.response-panel {
    min-width: 0;
    padding: 22px;
}

.request-panel {
    border-right: 1px solid #d8dee6;
}

.method-badge,
.response-status,
.table-status {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 900;
}

.method-badge {
    min-width: 54px;
    min-height: 28px;
    background: #e8f5ee;
    color: #176542;
}

.request-panel form {
    display: grid;
    gap: 16px;
    margin-top: 20px;
}

.field-label {
    display: grid;
    gap: 7px;
    color: #334150;
    font-weight: 800;
}

.field-label input {
    width: 100%;
    min-height: 44px;
    box-sizing: border-box;
    padding: 0 12px;
    border: 1px solid #bcc7d3;
    border-radius: 5px;
    background: #ffffff;
    font: inherit;
}

.field-label small {
    color: #687583;
    font-weight: 500;
}

.upload-zone {
    position: relative;
    display: grid;
    min-height: 260px;
    place-items: center;
    overflow: hidden;
    border: 2px dashed #aeb9c6;
    border-radius: 6px;
    background: #f8fafb;
    cursor: pointer;
}

.upload-zone.dragging {
    border-color: #1769aa;
    background: #edf6fc;
}

.upload-zone.filled {
    border-style: solid;
}

.upload-zone input {
    position: absolute;
    width: 1px;
    height: 1px;
    opacity: 0;
}

.upload-zone img {
    width: 100%;
    height: 260px;
    object-fit: contain;
    background: #111820;
}

.upload-zone div {
    display: grid;
    gap: 7px;
    padding: 24px;
    text-align: center;
}

.upload-zone div span {
    color: #667483;
    font-size: 13px;
}

.file-meta {
    min-width: 0;
    padding: 11px 12px;
    background: #eef2f5;
}

.file-meta div {
    display: grid;
    min-width: 0;
    gap: 3px;
}

.file-meta strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.file-meta span,
.request-meta span {
    color: #677584;
    font-size: 12px;
}

.request-meta {
    display: grid;
    min-width: 0;
    gap: 5px;
}

.request-meta code {
    overflow-wrap: anywhere;
    color: #334150;
    font-size: 12px;
}

.submit-button {
    width: 100%;
    padding: 0 18px;
    background: #1769aa;
    color: #ffffff;
}

.submit-button:disabled,
.text-button:disabled {
    cursor: not-allowed;
    opacity: 0.5;
}

.validation-message {
    margin: 0;
    color: #b42318;
    font-weight: 700;
}

.response-panel {
    display: grid;
    min-height: 570px;
    align-content: start;
}

.response-status {
    min-width: 52px;
    min-height: 30px;
}

.success {
    background: #dff3e8;
    color: #176542;
}

.failure {
    background: #fee7e5;
    color: #a11f17;
}

.response-content {
    display: grid;
    gap: 18px;
    margin-top: 20px;
}

.reservation-outcome {
    display: grid;
    gap: 8px;
    padding: 14px 16px;
    border: 1px solid #cfd6df;
    border-left-width: 5px;
    background: #f8fafb;
}

.reservation-outcome div {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
}

.reservation-outcome span {
    color: #687583;
    font-size: 12px;
    font-weight: 800;
}

.reservation-outcome strong {
    color: #25313d;
}

.reservation-outcome p {
    margin: 0;
    color: #536171;
    font-size: 13px;
    line-height: 1.5;
}

.reservation-outcome.success {
    border-left-color: #238653;
    background: #edf8f1;
}

.reservation-outcome.warning {
    border-left-color: #c47b16;
    background: #fff7e8;
}

.reservation-outcome.failure {
    border-left-color: #b42318;
    background: #fff1f0;
}

.response-summary {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    border: 1px solid #d7dee6;
}

.response-summary div {
    display: grid;
    min-width: 0;
    gap: 5px;
    padding: 12px;
    border-right: 1px solid #d7dee6;
}

.response-summary div:last-child {
    border-right: 0;
}

.response-summary span,
.highlight-list dt {
    color: #687583;
    font-size: 12px;
}

.response-summary strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.highlight-list {
    display: grid;
    grid-template-columns: 160px minmax(0, 1fr);
    margin: 0;
    border-top: 1px solid #d7dee6;
}

.highlight-list dt,
.highlight-list dd {
    margin: 0;
    padding: 10px 12px;
    border-right: 1px solid #d7dee6;
    border-bottom: 1px solid #d7dee6;
}

.highlight-list dt {
    border-left: 1px solid #d7dee6;
    background: #f5f7f9;
    font-weight: 800;
}

.highlight-list dd {
    overflow-wrap: anywhere;
    color: #25313d;
}

.json-heading {
    margin-bottom: -10px;
    color: #465463;
    font-size: 13px;
    font-weight: 800;
}

.text-button {
    padding: 4px;
    border: 0;
    background: transparent;
    color: #1769aa;
    font: inherit;
    font-weight: 800;
    cursor: pointer;
}

pre {
    max-height: 300px;
    overflow: auto;
    margin: 0;
    padding: 16px;
    border-radius: 5px;
    background: #17202a;
    color: #eaf0f5;
    font-size: 13px;
    line-height: 1.55;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}

.response-empty {
    display: grid;
    min-height: 450px;
    place-content: center;
    gap: 7px;
    color: #667483;
    text-align: center;
}

.response-empty strong {
    color: #334150;
}

.payment-console {
    max-width: var(--app-content-width, 1280px);
    margin: 18px auto 0;
    padding: 22px;
    border: 1px solid #cfd6df;
    background: #ffffff;
}

.wash-complete-console {
    display: grid;
    max-width: var(--app-content-width, 1280px);
    box-sizing: border-box;
    margin: 18px auto 0;
    padding: 18px 22px;
    grid-template-columns: minmax(280px, 1.15fr) minmax(150px, 0.55fr) minmax(300px, 1fr);
    gap: 18px;
    align-items: end;
    border: 1px solid #cfd6df;
    background: #ffffff;
}

.completion-action-buttons {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}

.completion-action-message {
    grid-column: 1 / -1;
    margin: 0;
    padding: 10px 12px;
    border-radius: 5px;
    font-size: 13px;
    font-weight: 800;
    text-align: center;
}

.completion-action-message.success {
    color: #166534;
    background: #ecfdf3;
}

.completion-action-message.failure {
    color: #b42318;
    background: #fff1f0;
}

.cleanup-button {
    grid-column: 1 / -1;
    background: #5b6472;
    border-color: #5b6472;
}

.cleanup-button:hover:not(:disabled) {
    background: #444c58;
    border-color: #444c58;
}

.payment-complete-button {
    background: #0f766e;
    border-color: #0f766e;
}

.payment-complete-button:hover:not(:disabled) {
    background: #0b5f59;
    border-color: #0b5f59;
}

.wash-complete-heading {
    display: grid;
    gap: 5px;
}

.wash-complete-heading h2,
.wash-complete-heading p {
    margin: 0;
}

.wash-complete-heading p {
    color: #687583;
    font-size: 13px;
    line-height: 1.5;
}

.wash-complete-button {
    width: 100%;
    background: #1769aa;
}

.polling-toggle {
    display: inline-flex;
    min-height: 34px;
    align-items: center;
    gap: 8px;
    color: #334150;
    font-size: 13px;
    font-weight: 800;
    cursor: pointer;
}

.polling-toggle input {
    width: 18px;
    height: 18px;
    accent-color: #1769aa;
}

.monitor-controls {
    display: grid;
    margin-top: 18px;
    grid-template-columns: repeat(3, minmax(140px, 1fr)) minmax(120px, 0.45fr);
    gap: 12px;
    align-items: end;
}

.compact-field {
    display: grid;
    min-width: 0;
    gap: 6px;
    color: #465463;
    font-size: 13px;
    font-weight: 800;
}

.compact-field input,
.compact-field select {
    width: 100%;
    min-height: 42px;
    box-sizing: border-box;
    padding: 0 11px;
    border: 1px solid #bcc7d3;
    border-radius: 5px;
    background: #ffffff;
    color: #17202a;
    font: inherit;
}

.refresh-button,
.action-button {
    min-height: 42px;
    padding: 0 15px;
    border: 0;
    border-radius: 5px;
    background: #25313d;
    color: #ffffff;
    font: inherit;
    font-weight: 800;
    cursor: pointer;
}

.refresh-button:disabled,
.action-button:disabled {
    cursor: not-allowed;
    opacity: 0.5;
}

.monitor-message {
    margin-top: 12px;
}

.status-grid {
    display: grid;
    margin-top: 16px;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    border: 1px solid #d7dee6;
}

.status-grid article {
    display: grid;
    min-width: 0;
    min-height: 104px;
    box-sizing: border-box;
    align-content: center;
    gap: 6px;
    padding: 14px;
    border-right: 1px solid #d7dee6;
}

.status-grid article:last-child {
    border-right: 0;
}

.status-grid span,
.status-grid small {
    overflow: hidden;
    color: #687583;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.status-grid strong {
    overflow: hidden;
    color: #25313d;
    font-size: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.status-grid .status-good {
    background: #edf8f1;
}

.status-grid .status-warn {
    background: #fff4e5;
}

.monitor-empty {
    margin-top: 16px;
    padding: 20px;
    border: 1px dashed #bcc7d3;
    color: #687583;
    text-align: center;
}

.action-message {
    margin: 16px 0 0;
    padding: 11px 13px;
    border-radius: 4px;
    font-weight: 800;
}

.payment-actions {
    display: grid;
    margin-top: 16px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
}

.action-tool {
    display: grid;
    min-width: 0;
    min-height: 280px;
    box-sizing: border-box;
    align-content: start;
    gap: 14px;
    padding: 18px;
    border: 1px solid #d7dee6;
    border-radius: 6px;
    background: #fafbfc;
}

.action-tool h3 {
    margin: 0;
    font-size: 17px;
}

.action-tool p {
    margin: 0;
    color: #5c6977;
    font-size: 13px;
    line-height: 1.55;
}

.action-tool .action-button {
    width: 100%;
    margin-top: auto;
    background: #1769aa;
}

.segmented-control {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    border: 1px solid #bcc7d3;
    border-radius: 5px;
    overflow: hidden;
}

.segmented-control label {
    min-width: 0;
    cursor: pointer;
}

.segmented-control label:first-child {
    border-right: 1px solid #bcc7d3;
}

.segmented-control input {
    position: absolute;
    opacity: 0;
}

.segmented-control span {
    display: grid;
    min-height: 38px;
    place-items: center;
    color: #536171;
    font-size: 13px;
    font-weight: 800;
}

.segmented-control input:checked + span {
    background: #25313d;
    color: #ffffff;
}

.action-context {
    display: grid;
    grid-template-columns: 1fr auto;
    margin: 0;
    border-top: 1px solid #d7dee6;
}

.action-context dt,
.action-context dd {
    margin: 0;
    padding: 9px 0;
    border-bottom: 1px solid #d7dee6;
}

.action-context dt {
    color: #687583;
    font-size: 12px;
}

.action-context dd {
    font-weight: 800;
}

.onsite-fields {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(100px, 0.65fr);
    gap: 10px;
}

.history-section {
    max-width: var(--app-content-width, 1280px);
    margin: 18px auto 0;
    padding: 22px;
    border: 1px solid #cfd6df;
    background: #ffffff;
}

.history-table-wrap {
    overflow-x: auto;
    margin-top: 16px;
}

table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
}

th,
td {
    padding: 11px 12px;
    border-bottom: 1px solid #dde3e9;
    text-align: left;
    white-space: nowrap;
}

th {
    background: #f4f6f8;
    color: #536171;
    font-size: 12px;
}

tbody tr {
    cursor: pointer;
}

tbody tr:hover {
    background: #f0f6fa;
}

.table-status {
    min-width: 44px;
    min-height: 24px;
}

.history-empty {
    margin: 16px 0 0;
    padding: 18px;
    background: #f5f7f9;
    color: #687583;
    text-align: center;
}

@media (max-width: 900px) {
    .post-page {
        padding: 16px;
    }

    .operation-strip {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .operation-button:nth-child(2) {
        border-right: 0;
    }

    .operation-button:nth-child(-n + 2) {
        border-bottom: 1px solid #d8dee6;
    }

    .workspace-grid {
        grid-template-columns: 1fr;
    }

    .wash-complete-console {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .completion-action-buttons {
        grid-column: 1 / -1;
    }

    .monitor-controls,
    .status-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .status-grid article:nth-child(2n) {
        border-right: 0;
    }

    .status-grid article:nth-child(-n + 4) {
        border-bottom: 1px solid #d7dee6;
    }

    .payment-actions {
        grid-template-columns: 1fr;
    }

    .action-tool {
        min-height: 0;
    }

    .request-panel {
        border-right: 0;
        border-bottom: 1px solid #d8dee6;
    }
}

@media (max-width: 600px) {
    .page-header,
    .panel-heading,
    .history-heading {
        align-items: stretch;
        flex-direction: column;
    }

    .home-link {
        width: 100%;
        box-sizing: border-box;
    }

    .operation-strip,
    .response-summary,
    .wash-complete-console,
    .monitor-controls,
    .status-grid,
    .onsite-fields {
        grid-template-columns: 1fr;
    }

    .completion-action-buttons {
        grid-column: auto;
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .status-grid article,
    .status-grid article:nth-child(2n) {
        border-right: 0;
        border-bottom: 1px solid #d7dee6;
    }

    .status-grid article:last-child {
        border-bottom: 0;
    }

    .operation-button,
    .operation-button:nth-child(2) {
        border-right: 0;
        border-bottom: 1px solid #d8dee6;
    }

    .operation-button:last-child {
        border-bottom: 0;
    }

    .response-summary div {
        border-right: 0;
        border-bottom: 1px solid #d7dee6;
    }

    .response-summary div:last-child {
        border-bottom: 0;
    }

    .highlight-list {
        grid-template-columns: 1fr;
    }

    .highlight-list dt,
    .highlight-list dd {
        border-left: 1px solid #d7dee6;
    }
}
</style>
