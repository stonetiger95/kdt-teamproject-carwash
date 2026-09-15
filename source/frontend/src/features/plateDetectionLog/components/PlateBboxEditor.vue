<template>
    <div class="bbox-editor">
        <section class="editor-panel">
            <header class="panel-header">
                <h3>번호판 영역 보정</h3>

                <span class="bbox-status">
                    {{ statusText }}
                </span>
            </header>

            <div class="annotation-wrap">
                <canvas
                    ref="annotationCanvas"
                    class="annotation-canvas"
                    @pointerdown="startDrawing"
                    @pointermove="continueDrawing"
                    @pointerup="finishDrawing"
                    @pointercancel="cancelDrawing"
                    @contextmenu.prevent
                ></canvas>

                <div
                    v-if="!imageReady"
                    class="canvas-empty"
                >
                    {{ imageError || "이미지를 불러오는 중입니다." }}
                </div>
            </div>

            <p class="editor-guide">
                <template v-if="readonly">
                    빨간색은 YOLO 예측, 초록색은 검수 확정 영역입니다.
                </template>

                <template v-else>
                    빨간색은 YOLO 예측, 초록색은 검수 확정 영역입니다.
                    원본 위에서 드래그하면 새 영역으로 보정됩니다.
                </template>
            </p>

            <div 
                class="editor-actions"
                v-if="!readonly"
            >
                <button
                    type="button"
                    :disabled="!isValidBox(predictedBox)"
                    @click="acceptYoloBox"
                >
                    YOLO 영역 사용
                </button>

                <button
                    type="button"
                    class="danger-button"
                    @click="markNoPlate"
                >
                    번호판 없음
                </button>
            </div>
        </section>

        <section class="crop-panel">
            <h3>보정 전·후 비교</h3>

            <div class="crop-comparison">
                <figure>
                    <figcaption>
                        YOLO 크롭 · 보정 전
                    </figcaption>

                    <canvas
                        v-show="yoloCropReady"
                        ref="yoloCropCanvas"
                        class="crop-canvas"
                    ></canvas>

                    <div
                        v-if="!yoloCropReady"
                        class="crop-empty"
                    >
                        YOLO 탐지 영역이 없습니다.
                    </div>
                </figure>

                <figure>
                    <figcaption>
                        검수 크롭 · 보정 후
                    </figcaption>

                    <canvas
                        v-show="reviewCropReady"
                        ref="reviewCropCanvas"
                        class="crop-canvas"
                    ></canvas>

                    <div
                        v-if="!reviewCropReady"
                        class="crop-empty"
                    >
                        확정된 번호판 영역이 없습니다.
                    </div>
                </figure>
            </div>

            <p class="crop-guide">
                미리보기에는 {{ paddingPercent }}% 패딩이 적용됩니다.
                저장되는 bbox 좌표에는 패딩이 포함되지 않습니다.
            </p>
        </section>
    </div>
</template>

<script setup>
import { computed, ref, watch } from "vue"

