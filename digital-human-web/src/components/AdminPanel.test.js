import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import AdminPanel from './AdminPanel.vue'

vi.mock('../services/api', () => ({
  adminApi: {
    get: vi.fn(async (url) => {
      if (url === '/config/prompt') return { data: { code: 200, data: { system_prompt: '你好' } } }
      if (url === '/config/tts-voice') return { data: { code: 200, data: { voice_id: 'v1', speed: '1.0', pitch: '0' } } }
      if (url === '/config/model') return { data: { code: 200, data: { live2d_model_path: '/m.model3.json' } } }
      return { data: { code: 200, data: {} } }
    })
  },
  detectFace: vi.fn()
}))

vi.mock('../services/preferences', () => ({
  getAdminPreferences: vi.fn(async () => ({
    data: {
      code: 200,
      data: {
        voiceOptions: [{ label: '莹晓', value: 'longyingxiao_v3' }],
        modelOptions: [{ label: '默认 Haru', value: '/models/generated/avatar_default/Haru.model3.json' }],
        defaultVoiceId: 'longyingxiao_v3',
        defaultModelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    }
  })),
  updateAdminPreferences: vi.fn(async () => ({ data: { code: 200 } }))
}))

const mockAvatarStore = {
  avatars: [{ id: '1', name: '默认', modelPath: '/models/generated/avatar_default/Haru.model3.json', thumbnailPath: '' }],
  loadAdminAvatars: vi.fn(),
  uploadAvatar: vi.fn(),
  deleteAvatar: vi.fn(),
  uploading: false
}

vi.mock('../stores/avatar', () => ({
  useAvatarStore: () => mockAvatarStore
}))

describe('AdminPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders preference management sections and loads config', async () => {
    const wrapper = mount(AdminPanel, {
      global: {
        plugins: [ElementPlus],
        stubs: {
          ImageCropper: true,
          'el-upload': true
        }
      }
    })
    // Wait for all async onMounted operations
    await new Promise(resolve => setTimeout(resolve, 200))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('音色管理')
    expect(wrapper.text()).toContain('形象管理')
  })
})
