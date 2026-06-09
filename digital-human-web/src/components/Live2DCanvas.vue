<template>
  <div class="live2d-container" :style="{ width: width + 'px', height: height + 'px' }">
    <iframe
      ref="iframeRef"
      src="http://localhost:5000"
      class="live2d-frame"
      frameborder="0"
      allow="autoplay"
      scrolling="no"
    ></iframe>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  width: { type: Number, default: 600 },
  height: { type: Number, default: 600 },
  modelPath: { type: String, default: '/models/generated/avatar_default/Haru.model3.json' }
})

const iframeRef = ref(null)
</script>

<style scoped>
.live2d-container {
  overflow: hidden;
  position: relative;
  border-radius: 16px;
}
.live2d-frame {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
