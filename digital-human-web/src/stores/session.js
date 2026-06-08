import { defineStore } from 'pinia'
import { createSession as apiCreateSession } from '../services/api'

export const useSessionStore = defineStore('session', {
  state: () => ({
    sessionId: null,
    status: null,
    loading: false
  }),
  actions: {
    async initSession() {
      this.loading = true
      try {
        const { data } = await apiCreateSession()
        if (data.code === 200) {
          this.sessionId = data.data.sessionId
          this.status = 'ACTIVE'
        }
      } finally {
        this.loading = false
      }
    },
    setStatus(status) {
      this.status = status
    }
  }
})
