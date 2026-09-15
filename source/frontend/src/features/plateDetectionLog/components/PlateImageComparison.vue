<template>
    <div class="image-comparison">
        <figure>
            <figcaption>원본 사진</figcaption>

            <img
                v-if="imageUrl"
                ref="sourceImage"
                :src="imageUrl"
                alt="번호판 촬영 원본"
                @load="drawCrop"
            >

            <div v-else class="empty-image">
                원본 이미지가 없습니다.
            </div>
        </figure>

        <figure>
            <figcaption>YOLO 크롭</figcaption>

            <canvas
                v-show="cropReady"
                ref="cropCanvas"
                aria-label="YOLO가 탐지한 번호판 크롭"
            ></canvas>

            <div v-if="!cropReady" class="empty-image">
                YOLO 탐지 영역이 없습니다.
            </div>
        </figure>
    </div>
</template>

<script setup>
import { nextTick, ref, watch } from "vue"

const props = defineProps({
    imageUrl: { type: String, default: "" },
    x1: { type: Number, default: null },
    y1: { type: Number, default: null },
    x2: { type: Number, default: null },
    y2: { type: Number, default: null },

    // FastAPI의 CROP_PADDING_RATIO 기본값과 동일하게 유지
    paddingRatio: { type: Number, default: 0.12 }
})

const sourceImage = ref(null)
const cropCanvas = ref(null)
const cropReady = ref(false)

function drawCrop() {
    cropReady.value = false

    const image = sourceImage.value
    const canvas = cropCanvas.value
    const coordinates = [props.x1, props.y1, props.x2, props.y2]

    if (
        !image ||
        !canvas ||
        !image.complete ||
        !coordinates.every(Number.isFinite)
    ) {
        return
    }

    const boxWidth = props.x2 - props.x1
    const boxHeight = props.y2 - props.y1

    if (boxWidth <= 0 || boxHeight <= 0) {
        return
    }

    const padX = Math.floor(boxWidth * props.paddingRatio)
    const padY = Math.floor(boxHeight * props.paddingRatio)

    const cropX1 = Math.max(0, props.x1 - padX)
    const cropY1 = Math.max(0, props.y1 - padY)
    const cropX2 = Math.min(image.naturalWidth, props.x2 + padX)
    const cropY2 = Math.min(image.naturalHeight, props.y2 + padY)

    const cropWidth = cropX2 - cropX1
    const cropHeight = cropY2 - cropY1

    if (cropWidth <= 0 || cropHeight <= 0) {
        return
    }

    canvas.width = cropWidth
    canvas.height = cropHeight

    const context = canvas.getContext("2d")

    context.clearRect(0, 0, cropWidth, cropHeight)
    context.drawImage(
        image,
        cropX1,
        cropY1,
        cropWidth,
        cropHeight,
        0,
        0,
        cropWidth,
        cropHeight
    )

    cropReady.value = true
}

watch(
    () => [
        props.imageUrl,
        props.x1,
        props.y1,
        props.x2,
        props.y2,
        props.paddingRatio
    ],
    async () => {
        cropReady.value = false
        await nextTick()
        drawCrop()
    }
)
</script>

<style scoped>
.image-comparison {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

figure {
    min-width: 0;
    margin: 0;
}

figcaption {
    margin-bottom: 8px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 900;
}

img,
canvas {
    display: block;
    width: 100%;
    height: 260px;
    box-sizing: border-box;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    object-fit: contain;
}

canvas {
    object-fit: contain;
}

.empty-image {
    display: grid;
    place-items: center;
    height: 260px;
    box-sizing: border-box;
    border: 1px dashed #cbd6e6;
    border-radius: 8px;
    color: #52637d;
    background: #ffffff;
    font-weight: 800;
}

@media (max-width: 700px) {
    .image-comparison {
        grid-template-columns: 1fr;
    }
}
</style>