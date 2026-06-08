<template>
  <div class="input-bar">
    <el-input
      v-model="text"
      placeholder="输入消息..."
      @keyup.enter="send"
      :disabled="chatStore.sending"
    >
      <template #append>
        <el-button @click="send" :loading="chatStore.sending">
          发送
        </el-button>
      </template>
    </el-input>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useChatStore } from '../stores/chat'

const chatStore = useChatStore()
const text = ref('')

async function send() {
  if (!text.value.trim()) return
  await chatStore.sendText(text.value.trim())
  text.value = ''
}
</script>

<style scoped>
.input-bar {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 480px;
}
</style>
