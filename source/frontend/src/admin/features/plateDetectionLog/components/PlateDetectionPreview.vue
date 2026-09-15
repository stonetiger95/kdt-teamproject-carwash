<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  imageUrl: { type: String, default: '' },
  box: { type: Object, default: null },
})
const sourceImage = ref(null)
const overlayCanvas = ref(null)
const cropCanvas = ref(null)
const imageReady = ref(false)
const cropReady = ref(false)

function validBox(box) {
  return box && [box.x1, box.y1, box.x2, box.y2].every(Number.isFinite) && box.x2 > box.x1 && box.y2 > box.y1
}

function draw() {
  const image = sourceImage.value
  const overlay = overlayCanvas.value
  const crop = cropCanvas.value
  imageReady.value = Boolean(image?.complete && image.naturalWidth)
  cropReady.value = false
  if (!imageReady.value || !overlay) return
  overlay.width = image.naturalWidth
  overlay.height = image.naturalHeight
  const context = overlay.getContext('2d')
  context.drawImage(image, 0, 0)
  if (!validBox(props.box)) return
  context.strokeStyle = '#ef4444'
  context.lineWidth = Math.max(3, overlay.width / 350)
  context.strokeRect(props.box.x1, props.box.y1, props.box.x2 - props.box.x1, props.box.y2 - props.box.y1)
  const width = props.box.x2 - props.box.x1
  const height = props.box.y2 - props.box.y1
  const padX = Math.floor(width * .12)
  const padY = Math.floor(height * .12)
  const x = Math.max(0, props.box.x1 - padX)
  const y = Math.max(0, props.box.y1 - padY)
  const x2 = Math.min(image.naturalWidth, props.box.x2 + padX)
  const y2 = Math.min(image.naturalHeight, props.box.y2 + padY)
  crop.width = x2 - x
  crop.height = y2 - y
  crop.getContext('2d').drawImage(image, x, y, crop.width, crop.height, 0, 0, crop.width, crop.height)
  cropReady.value = true
}

watch(() => [props.imageUrl, props.box], async () => { await nextTick(); draw() }, { deep: true })
</script>

<template>
  <div class="plate-preview-comparison">
    <figure><figcaption>원본 및 YOLO 탐지 영역</figcaption><img v-if="imageUrl" ref="sourceImage" :src="imageUrl" alt="번호판 촬영 원본" hidden @load="draw"><canvas v-show="imageReady" ref="overlayCanvas" /><div v-if="!imageReady" class="plate-image-empty"><i class="pi pi-image" /><span>원본 이미지를 표시할 수 없습니다.</span></div></figure>
    <figure><figcaption>번호판 인식 영역</figcaption><canvas v-show="cropReady" ref="cropCanvas" /><div v-if="!cropReady" class="plate-image-empty"><i class="pi pi-crop" /><span>탐지된 번호판 영역이 없습니다.</span></div></figure>
  </div>
</template>