const props = defineProps({
    imageUrl: {
        type: String,
        default: ""
    },
    predictedBox: {
        type: Object,
        default: null
    },
    modelValue: {
        type: Object,
        default: null
    },
    status: {
        type: String,
        default: ""
    },
    paddingRatio: {
        type: Number,
        default: 0.12
    },
    readonly: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits([
    "update:modelValue",
    "update:status"
])

const annotationCanvas = ref(null)
const yoloCropCanvas = ref(null)
const reviewCropCanvas = ref(null)
const sourceImage = ref(null)

const imageReady = ref(false)
const imageError = ref("")
const yoloCropReady = ref(false)
const reviewCropReady = ref(false)

const dragStart = ref(null)
const draftBox = ref(null)

let imageLoadToken = 0

const statusText = computed(() => {
    if (props.status === "ACCEPTED") {
        return "YOLO 영역 승인"
    }

    if (props.status === "CORRECTED") {
        return "영역 보정됨"
    }

    if (props.status === "NO_PLATE") {
        return "번호판 없음"
    }

    return "미검수"
})

const paddingPercent = computed(() =>
    Math.round(props.paddingRatio * 100)
)

watch(
    () => props.imageUrl,
    loadImage,
    { immediate: true }
)

watch(
    () => [
        props.predictedBox?.x1,
        props.predictedBox?.y1,
        props.predictedBox?.x2,
        props.predictedBox?.y2,
        props.modelValue?.x1,
        props.modelValue?.y1,
        props.modelValue?.x2,
        props.modelValue?.y2,
        props.status,
        props.paddingRatio
    ],
    () => {
        if (!dragStart.value) {
            draftBox.value = null
        }

        redraw()
        drawCropPreviews()
    }
)

function loadImage() {
    const currentToken = ++imageLoadToken

    imageReady.value = false
    imageError.value = ""
    sourceImage.value = null
    yoloCropReady.value = false
    reviewCropReady.value = false
    clearCanvas(annotationCanvas.value)
    clearCanvas(yoloCropCanvas.value)
    clearCanvas(reviewCropCanvas.value)

    if (!props.imageUrl) {
        imageError.value = "원본 이미지가 없습니다."
        return
    }

    const image = new Image()

    image.onload = () => {
        if (currentToken !== imageLoadToken) {
            return
        }

        sourceImage.value = image
        imageReady.value = true

        const canvas = annotationCanvas.value

        canvas.width = image.naturalWidth
        canvas.height = image.naturalHeight

        redraw()
        drawCropPreviews()
    }

    image.onerror = () => {
        if (currentToken !== imageLoadToken) {
            return
        }

        imageError.value = "원본 이미지를 불러오지 못했습니다."
    }

    image.src = props.imageUrl
}

function startDrawing(event) {
    if (
        props.readonly ||
        !imageReady.value
    ) {
        return
    }

    event.currentTarget.setPointerCapture(event.pointerId)

    const point = eventToImagePoint(event)

    dragStart.value = point
    draftBox.value = {
        x1: point.x,
        y1: point.y,
        x2: point.x,
        y2: point.y
    }

    redraw()
}

function continueDrawing(event) {
    if (props.readonly || !dragStart.value) {
    return
    }
    
    if (!dragStart.value) {
        return
    }

    const current = eventToImagePoint(event)

    draftBox.value = normalizeBox(
        dragStart.value,
        current
    )

    redraw()
    drawCropPreviews()
}

function finishDrawing(event) {
    if (props.readonly || !dragStart.value) {
        return
    }

    if (!dragStart.value) {
        return
    }

    const end = eventToImagePoint(event)
    const box = normalizeBox(dragStart.value, end)

    if (
        event.currentTarget.hasPointerCapture(event.pointerId)
    ) {
        event.currentTarget.releasePointerCapture(
            event.pointerId
        )
    }

    dragStart.value = null

    if (!isValidBox(box, 5)) {
        draftBox.value = null
        redraw()
        drawCropPreviews()
        return
    }

    draftBox.value = box

    emit("update:modelValue", { ...box })
    emit("update:status", "CORRECTED")

    redraw()
    drawCropPreviews()
}

function cancelDrawing() {
    dragStart.value = null
    draftBox.value = null

    redraw()
    drawCropPreviews()
}

function acceptYoloBox() {
    if (
        props.readonly ||
        !imageReady.value
    ) {
        return
    }

    const box = {
        x1: props.predictedBox.x1,
        y1: props.predictedBox.y1,
        x2: props.predictedBox.x2,
        y2: props.predictedBox.y2
    }

    draftBox.value = box

    emit("update:modelValue", box)
    emit("update:status", "ACCEPTED")

    redraw()
    drawCropPreviews()
}

function markNoPlate() {
    if (props.readonly) {
        return
    }

    dragStart.value = null
    draftBox.value = null

    emit("update:modelValue", null)
    emit("update:status", "NO_PLATE")

    redraw()
    drawCropPreviews()
}

function eventToImagePoint(event) {
    const canvas = annotationCanvas.value
    const rect = canvas.getBoundingClientRect()

    const x = Math.round(
        (event.clientX - rect.left) *
        (canvas.width / rect.width)
    )

    const y = Math.round(
        (event.clientY - rect.top) *
        (canvas.height / rect.height)
    )

    return {
        x: clamp(x, 0, canvas.width),
        y: clamp(y, 0, canvas.height)
    }
}

function normalizeBox(start, end) {
    return {
        x1: Math.min(start.x, end.x),
        y1: Math.min(start.y, end.y),
        x2: Math.max(start.x, end.x),
        y2: Math.max(start.y, end.y)
    }
}

function redraw() {
    const canvas = annotationCanvas.value
    const image = sourceImage.value

    if (!canvas || !image || !imageReady.value) {
        return
    }

    const context = canvas.getContext("2d")

    context.clearRect(
        0,
        0,
        canvas.width,
        canvas.height
    )

    context.drawImage(
        image,
        0,
        0,
        canvas.width,
        canvas.height
    )

    if (isValidBox(props.predictedBox)) {
        drawBox(
            context,
            props.predictedBox,
            "#ef4444",
            "YOLO"
        )
    }

    const reviewedBox = getActiveBox()

    if (
        props.status !== "NO_PLATE" &&
        isValidBox(reviewedBox)
    ) {
        drawBox(
            context,
            reviewedBox,
            "#22c55e",
            "검수"
        )
    }
}

function drawBox(context, box, color, label) {
    const lineWidth = Math.max(
        2,
        context.canvas.width / 600
    )

    const fontSize = Math.max(
        16,
        context.canvas.width / 70
    )

    context.save()
    context.strokeStyle = color
    context.fillStyle = color
    context.lineWidth = lineWidth
    context.font = `bold ${fontSize}px sans-serif`

    context.strokeRect(
        box.x1,
        box.y1,
        box.x2 - box.x1,
        box.y2 - box.y1
    )

    context.fillText(
        label,
        box.x1,
        Math.max(fontSize, box.y1 - 6)
    )

    context.restore()
}

function drawCropPreviews() {
    yoloCropReady.value = drawCropToCanvas(
        props.predictedBox,
        yoloCropCanvas.value
    )

    const reviewedBox =
        props.status === "NO_PLATE"
            ? null
            : getActiveBox()

    reviewCropReady.value = drawCropToCanvas(
        reviewedBox,
        reviewCropCanvas.value
    )
}

function drawCropToCanvas(box, canvas) {
    const image = sourceImage.value

    if (
        !image ||
        !canvas ||
        !isValidBox(box)
    ) {
        clearCanvas(canvas)
        return false
    }

    const boxWidth = box.x2 - box.x1
    const boxHeight = box.y2 - box.y1

    const padX = Math.floor(
        boxWidth * props.paddingRatio
    )
    const padY = Math.floor(
        boxHeight * props.paddingRatio
    )

    const cropX1 = clamp(
        box.x1 - padX,
        0,
        image.naturalWidth
    )

    const cropY1 = clamp(
        box.y1 - padY,
        0,
        image.naturalHeight
    )

    const cropX2 = clamp(
        box.x2 + padX,
        0,
        image.naturalWidth
    )

    const cropY2 = clamp(
        box.y2 + padY,
        0,
        image.naturalHeight
    )

    const cropWidth = cropX2 - cropX1
    const cropHeight = cropY2 - cropY1

    if (cropWidth <= 0 || cropHeight <= 0) {
        clearCanvas(canvas)
        return false
    }

    canvas.width = cropWidth
    canvas.height = cropHeight

    const context = canvas.getContext("2d")

    context.clearRect(
        0,
        0,
        cropWidth,
        cropHeight
    )

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

    return true
}

function getActiveBox() {
    return draftBox.value || props.modelValue
}

function isValidBox(box, minimumSize = 0) {
    if (!box) {
        return false
    }

    const coordinates = [
        box.x1,
        box.y1,
        box.x2,
        box.y2
    ]

    return (
        coordinates.every(Number.isFinite) &&
        box.x1 >= 0 &&
        box.y1 >= 0 &&
        box.x2 - box.x1 > minimumSize &&
        box.y2 - box.y1 > minimumSize
    )
}

function clearCanvas(canvas) {
    if (!canvas) {
        return
    }

    const context = canvas.getContext("2d")
    context.clearRect(0, 0, canvas.width, canvas.height)
}

function clamp(value, minimum, maximum) {
    return Math.min(
        maximum,
        Math.max(minimum, value)
    )
}
</script>

<style scoped>
.bbox-editor {
    display: grid;
    grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.6fr);
    gap: 18px;
    margin-bottom: 20px;
}

.editor-panel,
.crop-panel {
    min-width: 0;
    padding: 16px;
    border: 1px solid #dce5f1;
    border-radius: 10px;
    background: #f8fafc;
}

.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

h3 {
    margin: 0 0 12px;
    color: #071b3a;
    font-size: 16px;
}

.bbox-status {
    margin-bottom: 12px;
    color: #52637d;
    font-size: 13px;
    font-weight: 800;
}

.annotation-wrap {
    position: relative;
    min-height: 220px;
    overflow: hidden;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    background: #111827;
}

.annotation-canvas {
    display: block;
    width: 100%;
    height: auto;
    touch-action: none;
    cursor: crosshair;
}

.canvas-empty {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    min-height: 220px;
    color: #52637d;
    background: #ffffff;
    font-weight: 800;
}

.crop-canvas {
    display: block;
    width: 100%;
    height: 260px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    background: #ffffff;
    object-fit: contain;
}

.crop-empty {
    display: grid;
    place-items: center;
    height: 260px;
    border: 1px dashed #cbd6e6;
    border-radius: 8px;
    color: #52637d;
    background: #ffffff;
    font-weight: 800;
}

.editor-guide,
.crop-guide {
    margin: 10px 0 0;
    color: #52637d;
    font-size: 13px;
    line-height: 1.5;
}

.editor-actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
}

.editor-actions button {
    min-height: 38px;
    padding: 0 14px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    font-weight: 800;
    cursor: pointer;
}

.editor-actions button:disabled {
    color: #94a3b8;
    background: #edf2f8;
    cursor: not-allowed;
}

.editor-actions .danger-button {
    color: #b91c1c;
    border-color: #fecaca;
    background: #fff7f7;
}

.crop-comparison {
    display: grid;
    gap: 14px;
}

.crop-comparison figure {
    min-width: 0;
    margin: 0;
}

.crop-comparison figcaption {
    margin-bottom: 8px;
    color: #1e3354;
    font-size: 13px;
    font-weight: 900;
}

@media (max-width: 800px) {
    .bbox-editor {
        grid-template-columns: 1fr;
    }
}
</style>