<template>
  <div class="live2d-container" :style="{ width: width + 'px', height: height + 'px' }">
    <canvas ref="canvasRef" class="live2d-canvas"></canvas>
    <div v-if="error" class="live2d-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useLive2dDriver } from '../composables/useLive2dDriver'

const props = defineProps({
  width: { type: Number, default: 600 },
  height: { type: Number, default: 600 },
  modelPath: { type: String, default: '/models/generated/avatar_default/Haru.model3.json' },
  animationParams: { type: Object, default: () => ({}) },
  mouthOpenY: { type: Number, default: 0 }
})

const canvasRef = ref(null)
const { init, setParams, destroy, error } = useLive2dDriver()

async function initModel() {
  if (!canvasRef.value || !props.modelPath) return
  await init(canvasRef.value, props.modelPath)
  applyAnimation()
}

function applyAnimation() {
  setParams({
    ...props.animationParams,
    ParamMouthOpenY: props.mouthOpenY
  })
}

onMounted(() => {
  initModel()
})

watch(() => props.modelPath, () => {
  initModel()
})

watch(
  () => [props.animationParams, props.mouthOpenY],
  () => {
    applyAnimation()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  destroy()
})
</script>

<style scoped>
.live2d-container {
  overflow: hidden;
  position: relative;
  border-radius: 16px;
}

.live2d-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.live2d-error {
  position: absolute;
  inset: auto 16px 16px 16px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 12px;
}
</style>
