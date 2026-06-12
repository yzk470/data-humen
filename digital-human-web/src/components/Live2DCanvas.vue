<template>
  <div class="live2d-container" :style="{ width: width + 'px', height: height + 'px' }">
    <canvas :key="canvasKey" ref="canvasRef" class="live2d-canvas"></canvas>
    <div v-if="error" class="live2d-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useLive2dDriver } from '../composables/useLive2dDriver'
import { usePixiLive2dDriver } from '../composables/usePixiLive2dDriver'

const props = defineProps({
  width: { type: Number, default: 600 },
  height: { type: Number, default: 600 },
  modelPath: { type: String, default: '/models/generated/avatar_default/Haru.model3.json' },
  animationParams: { type: Object, default: () => ({}) },
  mouthOpenY: { type: Number, default: 0 }
})

const canvasRef = ref(null)
const canvasKey = ref(0)
const c4driver = useLive2dDriver()
const pixiDriver = usePixiLive2dDriver()

// Cubism 2: .model.json 但不以 .model3.json 结尾
function isCubism2(path) {
  if (!path || path.endsWith('.model3.json')) return false
  return path.endsWith('.model.json') || path.endsWith('/model.json')
}

const driver = ref(c4driver)
const error = computed(() => driver.value.error?.value ?? null)

function pickDriver(path) {
  driver.value = isCubism2(path) ? pixiDriver : c4driver
}

async function initModel() {
  driver.value.destroy()
  canvasKey.value += 1
  await nextTick()
  if (!canvasRef.value || !props.modelPath) return
  pickDriver(props.modelPath)
  await driver.value.init(canvasRef.value, props.modelPath)
  applyAnimation()
}

function applyAnimation() {
  driver.value.setParams({
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
  c4driver.destroy()
  c4driver.disposeFramework?.()
  pixiDriver.destroy()
})
</script>

<style scoped>
.live2d-container {
  position: relative;
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
