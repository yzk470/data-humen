<template>
  <div class="chat-view">
    <div class="main-stage">
      <div class="runtime-switchers">
        <el-select v-model="selectedVoiceId" size="small" style="width: 180px" placeholder="音色">
          <el-option v-for="item in preferencesStore.voiceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="selectedModelPath" size="small" style="width: 220px" placeholder="形象">
          <el-option v-for="item in preferencesStore.modelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>

      <Live2DCanvas
        :width="600"
        :height="600"
        :modelPath="preferencesStore.currentModelPath || avatarStore.modelPath"
        :animationParams="chatStore.currentAnimationParams"
        :mouthOpenY="effectiveMouthOpenY"
      />
    </div>

    <ChatPanel />
    <InputBar />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSessionStore } from '../stores/session'
import { useAvatarStore } from '../stores/avatar'
import { useChatStore } from '../stores/chat'
import { usePreferencesStore } from '../stores/preferences'
import { useSignaling } from '../composables/useSignaling'
import { useRtcClient } from '../composables/useRtcClient'
import { useAudioAnalyzer } from '../composables/useAudioAnalyzer'
import Live2DCanvas from '../components/Live2DCanvas.vue'
import ChatPanel from '../components/ChatPanel.vue'
import InputBar from '../components/InputBar.vue'

const sessionStore = useSessionStore()
const avatarStore = useAvatarStore()
const chatStore = useChatStore()
const preferencesStore = usePreferencesStore()
const signaling = useSignaling()
const rtcClient = useRtcClient()
const { connect, stop, destroy: destroyAnalyzer, mouthOpenY, ensureAudioContext } = useAudioAnalyzer()

const fallbackMouthOpenY = ref(0)
const effectiveMouthOpenY = computed(() =>
  mouthOpenY.value > 0.02 ? mouthOpenY.value : fallbackMouthOpenY.value
)

let audioElement = null
let fallbackTimer = null

const selectedVoiceId = computed({
  get: () => preferencesStore.currentVoiceId,
  set: value => preferencesStore.setVoice(value)
})

const selectedModelPath = computed({
  get: () => preferencesStore.currentModelPath,
  set: value => preferencesStore.setModel(value)
})

function unlockAudio() {
  try {
    ensureAudioContext()
  } catch (error) {
    console.warn('[Audio] failed to unlock audio context', error)
  }
}

function clearFallbackAnimation() {
  if (fallbackTimer) {
    clearInterval(fallbackTimer)
    fallbackTimer = null
  }
  fallbackMouthOpenY.value = 0
}

function startTextFallback(text) {
  clearFallbackAnimation()

  const chars = Array.from(text || '')
  if (chars.length === 0) return

  let index = 0
  fallbackTimer = setInterval(() => {
    const char = chars[index % chars.length]
    fallbackMouthOpenY.value = /[，。！？、\s]/.test(char) ? 0 : 0.2 + (index % 3) * 0.18
    index += 1

    if (index >= chars.length * 2) {
      clearFallbackAnimation()
    }
  }, 90)
}

async function playAssistantAudio(base64, text) {
  clearFallbackAnimation()
  stop()

  if (audioElement) {
    audioElement.pause()
    audioElement.src = ''
    audioElement = null
  }

  if (!base64) {
    console.warn('[Audio] no audioBase64 returned, using fallback mouth animation')
    startTextFallback(text)
    return
  }

  audioElement = new Audio(`data:audio/wav;base64,${base64}`)
  audioElement.preload = 'auto'
  audioElement.crossOrigin = 'anonymous'
  audioElement.volume = 1
  audioElement.muted = false

  audioElement.addEventListener(
    'ended',
    () => {
      console.log('[Audio] playback ended')
      stop()
      fallbackMouthOpenY.value = 0
    },
    { once: true }
  )

  audioElement.addEventListener(
    'error',
    (event) => {
      console.error('[Audio] playback error, falling back to text animation', event)
      stop()
      startTextFallback(text)
    },
    { once: true }
  )

  try {
    await ensureAudioContext()
    await connect(audioElement)
    console.log('[Audio] playback starting', {
      bytesBase64Length: base64.length,
      srcPrefix: audioElement.src.slice(0, 32)
    })
    await audioElement.play()
  } catch (error) {
    console.error('[Audio] play() failed, likely blocked by autoplay policy', error)
    stop()
    startTextFallback(text)
  }
}

watch(
  () => chatStore.messages.length,
  () => {
    const msgs = chatStore.messages
    if (msgs.length === 0) return

    const last = msgs[msgs.length - 1]
    if (last.role !== 'ASSISTANT') return

    playAssistantAudio(chatStore.currentAudioBase64, last.text)
  }
)

watch(
  () => preferencesStore.currentModelPath,
  value => {
    if (value) {
      avatarStore.setModelPath(value)
    }
  },
  { immediate: true }
)

onMounted(async () => {
  await sessionStore.initSession()
  await preferencesStore.load()
  await avatarStore.loadAvatars()
  avatarStore.setModelPath(preferencesStore.currentModelPath)
  signaling.connect(sessionStore.sessionId)
  window.addEventListener('pointerdown', unlockAudio, { passive: true })
})

onUnmounted(() => {
  clearFallbackAnimation()
  stop()
  destroyAnalyzer()
  if (audioElement) {
    audioElement.pause()
    audioElement.src = ''
  }
  signaling.disconnect()
  rtcClient.close()
  window.removeEventListener('pointerdown', unlockAudio)
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
  position: relative;
}

.runtime-switchers {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 100;
  display: flex;
  gap: 8px;
}

.runtime-switchers .el-select {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
  border-radius: 4px;
}
</style>
