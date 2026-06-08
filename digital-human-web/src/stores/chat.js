import { defineStore } from 'pinia'
import { sendTextMessage } from '../services/api'
import { useSessionStore } from './session'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    currentEmotion: null,
    currentAnimationParams: {},
    sending: false
  }),
  actions: {
    async sendText(text) {
      const sessionStore = useSessionStore()
      if (!sessionStore.sessionId) return

      this.messages.push({ role: 'USER', text })
      this.sending = true

      try {
        const { data } = await sendTextMessage(sessionStore.sessionId, text)
        if (data.code === 200) {
          const result = data.data
          this.messages.push({
            role: 'ASSISTANT',
            text: result.text,
            emotion: result.emotion
          })
          this.currentEmotion = result.emotion
          this.currentAnimationParams = result.animationParams
          return result
        }
      } finally {
        this.sending = false
      }
    },
    clearMessages() {
      this.messages = []
    }
  }
})
