import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePreferencesStore } from './preferences'

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

describe('preferences store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads current preferences and exposes current model path', async () => {
    const store = usePreferencesStore()
    await store.load()
    expect(store.currentVoiceId).toBe('longyingxiao_v3')
    expect(store.currentModelPath).toBe('/models/generated/avatar_default/Haru.model3.json')
  })
})
