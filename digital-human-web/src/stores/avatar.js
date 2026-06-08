import { defineStore } from 'pinia'

export const useAvatarStore = defineStore('avatar', {
  state: () => ({
    modelPath: '/models/default.model3.json',
    ttsVoiceId: 'zh-CN-XiaoxiaoNeural',
    speechSpeed: 1.0,
    pitch: 0
  }),
  actions: {
    setModelPath(path) {
      this.modelPath = path
    },
    setTtsConfig(config) {
      if (config.voice_id) this.ttsVoiceId = config.voice_id
      if (config.speed) this.speechSpeed = parseFloat(config.speed)
      if (config.pitch) this.pitch = parseInt(config.pitch)
    }
  }
})
