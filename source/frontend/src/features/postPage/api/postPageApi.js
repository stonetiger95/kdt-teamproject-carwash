import axios from "axios"

const configuredBackendBaseUrl = String(
    import.meta.env.VITE_BACKEND_URL || ""
).trim().replace(/\/$/, "")

// Local Vite development talks directly to Spring Boot. In a production build,
// an empty base URL keeps requests on the current origin so Caddy can proxy /api.
const backendBaseUrl = configuredBackendBaseUrl
    || (import.meta.env.DEV ? "http://localhost:8080" : "")

const postPageClient = axios.create({
    baseURL: backendBaseUrl,
    timeout: 60000
})

postPageClient.interceptors.request.use(config => {
    const accessToken = localStorage.getItem("accessToken")

    if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`
    }

    return config
})

const TEST_CONSOLE_BASE_URL = "/api/admin/test-console"

export const EQUIPMENT_OPERATIONS = Object.freeze({
    DETECT: {
        key: "DETECT",
        label: "번호판 탐지",
        shortLabel: "탐지",
        endpoint: "/api/plate-detections/preview",
        deviceHint: "일반 카메라"
    },
    ENTRY: {
        key: "ENTRY",
        label: "입차 처리",
        shortLabel: "입차",
        endpoint: "/api/gates/entry",
        deviceHint: "ENTRY_CAMERA"
    },
    WASH: {
        key: "WASH",
        label: "세차 시작",
        shortLabel: "세차",
        endpoint: "/api/wash-cycles/detect",
        deviceHint: "WASH_CAMERA"
    },
    EXIT: {
        key: "EXIT",
        label: "출차 처리",
        shortLabel: "출차",
        endpoint: "/api/gates/exit",
        deviceHint: "EXIT_CAMERA"
    }
})

export function getPostPageBackendUrl() {
    return backendBaseUrl
}

async function runRequest(request) {
    const startedAt = performance.now()

    try {
        const response = await request()

        return {
            ok: true,
            status: response.status,
            statusText: response.statusText,
            elapsedMs: Math.round(performance.now() - startedAt),
            data: response.data
        }
    } catch (error) {
        return {
            ok: false,
            status: error.response?.status || 0,
            statusText: error.response?.statusText || "NETWORK_ERROR",
            elapsedMs: Math.round(performance.now() - startedAt),
            data: error.response?.data || {
                message: error.message || "테스트 요청에 실패했습니다."
            }
        }
    }
}

export async function postEquipmentEvent(operationKey, deviceId, image) {
    const operation = EQUIPMENT_OPERATIONS[operationKey]

    if (!operation) {
        throw new Error("지원하지 않는 장비 이벤트입니다.")
    }

    const formData = new FormData()
    formData.append("deviceId", String(deviceId))
    formData.append("image", image)

    return runRequest(() => {
        return postPageClient.post(
            operation.endpoint,
            formData
        )
    })
}

export function getTestConsoleState(params) {
    return runRequest(() => {
        return postPageClient.get(`${TEST_CONSOLE_BASE_URL}/state`, {
            params
        })
    })
}

export function simulateTestCharge(request) {
    return runRequest(() => {
        return postPageClient.post(
            `${TEST_CONSOLE_BASE_URL}/charges`,
            request
        )
    })
}

export function retryTestAutoPayment(request) {
    return runRequest(() => {
        return postPageClient.post(
            `${TEST_CONSOLE_BASE_URL}/auto-payments`,
            request
        )
    })
}

export function simulateTestOnsitePayment(request) {
    return runRequest(() => {
        return postPageClient.post(
            `${TEST_CONSOLE_BASE_URL}/onsite-payments`,
            request
        )
    })
}

export function completeTestWash(request) {
    return runRequest(() => {
        return postPageClient.post(
            `${TEST_CONSOLE_BASE_URL}/wash-completions`,
            request
        )
    })
}

export function completeTestWalkInPayment(request) {
    return runRequest(() => {
        return postPageClient.post(
            `${TEST_CONSOLE_BASE_URL}/walk-in-payment-completions`,
            request
        )
    })
}

export function cleanupTestVehicle() {
    return runRequest(() => {
        return postPageClient.post(
            `${TEST_CONSOLE_BASE_URL}/vehicle-cleanups`
        )
    })
}
