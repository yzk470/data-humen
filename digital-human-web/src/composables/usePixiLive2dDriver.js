import { ref } from 'vue'
import * as PIXI from 'pixi.js'

let PIXI_APP = null
let LIVE2D_MODEL = null
let ACTIVE_PARAMS = {}

const PARAM_MAP = {
  ParamMouthOpenY: 'PARAM_MOUTH_OPEN_Y',
  ParamAngleX: 'PARAM_ANGLE_X',
  ParamAngleY: 'PARAM_ANGLE_Y',
  ParamAngleZ: 'PARAM_ANGLE_Z',
  ParamBodyAngleX: 'PARAM_BODY_ANGLE_X',
  ParamEyeBallX: 'PARAM_EYE_BALL_X',
  ParamEyeBallY: 'PARAM_EYE_BALL_Y',
  ParamEyeLOpen: 'PARAM_EYE_L_OPEN',
  ParamEyeROpen: 'PARAM_EYE_R_OPEN'
}

// Cubism2 模型常见口型参数名（按优先级排序）
const MOUTH_PARAM_CANDIDATES = [
  'PARAM_MOUTH_OPEN_Y',
  'PARAM_MOUTH_OPEN_X',
  'PARAM_MOUTH_OPEN',
  'PARAM_MOUTH_FORM',
  'PARAM_MOUTH',
  'PARAM_M_OPEN_Y',
  'PARAM_M_OPEN_X',
  'PARAM_M_OPEN'
]

// 模型加载时探测到的有效口型参数名
let DISCOVERED_MOUTH_PARAM = null

function discoverModelParams(coreModel) {
  if (!coreModel) return { params: [], mouthParams: [] }

  try {
    const allCandidates = [
      ...MOUTH_PARAM_CANDIDATES,
      'PARAM_ANGLE_X', 'PARAM_ANGLE_Y', 'PARAM_ANGLE_Z',
      'PARAM_EYE_L_OPEN', 'PARAM_EYE_R_OPEN',
      'PARAM_EYE_BALL_X', 'PARAM_EYE_BALL_Y',
      'PARAM_BODY_ANGLE_X', 'PARAM_BREATH',
      'PARAM_BUST_X', 'PARAM_BUST_Y',
      'PARAM_HAIR_FRONT', 'PARAM_HAIR_SIDE',
      'PARAM_CHEEK'
    ]

    const params = []
    const mouthParams = []

    for (const name of allCandidates) {
      try {
        const idx = coreModel.getParamIndex(name)
        if (typeof idx === 'number' && idx >= 0) {
          params.push(name)
          if (/MOUTH/i.test(name)) {
            mouthParams.push(name)
          }
        }
      } catch (_) { /* skip */ }
    }

    return { params, mouthParams }
  } catch (_) {
    return { params: [], mouthParams: [] }
  }
}

function applyParamsToCoreModel(params) {
  if (!LIVE2D_MODEL?.internalModel?.coreModel) return

  const coreModel = LIVE2D_MODEL.internalModel.coreModel
  for (const [key, value] of Object.entries(params || {})) {
    if (typeof value !== 'number' || Number.isNaN(value)) continue

    if (key === 'ParamMouthOpenY') {
      if (DISCOVERED_MOUTH_PARAM) {
        for (const name of DISCOVERED_MOUTH_PARAM) {
          try { coreModel.setParamFloat(name, value) } catch (_) { /* skip */ }
        }
        continue
      }

      for (const name of MOUTH_PARAM_CANDIDATES) {
        const idx = coreModel.getParamIndex?.(name)
        if (typeof idx === 'number' && idx >= 0) {
          try { coreModel.setParamFloat(name, value) } catch (_) { /* skip */ }
        }
      }
      continue
    }

    const paramId = PARAM_MAP[key] || key
    try {
      coreModel.setParamFloat(paramId, value)
    } catch (_) {
      // 忽略当前模型不支持的参数
    }
  }
}

function ensureWebGLBufferGlobal(gl) {
  if (typeof globalThis.WebGLBuffer === 'function') return
  if (!gl?.createBuffer) return

  const probe = gl.createBuffer()
  const ctor = probe?.constructor

  if (typeof ctor === 'function') {
    globalThis.WebGLBuffer = ctor
  }

  if (probe && gl.deleteBuffer) {
    gl.deleteBuffer(probe)
  }
}

export function usePixiLive2dDriver() {
  const error = ref(null)
  const loaded = ref(false)

  async function init(canvas, modelPath) {
    destroy()
    loaded.value = false
    error.value = null

    try {
      const { Live2DModel } = await import('pixi-live2d-display/cubism2')
      Live2DModel.registerTicker(PIXI.Ticker)

      const app = new PIXI.Application({
        view: canvas,
        width: canvas.clientWidth,
        height: canvas.clientHeight,
        transparent: true,
        backgroundAlpha: 0,
        clearBeforeRender: true,
        autoStart: true,
        resizeTo: canvas.parentElement || undefined
      })

      ensureWebGLBufferGlobal(app.renderer?.gl)

      const model = await Live2DModel.from(modelPath)

      // 通过拦截 emit("afterMotionUpdate") 在 motion 更新后、saveParam/update 前注入口型参数
      if (model.internalModel) {
        const origEmit = model.internalModel.emit.bind(model.internalModel)
        model.internalModel.emit = (event) => {
          if (event === 'afterMotionUpdate') {
            applyParamsToCoreModel(ACTIVE_PARAMS)
          }
          return origEmit(event)
        }
      }

      const targetW = canvas.clientWidth * 0.8
      const targetH = canvas.clientHeight * 0.8
      const scale = Math.min(targetW / model.width, targetH / model.height, 0.6)
      model.scale.set(scale)
      model.x = canvas.clientWidth / 2
      model.y = canvas.clientHeight * 0.55
      model.anchor.set(0.5, 0.5)

      app.stage.addChild(model)

      PIXI_APP = app
      LIVE2D_MODEL = model
      loaded.value = true

      // 探测模型支持的口型参数
      const { params, mouthParams } = discoverModelParams(model.internalModel.coreModel)
      if (mouthParams.length > 0) {
        DISCOVERED_MOUTH_PARAM = mouthParams
        console.log('[PixiLive2D] Cubism2 model loaded', {
          modelPath,
          width: model.width,
          height: model.height,
          mouthParams
        })
      } else {
        DISCOVERED_MOUTH_PARAM = null
        console.warn('[PixiLive2D] Cubism2 model loaded - no mouth params detected', {
          modelPath,
          detectedParams: params
        })
      }
    } catch (e) {
      console.error('[PixiLive2D] init failed', e)
      error.value = e?.message || 'PixiLive2D init failed'
      destroy()
    }
  }

  function setParams(_params) {
    ACTIVE_PARAMS = { ...(_params || {}) }
    applyParamsToCoreModel(ACTIVE_PARAMS)
  }

  function destroy() {
    ACTIVE_PARAMS = {}
    DISCOVERED_MOUTH_PARAM = null
    if (LIVE2D_MODEL) {
      try { LIVE2D_MODEL.destroy?.() } catch (_) { /* ignore */ }
      LIVE2D_MODEL = null
    }
    if (PIXI_APP) {
      try {
        PIXI_APP.stage?.removeChildren()
        PIXI_APP.destroy?.(false, { children: true, texture: true })
      } catch (_) { /* ignore */ }
      PIXI_APP = null
    }
    loaded.value = false
  }

  return { init, setParams, destroy, error, loaded }
}
