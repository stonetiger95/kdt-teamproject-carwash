<template>
    <section class="chatbot-wash-site-map">
        <div
            ref="mapElement"
            :class="['chatbot-map-canvas', { hidden: mapError }]"
            :aria-label="`${site.siteName || '세차장'} 위치 지도`"
        ></div>
        <div v-if="mapError" class="chatbot-map-fallback">
            지도를 불러오지 못했습니다. 아래 주소를 확인해주세요.
        </div>
        <strong>{{ site.siteName || "세차장" }}</strong>
        <span>{{ site.address || "주소 미등록" }}</span>
        <a
            :href="naverMapUrl"
            target="_blank"
            rel="noopener noreferrer"
        >네이버 지도에서 보기</a>
    </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue"
import {
    geocodeWithNaver,
    loadNaverMapSdk
} from "@/features/washSite/utils/naverMapLoader.js"

const props = defineProps({
    site: {
        type: Object,
        required: true
    }
})

const mapElement = ref(null)
const mapError = ref("")
let map
let marker

const naverMapUrl = computed(() => {
    const query = props.site.address || props.site.siteName || "세차장"
    return `https://map.naver.com/p/search/${encodeURIComponent(query)}`
})

onMounted(renderMap)

onBeforeUnmount(() => {
    marker?.setMap(null)
    marker = null
    map = null
})

async function renderMap() {
    try {
        let latitude = Number(props.site.latitude)
        let longitude = Number(props.site.longitude)
        if (!isCoordinate(latitude, longitude)) {
            const coordinates = await geocodeWithNaver(props.site.address)
            latitude = coordinates.latitude
            longitude = coordinates.longitude
        }

        const maps = await loadNaverMapSdk()
        await nextTick()
        const position = new maps.LatLng(latitude, longitude)
        map = new maps.Map(mapElement.value, {
            center: position,
            zoom: 16,
            draggable: false,
            scrollWheel: false,
            disableDoubleClickZoom: true,
            zoomControl: false,
            mapTypeControl: false
        })
        marker = new maps.Marker({
            map,
            position,
            title: props.site.siteName || "세차장"
        })
    } catch (error) {
        console.warn("챗봇 세차장 지도를 불러오지 못했습니다.", error)
        mapError.value = error?.message || "지도를 불러오지 못했습니다."
    }
}

function isCoordinate(latitude, longitude) {
    return Number.isFinite(latitude)
        && Number.isFinite(longitude)
        && latitude >= -90
        && latitude <= 90
        && longitude >= -180
        && longitude <= 180
}
</script>

<style scoped>
.chatbot-wash-site-map {
    display: grid;
    gap: 6px;
    margin-top: 10px;
    overflow: hidden;
    border: 1px solid #d9e4f1;
    border-radius: 10px;
    background: #fff;
}

.chatbot-map-canvas {
    width: 100%;
    height: 132px;
    background: #eaf1f8;
}

.chatbot-map-canvas.hidden {
    display: none;
}

.chatbot-map-fallback {
    padding: 12px;
    color: #667085;
    background: #f4f7fb;
    font-size: 11px;
}

.chatbot-wash-site-map > strong,
.chatbot-wash-site-map > span,
.chatbot-wash-site-map > a {
    margin-inline: 10px;
}

.chatbot-wash-site-map > strong {
    color: #132b4f;
    font-size: 12px;
}

.chatbot-wash-site-map > span {
    color: #667085;
    font-size: 11px;
}

.chatbot-wash-site-map > a {
    margin-bottom: 9px;
    color: #155da8;
    font-size: 11px;
    font-weight: 800;
    text-decoration: none;
}
</style>
