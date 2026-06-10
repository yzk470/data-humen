<template>
  <div class="image-cropper">
    <div class="cropper-canvas-wrapper" ref="wrapperRef">
      <canvas
        ref="canvasRef"
        :width="canvasSize"
        :height="canvasSize"
        @mousedown="onMouseDown"
        @mousemove="onMouseMove"
        @mouseup="onMouseUp"
        @mouseleave="onMouseUp"
      ></canvas>
      <div v-if="!imageLoaded" class="upload-hint">
        请先选择一张二次元图片
      </div>
    </div>
    <div class="crop-info" v-if="imageLoaded">
      裁剪区域: {{ cropX }}, {{ cropY }} — {{ cropW }} × {{ cropH }}
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  imageFile: {
    type: File,
    default: null
  },
  presetCrop: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['crop-change'])

const canvasSize = 400
const canvasRef = ref(null)
const imageLoaded = ref(false)

const cropX = ref(50)
const cropY = ref(50)
const cropW = ref(200)
const cropH = ref(200)

let img = null
let dragging = false
let resizing = false
let dragStartX = 0
let dragStartY = 0
let dragStartCropX = 0
let dragStartCropY = 0
let dragStartCropW = 0
let dragStartCropH = 0

const MIN_CROP = 64

watch(() => props.imageFile, async (file) => {
  if (!file) return
  const reader = new FileReader()
  reader.onload = (e) => {
    img = new Image()
    img.onload = () => {
      imageLoaded.value = true
      initCropBox()
      nextTick(() => draw())
    }
    img.src = e.target.result
  }
  reader.readAsDataURL(file)
})

function initCropBox() {
  const imgW = img.naturalWidth
  const imgH = img.naturalHeight
  const size = Math.min(imgW, imgH, 300)
  cropX.value = Math.round((imgW - size) / 2)
  cropY.value = Math.round((imgH - size) / 2)
  cropW.value = size
  cropH.value = size
  emitCrop()
}

// 监听 AI 预设坐标，自动吸附裁剪框
watch(() => props.presetCrop, (preset) => {
  if (preset && img && imageLoaded.value) {
    cropX.value = preset.x
    cropY.value = preset.y
    cropW.value = preset.width
    cropH.value = preset.height
    nextTick(() => draw())
    emitCrop()
  }
})

function draw() {
  const canvas = canvasRef.value
  if (!canvas || !img) return
  const ctx = canvas.getContext('2d')
  const scale = canvasSize / Math.max(img.naturalWidth, img.naturalHeight)

  const drawW = img.naturalWidth * scale
  const drawH = img.naturalHeight * scale
  const offsetX = (canvasSize - drawW) / 2
  const offsetY = (canvasSize - drawH) / 2

  ctx.clearRect(0, 0, canvasSize, canvasSize)

  // Draw image
  ctx.drawImage(img, offsetX, offsetY, drawW, drawH)

  // Draw crop box coords on canvas
  const cx = offsetX + cropX.value * scale
  const cy = offsetY + cropY.value * scale
  const cw = cropW.value * scale
  const ch = cropH.value * scale

  // Semi-transparent overlay outside crop
  ctx.fillStyle = 'rgba(0, 0, 0, 0.5)'
  ctx.fillRect(0, 0, canvasSize, canvasSize)

  // Clear crop area and re-draw the portion
  ctx.save()
  ctx.beginPath()
  ctx.rect(cx, cy, cw, ch)
  ctx.clip()
  ctx.clearRect(0, 0, canvasSize, canvasSize)
  ctx.drawImage(img,
    cropX.value, cropY.value, cropW.value, cropH.value,
    cx, cy, cw, ch
  )
  ctx.restore()

  // Crop box border
  ctx.strokeStyle = '#409EFF'
  ctx.lineWidth = 2
  ctx.strokeRect(cx, cy, cw, ch)

  // Corner handles
  const handleSize = 8
  ctx.fillStyle = '#409EFF'
  const corners = [
    [cx - handleSize / 2, cy - handleSize / 2],
    [cx + cw - handleSize / 2, cy - handleSize / 2],
    [cx - handleSize / 2, cy + ch - handleSize / 2],
    [cx + cw - handleSize / 2, cy + ch - handleSize / 2]
  ]
  corners.forEach(([hx, hy]) => {
    ctx.fillRect(hx, hy, handleSize, handleSize)
  })
}

function getScale() {
  return canvasSize / Math.max(img.naturalWidth, img.naturalHeight)
}

function isNearCorner(canvasX, canvasY, cornerX, cornerY) {
  return Math.abs(canvasX - cornerX) < 10 && Math.abs(canvasY - cornerY) < 10
}

function onMouseDown(e) {
  if (!img) return
  const rect = canvasRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const scale = getScale()
  const drawW = img.naturalWidth * scale
  const drawH = img.naturalHeight * scale
  const offsetX = (canvasSize - drawW) / 2
  const offsetY = (canvasSize - drawH) / 2

  const cx = offsetX + cropX.value * scale
  const cy = offsetY + cropY.value * scale
  const cw = cropW.value * scale
  const ch = cropH.value * scale

  // Check corner resize
  if (isNearCorner(mx, my, cx + cw, cy + ch)) {
    resizing = true
    dragStartX = mx
    dragStartY = my
    dragStartCropW = cropW.value
    dragStartCropH = cropH.value
  } else if (mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch) {
    // Inside crop box (drag)
    dragging = true
    dragStartX = mx
    dragStartY = my
    dragStartCropX = cropX.value
    dragStartCropY = cropY.value
  }
}

function onMouseMove(e) {
  if (!dragging && !resizing) return
  const rect = canvasRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const scale = getScale()

  if (dragging) {
    const dx = (mx - dragStartX) / scale
    const dy = (my - dragStartY) / scale
    cropX.value = Math.round(
      Math.max(0, Math.min(img.naturalWidth - cropW.value,
        dragStartCropX + dx)))
    cropY.value = Math.round(
      Math.max(0, Math.min(img.naturalHeight - cropH.value,
        dragStartCropY + dy)))
  }

  if (resizing) {
    const dx = (mx - dragStartX) / scale
    const dy = (my - dragStartY) / scale
    const maxSize = Math.min(
      img.naturalWidth - cropX.value,
      img.naturalHeight - cropY.value)
    const newSize = Math.round(
      Math.max(MIN_CROP,
        Math.min(maxSize,
          Math.max(dragStartCropW + dx, dragStartCropH + dy))))
    cropW.value = newSize
    cropH.value = newSize
  }

  draw()
}

function onMouseUp() {
  if (dragging || resizing) {
    emitCrop()
  }
  dragging = false
  resizing = false
}

function emitCrop() {
  emit('crop-change', {
    x: cropX.value,
    y: cropY.value,
    width: cropW.value,
    height: cropH.value
  })
}
</script>

<style scoped>
.image-cropper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.cropper-canvas-wrapper {
  position: relative;
  width: 400px;
  height: 400px;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
  background: #f0f0f0;
}
.cropper-canvas-wrapper canvas {
  cursor: crosshair;
  display: block;
}
.upload-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #999;
  font-size: 14px;
}
.crop-info {
  font-size: 12px;
  color: #666;
}
</style>
