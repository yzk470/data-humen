<template>
  <div class="chat-panel">
    <div class="messages" ref="messagesContainer">
      <div
        v-for="(msg, idx) in chatStore.messages"
        :key="idx"
        :class="['message', msg.role === 'USER' ? 'user' : 'assistant']"
      >
        <div class="bubble">{{ msg.text }}</div>
        <span v-if="msg.emotion" class="emotion-tag">{{ msg.emotion }}</span>
      </div>
      <div v-if="chatStore.sending" class="message assistant">
        <div class="bubble typing">...</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { watch, ref, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'

const chatStore = useChatStore()
const messagesContainer = ref(null)

watch(() => chatStore.messages.length, async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
})
</script>

<style scoped>
.chat-panel {
  position: absolute;
  bottom: 80px;
  right: 20px;
  width: 320px;
  max-height: 400px;
  overflow-y: auto;
  background: rgba(255,255,255,0.9);
  border-radius: 12px;
  padding: 12px;
}
.message { margin-bottom: 8px; }
.message.user .bubble { background: #409EFF; color: white; text-align: right; }
.message.assistant .bubble { background: #f0f0f0; color: #333; }
.bubble { padding: 8px 12px; border-radius: 12px; max-width: 80%; display: inline-block; }
.emotion-tag { font-size: 11px; color: #999; display: block; margin-top: 2px; }
.typing { animation: pulse 1s infinite; }
@keyframes pulse { 0%, 100% { opacity: 0.5; } 50% { opacity: 1; } }
</style>
