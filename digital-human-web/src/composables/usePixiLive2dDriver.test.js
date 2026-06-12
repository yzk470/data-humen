import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const addChild = vi.fn()
  const removeChildren = vi.fn()
  const destroyApp = vi.fn()
  const registerTicker = vi.fn()
  const modelScaleSet = vi.fn()
  const modelAnchorSet = vi.fn()
  const createBuffer = vi.fn()
  const deleteBuffer = vi.fn()

  const gl = {
    createBuffer,
    deleteBuffer
  }

  const internalEmit = vi.fn()
  const mockModel = {
    width: 480,
    height: 480,
    scale: { set: modelScaleSet },
    anchor: { set: modelAnchorSet },
    internalModel: {
      emit: internalEmit,
      update: vi.fn(),
      coreModel: {
        setParamFloat: vi.fn(),
        getParamIndex: vi.fn(() => 0),
        update: vi.fn()
      }
    }
  }

  const Application = vi.fn(function Application(options) {
    this.options = options
    this.stage = {
      addChild,
      removeChildren
    }
    this.renderer = { gl }
    this.destroy = destroyApp
  })

  return {
    addChild,
    removeChildren,
    destroyApp,
    registerTicker,
    modelScaleSet,
    modelAnchorSet,
    createBuffer,
    deleteBuffer,
    gl,
    mockModel,
    Application
  }
})

vi.mock('pixi.js', () => ({
  Application: mocks.Application,
  Ticker: {}
}))

vi.mock('pixi-live2d-display/cubism2', () => ({
  Live2DModel: {
    registerTicker: mocks.registerTicker,
    from: vi.fn(async () => mocks.mockModel)
  }
}))

import { usePixiLive2dDriver } from './usePixiLive2dDriver'

