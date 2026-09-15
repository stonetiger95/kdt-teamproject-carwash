let kakaoMapPromise

const SCRIPT_ID = "kakao-map-sdk"

export function loadKakaoMapSdk() {
    if (window.kakao?.maps?.Map) {
        return Promise.resolve(window.kakao.maps)
    }

    if (kakaoMapPromise) {
        return kakaoMapPromise
    }

    const appKey = String(import.meta.env.VITE_KAKAO_MAP_APP_KEY || "").trim()
    if (!appKey) {
        return Promise.reject(new Error("VITE_KAKAO_MAP_APP_KEY가 설정되지 않았습니다."))
    }

    kakaoMapPromise = new Promise((resolve, reject) => {
        const timeoutId = window.setTimeout(() => {
            reject(new Error("카카오 지도 SDK 응답 시간이 초과되었습니다."))
        }, 15000)

        function finishLoading() {
            window.clearTimeout(timeoutId)
            if (!window.kakao?.maps) {
                kakaoMapPromise = undefined
                reject(new Error("카카오 지도 SDK를 초기화하지 못했습니다."))
                return
            }

            window.kakao.maps.load(() => resolve(window.kakao.maps))
        }

        const existingScript = document.getElementById(SCRIPT_ID)
        if (existingScript) {
            existingScript.addEventListener("load", finishLoading, { once: true })
            existingScript.addEventListener("error", () => {
                window.clearTimeout(timeoutId)
                kakaoMapPromise = undefined
                reject(new Error("카카오 지도 SDK를 불러오지 못했습니다."))
            }, { once: true })
            return
        }

        const script = document.createElement("script")
        script.id = SCRIPT_ID
        script.async = true
        script.src = [
            "https://dapi.kakao.com/v2/maps/sdk.js",
            `?appkey=${encodeURIComponent(appKey)}`,
            "&autoload=false",
            "&libraries=services"
        ].join("")
        script.addEventListener("load", finishLoading, { once: true })
        script.addEventListener("error", () => {
            window.clearTimeout(timeoutId)
            kakaoMapPromise = undefined
            reject(new Error("카카오 지도 SDK를 불러오지 못했습니다."))
        }, { once: true })
        document.head.appendChild(script)
    })

    return kakaoMapPromise
}

export async function geocodeWithKakao(address) {
    const maps = await loadKakaoMapSdk()
    const geocoder = new maps.services.Geocoder()

    return new Promise((resolve, reject) => {
        geocoder.addressSearch(String(address || "").trim(), (result, status) => {
            if (status !== maps.services.Status.OK || !result?.[0]) {
                reject(new Error("주소에 해당하는 위치를 찾지 못했습니다."))
                return
            }

            resolve({
                latitude: Number(result[0].y),
                longitude: Number(result[0].x)
            })
        })
    })
}
