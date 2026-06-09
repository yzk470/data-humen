<template>
  <div class="chat-view">
    <div class="main-stage">
      <div class="avatar-switcher">
        <el-dropdown @command="onAvatarSwitch" trigger="click">
          <el-button type="default" size="small">
            🎭 切换形象
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="av in avatarStore.avatars"
                :key="av.id"
                :command="av.id"
                :class="{ 'is-active': av.id === avatarStore.currentAvatarId }"
              >
                {{ av.name }}{{ av.id === avatarStore.currentAvatarId ? ' ✓' : '' }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <Live2DCanvas :width="600" :height="600" :modelPath="avatarStore.modelPath" />
    </div>
    <ChatPanel />
    <InputBar />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useSessionStore } from '../stores/session'
import { useAvatarStore } from '../stores/avatar'
import { useSignaling } from '../composables/useSignaling'
import { useRtcClient } from '../composables/useRtcClient'
import Live2DCanvas from '../components/Live2DCanvas.vue'
import ChatPanel from '../components/ChatPanel.vue'
import InputBar from '../components/InputBar.vue'

const sessionStore = useSessionStore()
const avatarStore = useAvatarStore()
const signaling = useSignaling()
const rtcClient = useRtcClient()

function onAvatarSwitch(avatarId) {
  avatarStore.switchAvatar(avatarId)
}

onMounted(async () => {
  await sessionStore.initSession()
  signaling.connect(sessionStore.sessionId)
  await avatarStore.loadAvatars()
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
  position: relative;
}
.avatar-switcher {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 100;
}
.avatar-switcher .el-button {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
}
</style>
