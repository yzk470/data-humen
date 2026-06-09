import { ref } from 'vue'
import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display'

export function useLive2dDriver() {
  const app = ref(null)
  const model = ref(null)
  const idleMode = ref(true)
  const currentParams = ref({})
  const targetParams = ref({})
  const transitionProgress = ref(1.0)

  let animationFrameId = null
  let lastFrameTime = 0

  const PARAM_MAP = {
    ParamMouthOpenY: 'ParamMouthOpenY',
    ParamEyeLOpen: 'ParamEyeLOpen',
    ParamEyeROpen: 'ParamEyeROpen',
    ParamBrowY: 'ParamBrowY',
    ParamHappy: 'ParamHappy',
    ParamSad: 'ParamSad',
    ParamSurprise: 'ParamSurprise',
    ParamAngry: 'ParamAngry',
    ParamBreath: 'ParamBreath'
  }

  async function init(canvas, modelPath) {
    destroy()

    app.value = new PIXI.Application({
      view: canvas,
      width: canvas.width,
      height: canvas.height,
      backgroundAlpha: 0,
      antialias: true,
      resolution: window.devicePixelRatio || 1,
      autoDensity: true
    })

    try {
      model.value = await Live2DModel.from(modelPath)
      // 居中缩放模型
      model.value.scale.set(0.5)
      model.value.x = canvas.width / 2
      model.value.y = canvas.height / 2
      model.value.anchor.set(0.5, 0.5)

      app.value.stage.addChild(model.value)
      startRenderLoop()
    } catch (e) {
      console.error('Live2D 模型加载失败:', e, modelPath)
    }
  }

  function startRenderLoop() {
    const render = (timestamp) => {
      const deltaTime = timestamp - lastFrameTime
      lastFrameTime = timestamp

      if (model.value) {
        updateTransition(deltaTime)
        applyParams()
      }

      // PIXI handles its own render loop
      animationFrameId = requestAnimationFrame(render)
    }
    animationFrameId = requestAnimationFrame(render)
  }

  function updateTransition(deltaTime) {
    if (transitionProgress.value >= 1.0) return
    const speed = 0.003
    transitionProgress.value = Math.min(1.0, transitionProgress.value + deltaTime * speed)
  }

  function applyParams() {
    if (!model.value) return
    const t = transitionProgress.value
    for (const key of Object.keys(PARAM_MAP)) {
      const paramName = PARAM_MAP[key]
      const cur = currentParams.value[key] || 0
      const tar = (targetParams.value[key] !== undefined)
        ? targetParams.value[key]
        : cur
      const val = cur + (tar - cur) * t
      currentParams.value[key] = val

      try {
        model.value.internalModel.coreModel.setParameterValueById(
          paramName, val)
      } catch (e) {
        // 参数可能不存在于此模型
      }
    }
  }

  function setParams(params) {
    targetParams.value = { ...params }
    transitionProgress.value = 0.0
    idleMode.value = false
  }

  function setIdleMode(idle) {
    idleMode.value = idle
  }

  function destroy() {
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
    if (model.value) {
      model.value.destroy()
      model.value = null
    }
    if (app.value) {
      app.value.destroy(false, { children: true })
      app.value = null
    }
    currentParams.value = {}
    targetParams.value = {}
  }

  return { init, setParams, setIdleMode, currentParams, idleMode, destroy }
}
