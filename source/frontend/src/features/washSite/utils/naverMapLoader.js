let naverMapPromise

const SCRIPT_ID = "naver-map-sdk"
const SDK_TIMEOUT_MS = 15000

function getReadyNaverMaps() {
    const maps = window.naver?.maps
    return maps
        && typeof maps.Map === "function"
        && typeof maps.LatLng === "function"
        ? maps
        : null
}

export function loadNaverMapSdk() {
    const readyMaps = getReadyNaverMaps()
    if (readyMaps) {
        return Promise.resolve(readyMaps)
    }

    if (naverMapPromise) {
        return naverMapPromise
    }

    const clientId = String(import.meta.env.VITE_NAVER_MAP_CLIENT_ID || "").trim()
    if (!clientId || clientId === "YOUR_NAVER_MAP_CLIENT_ID") {
        return Promise.reject(new Error("VITE_NAVER_MAP_CLIENT_ID가 설정되지 않았습니다."))
    }

    naverMapPromise = new Promise((resolve, reject) => {
        const callbackName = `__naverMapReady_${Date.now()}`
        const pollId = window.setInterval(resolveWhenReady, 50)
        const timeoutId = window.setTimeout(() => {
            cleanup()
            naverMapPromise = undefined
            reject(new Error("네이버 지도 SDK를 초기화하지 못했습니다. 지도 키와 Web 서비스 URL을 확인해주세요."))
        }, SDK_TIMEOUT_MS)

        function cleanup() {
            window.clearTimeout(timeoutId)
            window.clearInterval(pollId)
            delete window[callbackName]
        }

        function resolveWhenReady() {
            const maps = getReadyNaverMaps()
            if (!maps) {
                return false
            }

            cleanup()
            resolve(maps)
            return true
        }

        function rejectLoad() {
            cleanup()
            naverMapPromise = undefined
            reject(new Error("네이버 지도 SDK를 불러오지 못했습니다."))
        }

        window[callbackName] = resolveWhenReady

        const existingScript = document.getElementById(SCRIPT_ID)
        if (existingScript) {
            existingScript.addEventListener("load", resolveWhenReady, { once: true })
            existingScript.addEventListener("error", rejectLoad, { once: true })
            return
        }

        const script = document.createElement("script")
        script.id = SCRIPT_ID
        script.async = true
        script.defer = true
        script.src = [
            "https://oapi.map.naver.com/openapi/v3/maps.js",
            `?ncpKeyId=${encodeURIComponent(clientId)}`,
            "&submodules=geocoder",
            `&callback=${callbackName}`
        ].join("")
        script.addEventListener("load", resolveWhenReady, { once: true })
        script.addEventListener("error", rejectLoad, { once: true })
        document.head.appendChild(script)
    })

    return naverMapPromise
}

export async function geocodeWithNaver(address) {
    const maps = await loadNaverMapSdk()

    return new Promise((resolve, reject) => {
        maps.Service.geocode({ query: String(address || "").trim() }, (status, response) => {
            if (status !== maps.Service.Status.OK) {
                reject(new Error("주소를 좌표로 변환하지 못했습니다."))
                return
            }

            const result = response?.v2?.addresses?.[0]
            if (!result) {
                reject(new Error("주소에 해당하는 위치를 찾지 못했습니다."))
                return
            }

            resolve({
                latitude: Number(result.y),
                longitude: Number(result.x)
            })
        })
    })
}
