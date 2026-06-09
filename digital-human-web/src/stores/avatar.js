import { defineStore } from 'pinia'
import {
  uploadAvatar,
  listAdminAvatars,
  deleteAdminAvatar,
  listAvatars,
  switchSessionAvatar
} from '../services/api'
import { useSessionStore } from './session'

export const useAvatarStore = defineStore('avatar', {
  state: () => ({
    modelPath: '/models/base/haru.model3.json',
    avatars: [],
    defaultId: '',
    currentAvatarId: '',
    uploading: false
  }),

  actions: {
    /** 管理员：上传并生成新形象 */
    async uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH) {
      this.uploading = true
      try {
        const { data } = await uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH)
        if (data.code === 200) {
          await this.loadAdminAvatars()
          return data.data
        }
        throw new Error(data.message || '上传失败')
      } finally {
        this.uploading = false
      }
    },

    /** 管理员：加载形象库列表 */
    async loadAdminAvatars() {
      const { data } = await listAdminAvatars()
      if (data.code === 200) {
        this.avatars = data.data.avatars || []
        this.defaultId = data.data.defaultId || ''
      }
    },

    /** 管理员：删除形象 */
    async deleteAvatar(id) {
      const { data } = await deleteAdminAvatar(id)
      if (data.code === 200) {
        await this.loadAdminAvatars()
      }
      return data
    },

    /** 用户端：加载可用形象列表 */
    async loadAvatars() {
      const { data } = await listAvatars()
      if (data.code === 200) {
        this.avatars = data.data.avatars || []
        this.defaultId = data.data.defaultId || ''
        if (!this.currentAvatarId && this.defaultId) {
          this.currentAvatarId = this.defaultId
          const def = this.avatars.find(a => a.id === this.defaultId)
          if (def) this.modelPath = def.modelPath
        }
      }
    },

    /** 用户端：切换当前会话形象 */
    async switchAvatar(avatarId) {
      const sessionStore = useSessionStore()
      if (!sessionStore.sessionId) return

      const { data } = await switchSessionAvatar(sessionStore.sessionId, avatarId)
      if (data.code === 200) {
        this.currentAvatarId = avatarId
        this.modelPath = data.data.modelPath
      }
      return data
    },

    setModelPath(path) {
      this.modelPath = path
    }
  }
})