describe('usePixiLive2dDriver', () => {
  const originalWebGLBuffer = globalThis.WebGLBuffer

  beforeEach(() => {
    vi.clearAllMocks()
    globalThis.WebGLBuffer = undefined
    mocks.mockModel.internalModel.update = vi.fn()
    mocks.mockModel.internalModel.coreModel.setParamFloat = vi.fn()
    mocks.mockModel.internalModel.coreModel.getParamIndex = vi.fn(() => 0)
    mocks.mockModel.internalModel.coreModel.update = vi.fn()

    const bufferCtor = function MockWebGLBuffer() {}
    const buffer = Object.create(bufferCtor.prototype)
    Object.defineProperty(buffer, 'constructor', {
      value: bufferCtor
    })

    mocks.createBuffer.mockReturnValue(buffer)
  })

  afterEach(() => {
    globalThis.WebGLBuffer = originalWebGLBuffer
  })

  it('hydrates missing WebGLBuffer global from the active GL context', async () => {
    const canvas = document.createElement('canvas')
    const parent = document.createElement('div')
    parent.appendChild(canvas)

    Object.defineProperty(canvas, 'clientWidth', { value: 600, configurable: true })
    Object.defineProperty(canvas, 'clientHeight', { value: 600, configurable: true })

    const driver = usePixiLive2dDriver()

    await driver.init(canvas, '/models/generated/bronya/model.json')

    expect(mocks.createBuffer).toHaveBeenCalledTimes(1)
    expect(mocks.deleteBuffer).toHaveBeenCalledTimes(1)
    expect(globalThis.WebGLBuffer).toBe(mocks.createBuffer.mock.results[0].value.constructor)
    expect(driver.error.value).toBeNull()
    expect(driver.loaded.value).toBe(true)
  })

  it('initializes the Pixi app with a transparent background', async () => {
    const canvas = document.createElement('canvas')
    const parent = document.createElement('div')
    parent.appendChild(canvas)

    Object.defineProperty(canvas, 'clientWidth', { value: 600, configurable: true })
    Object.defineProperty(canvas, 'clientHeight', { value: 600, configurable: true })

    const driver = usePixiLive2dDriver()

    await driver.init(canvas, '/models/generated/bronya/model.json')

    expect(mocks.Application).toHaveBeenCalledWith(
      expect.objectContaining({
        backgroundAlpha: 0,
        clearBeforeRender: true,
        transparent: true
      })
    )
  })

  it('applies animation params to the Cubism 2 core model', async () => {
    const canvas = document.createElement('canvas')
    const parent = document.createElement('div')
    parent.appendChild(canvas)

    Object.defineProperty(canvas, 'clientWidth', { value: 600, configurable: true })
    Object.defineProperty(canvas, 'clientHeight', { value: 600, configurable: true })

    const driver = usePixiLive2dDriver()

    await driver.init(canvas, '/models/generated/bronya/model.json')
    driver.setParams({
      ParamMouthOpenY: 0.35,
      ParamAngleX: 12,
      ParamEyeLOpen: 0.2
    })

    expect(mocks.mockModel.internalModel.coreModel.setParamFloat).toHaveBeenCalledWith(
      'PARAM_MOUTH_OPEN_Y',
      0.35
    )
    expect(mocks.mockModel.internalModel.coreModel.setParamFloat).toHaveBeenCalledWith(
      'PARAM_ANGLE_X',
      12
    )
    expect(mocks.mockModel.internalModel.coreModel.setParamFloat).toHaveBeenCalledWith(
      'PARAM_EYE_L_OPEN',
      0.2
    )
  })

  it('applies params before internal update so saveParam/loadParam preserve them', async () => {
    const canvas = document.createElement('canvas')
    const parent = document.createElement('div')
    parent.appendChild(canvas)

    Object.defineProperty(canvas, 'clientWidth', { value: 600, configurable: true })
    Object.defineProperty(canvas, 'clientHeight', { value: 600, configurable: true })

    const driver = usePixiLive2dDriver()
    const originalUpdate = mocks.mockModel.internalModel.update

    await driver.init(canvas, '/models/generated/bronya/model.json')
    driver.setParams({ ParamMouthOpenY: 0.42 })
    // 模拟 PIXI 每帧 tick：先应用参数，再执行 originalUpdate
    mocks.mockModel.internalModel.update(16, 16)

    // 参数通过 setParams + customUpdate 的 applyParamsToCoreModel 两次设置
    expect(mocks.mockModel.internalModel.coreModel.setParamFloat).toHaveBeenCalledWith(
      'PARAM_MOUTH_OPEN_Y',
      0.42
    )
    // originalUpdate 内部会调用 coreModel.update()，我们不再额外调用
    expect(originalUpdate).toHaveBeenCalledWith(16, 16)
  })

  it('drives both mouth axes from mouth openness for Cubism 2 models', async () => {
    const canvas = document.createElement('canvas')
    const parent = document.createElement('div')
    parent.appendChild(canvas)

    Object.defineProperty(canvas, 'clientWidth', { value: 600, configurable: true })
    Object.defineProperty(canvas, 'clientHeight', { value: 600, configurable: true })

    const driver = usePixiLive2dDriver()

    await driver.init(canvas, '/models/generated/bronya/model.json')
    driver.setParams({ ParamMouthOpenY: 0.5 })

    expect(mocks.mockModel.internalModel.coreModel.setParamFloat).toHaveBeenCalledWith(
      'PARAM_MOUTH_OPEN_X',
      0.5
    )
  })

  it('keeps the provided canvas mounted when destroying the Pixi app', async () => {
    const canvas = document.createElement('canvas')
    const parent = document.createElement('div')
    parent.appendChild(canvas)

    Object.defineProperty(canvas, 'clientWidth', { value: 600, configurable: true })
    Object.defineProperty(canvas, 'clientHeight', { value: 600, configurable: true })

    const driver = usePixiLive2dDriver()

    await driver.init(canvas, '/models/generated/bronya/model.json')
    driver.destroy()

    expect(mocks.destroyApp).toHaveBeenCalledWith(
      false,
      expect.objectContaining({ children: true, texture: true })
    )
    expect(parent.contains(canvas)).toBe(true)
  })
})
