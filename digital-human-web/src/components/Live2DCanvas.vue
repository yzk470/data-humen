<template>
  <canvas ref="canvasRef" :width="width" :height="height" class="live2d-canvas"></canvas>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useLive2dDriver } from '../composables/useLive2dDriver'
import { useChatStore } from '../stores/chat'

const props = defineProps({
  width: { type: Number, default: 600 },
  height: { type: Number, default: 600 },
  modelPath: { type: String, default: '/models/default.model3.json' }
})

const canvasRef = ref(null)
const chatStore = useChatStore()
const driver = useLive2dDriver()

onMounted(async () => {
  if (canvasRef.value) {
    await driver.init(canvasRef.value, props.modelPath)
  }
})

// 监听 modelPath 变化，动态切换模型
watch(() => props.modelPath, async (newPath) => {
  if (newPath && canvasRef.value) {
    driver.destroy()
    await driver.init(canvasRef.value, newPath)
  }
})

watch(() => chatStore.currentAnimationParams, (params) => {
  if (params && Object.keys(params).length > 0) {
    driver.setParams(params)
  }
}, { deep: true })

onUnmounted(() => {
  driver.destroy()
})
</script>

<style scoped>
.live2d-canvas {
  display: block;
  margin: 0 auto;
}
</style>
