import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const cubismDriver = {
  init: vi.fn(async () => {}),
  setParams: vi.fn(),
  destroy: vi.fn(),
  disposeFramework: vi.fn(),
  error: { value: null },
  loaded: { value: false }
}

const pixiDriver = {
  init: vi.fn(async () => {}),
  setParams: vi.fn(),
  destroy: vi.fn(),
  error: { value: null },
  loaded: { value: false }
}

vi.mock('../composables/useLive2dDriver', () => ({
  useLive2dDriver: () => cubismDriver
}))

vi.mock('../composables/usePixiLive2dDriver', () => ({
  usePixiLive2dDriver: () => pixiDriver
}))

import Live2DCanvas from './Live2DCanvas.vue'

describe('Live2DCanvas', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    cubismDriver.error.value = null
    pixiDriver.error.value = null
  })

  it('uses pixi driver for Cubism 2 model.json paths', async () => {
    mount(Live2DCanvas, {
      props: {
        modelPath: '/models/generated/bronya/model.json'
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    expect(pixiDriver.init).toHaveBeenCalledWith(
      expect.any(HTMLCanvasElement),
      '/models/generated/bronya/model.json'
    )
    expect(cubismDriver.init).not.toHaveBeenCalled()
  })

  it('uses Cubism framework driver for model3.json paths', async () => {
    mount(Live2DCanvas, {
      props: {
        modelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    expect(cubismDriver.init).toHaveBeenCalledWith(
      expect.any(HTMLCanvasElement),
      '/models/generated/avatar_default/Haru.model3.json'
    )
    expect(pixiDriver.init).not.toHaveBeenCalled()
  })

  it('destroys the previous driver before switching runtimes', async () => {
    const wrapper = mount(Live2DCanvas, {
      props: {
        modelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    await wrapper.setProps({
      modelPath: '/models/generated/bronya/model.json'
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    expect(cubismDriver.destroy).toHaveBeenCalledTimes(2)
    expect(pixiDriver.init).toHaveBeenCalledWith(
      expect.any(HTMLCanvasElement),
      '/models/generated/bronya/model.json'
    )
  })

  it('recreates the canvas when switching models', async () => {
    const wrapper = mount(Live2DCanvas, {
      props: {
        modelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    const firstCanvas = wrapper.find('canvas').element

    await wrapper.setProps({
      modelPath: '/models/generated/bronya/model.json'
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    const secondCanvas = wrapper.find('canvas').element

    expect(secondCanvas).not.toBe(firstCanvas)
  })

  it('forwards animation params and mouth openness to the active driver', async () => {
    mount(Live2DCanvas, {
      props: {
        modelPath: '/models/generated/bronya/model.json',
        animationParams: {
          ParamAngleX: 10,
          ParamEyeLOpen: 0.4
        },
        mouthOpenY: 0.5
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))

    expect(pixiDriver.setParams).toHaveBeenCalledWith({
      ParamAngleX: 10,
      ParamEyeLOpen: 0.4,
      ParamMouthOpenY: 0.5
    })
  })

  it('only disposes the Cubism framework when the canvas unmounts', async () => {
    const wrapper = mount(Live2DCanvas, {
      props: {
        modelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))
    wrapper.unmount()

    expect(cubismDriver.disposeFramework).toHaveBeenCalledTimes(1)
  })
})
