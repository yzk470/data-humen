<template>
  <div class="chat-view">
    <div class="main-stage">
      <Live2DCanvas :width="600" :height="600" />
    </div>
    <ChatPanel />
    <InputBar />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useSessionStore } from '../stores/session'
import { useSignaling } from '../composables/useSignaling'
import { useRtcClient } from '../composables/useRtcClient'
import Live2DCanvas from '../components/Live2DCanvas.vue'
import ChatPanel from '../components/ChatPanel.vue'
import InputBar from '../components/InputBar.vue'

const sessionStore = useSessionStore()
const signaling = useSignaling()
const rtcClient = useRtcClient()

onMounted(async () => {
  await sessionStore.initSession()
  signaling.connect(sessionStore.sessionId)
})

onUnmounted(() => {
  signaling.disconnect()
  rtcClient.close()
})
</script>

<style scoped>
.chat-view {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}
.main-stage {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
