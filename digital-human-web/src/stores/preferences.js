import { defineStore } from 'pinia'
import { getUserPreferences, updateUserPreferences } from '../services/preferences'

export const usePreferencesStore = defineStore('preferences', {
  state: () => ({
    voiceOptions: [],
    modelOptions: [],
    defaultVoiceId: '',
    defaultModelPath: '',
    currentVoiceId: '',
    currentModelPath: '',
    loaded: false,
    saving: false
  }),
  actions: {
    async load() {
      const { data } = await getUserPreferences()
      if (data.code !== 200) return
      Object.assign(this, data.data, { loaded: true })
    },
    async saveCurrent() {
      this.saving = true
      try {
        await updateUserPreferences({
          voiceId: this.currentVoiceId,
          modelPath: this.currentModelPath
        })
      } finally {
        this.saving = false
      }
    },
    async setVoice(value) {
      this.currentVoiceId = value
      await this.saveCurrent()
    },
    async setModel(value) {
      this.currentModelPath = value
      await this.saveCurrent()
    }
  }
})
