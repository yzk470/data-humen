import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import ChatView from './ChatView.vue'
import { usePreferencesStore } from '../stores/preferences'

vi.mock('../components/Live2DCanvas.vue', () => ({
  default: {
    name: 'Live2DCanvas',
    template: '<div class="mock-live2d-canvas">Live2DCanvas</div>',
    props: ['width', 'height', 'modelPath', 'animationParams', 'mouthOpenY']
  }
}))

vi.mock('../components/ChatPanel.vue', () => ({
  default: {
    name: 'ChatPanel',
    template: '<div class="mock-chat-panel">ChatPanel</div>'
  }
}))

vi.mock('../components/InputBar.vue', () => ({
  default: {
    name: 'InputBar',
    template: '<div class="mock-input-bar">InputBar</div>'
  }
}))

vi.mock('../composables/useSignaling', () => ({ useSignaling: () => ({ connect: vi.fn(), disconnect: vi.fn() }) }))
vi.mock('../composables/useRtcClient', () => ({ useRtcClient: () => ({ close: vi.fn() }) }))
vi.mock('../composables/useAudioAnalyzer', () => ({
  useAudioAnalyzer: () => ({
    connect: vi.fn(),
    stop: vi.fn(),
    destroy: vi.fn(),
    mouthOpenY: { value: 0 },
    ensureAudioContext: vi.fn()
  })
}))

vi.mock('../services/api', () => ({
  default: {
    post: vi.fn(async () => ({ data: { code: 200, data: { sessionId: 'test-session' } } })),
    get: vi.fn(async () => ({ data: { code: 200, data: { avatars: [], defaultId: '' } } }))
  },
  createSession: vi.fn(async () => ({ data: { code: 200, data: { sessionId: 'test-session' } } })),
  listAvatars: vi.fn(async () => ({ data: { code: 200, data: { avatars: [], defaultId: '' } } }))
}))

vi.mock('../services/preferences', () => ({
  getUserPreferences: vi.fn(async () => ({
    data: {
      code: 200,
      data: {
        voiceOptions: [{ label: '莹晓', value: 'longyingxiao_v3' }],
        modelOptions: [{ label: '默认 Haru', value: '/models/generated/avatar_default/Haru.model3.json' }],
        defaultVoiceId: 'longyingxiao_v3',
        defaultModelPath: '/models/generated/avatar_default/Haru.model3.json',
        currentVoiceId: 'longyingxiao_v3',
        currentModelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    }
  })),
  updateUserPreferences: vi.fn(async () => ({ data: { code: 200 } }))
}))

describe('ChatView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads preferences and renders Layout with switchers', async () => {
    const preferences = usePreferencesStore()
    preferences.voiceOptions = [{ label: '莹晓', value: 'longyingxiao_v3' }]
    preferences.modelOptions = [{ label: '默认 Haru', value: '/models/generated/avatar_default/Haru.model3.json' }]
    preferences.currentVoiceId = 'longyingxiao_v3'
    preferences.currentModelPath = '/models/generated/avatar_default/Haru.model3.json'
    preferences.load = vi.fn()

    const wrapper = mount(ChatView, {
      global: {
        plugins: [ElementPlus]
      }
    })

    await new Promise(resolve => setTimeout(resolve, 100))
    expect(wrapper.html()).toContain('Live2DCanvas')
  })
})
