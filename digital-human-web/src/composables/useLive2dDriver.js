import { ref } from 'vue'

export function useLive2dDriver() {
  const modelRef = ref(null)
  const idleMode = ref(true)
  const currentParams = ref({})
  const targetParams = ref({})
  const transitionProgress = ref(1.0)

  let animationFrameId = null
  let lastFrameTime = 0

  const PARAM_MAP = {
    ParamMouthOpenY: 'ParamMouthOpenY',
    ParamMouthForm: 'ParamMouthForm',
    ParamEyeOpen: 'ParamEyeLOpen',
    ParamBrowY: 'ParamBrowY',
    ParamAngry: 'ParamAngry',
    ParamHappy: 'ParamHappy',
    ParamSad: 'ParamSad',
    ParamSurprise: 'ParamSurprise'
  }

  async function init(canvas, modelPath) {
    // Cubism SDK initialization (actual API depends on SDK version)
    // const { Live2DModel } = await import('@/lib/live2dcubismcore.min.js')
    // modelRef.value = await Live2DModel.from(modelPath)
    // modelRef.value.setCanvas(canvas)
    startRenderLoop()
  }

  function startRenderLoop() {
    const render = (timestamp) => {
      const deltaTime = timestamp - lastFrameTime
      lastFrameTime = timestamp
      updateTransition(deltaTime)
      applyParams()
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
    const t = transitionProgress.value
    const result = {}
    for (const key of Object.keys(PARAM_MAP)) {
      const cur = currentParams.value[key] || 0
      const tar = (targetParams.value[key] !== undefined)
        ? targetParams.value[key]
        : cur
      result[key] = cur + (tar - cur) * t
    }
    currentParams.value = result
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
    currentParams.value = {}
    targetParams.value = {}
  }

  // Idle animation state
  let idleStartTime = Date.now()
  const IDLE_BLINK_INTERVAL_MIN = 3000
  const IDLE_BLINK_INTERVAL_MAX = 6000
  let nextBlinkTime = IDLE_BLINK_INTERVAL_MIN
  let isBlinking = false
  let blinkProgress = 0

  function getIdleParams() {
    const now = Date.now()
    const params = {}

    const breathTime = (now % 4000) / 4000
    params.ParamBreath = Math.sin(breathTime * Math.PI * 2) * 0.3 + 0.5

    if (!isBlinking && now - idleStartTime > nextBlinkTime) {
      isBlinking = true
      blinkProgress = 0
    }

    if (isBlinking) {
      blinkProgress += 0.05
      if (blinkProgress < 0.3) {
        params.ParamEyeLOpen = 1 - (blinkProgress / 0.3)
      } else if (blinkProgress < 0.4) {
        params.ParamEyeLOpen = 0
      } else if (blinkProgress < 0.7) {
        params.ParamEyeLOpen = (blinkProgress - 0.4) / 0.3
      } else {
        params.ParamEyeLOpen = 1
        isBlinking = false
        nextBlinkTime = IDLE_BLINK_INTERVAL_MIN +
          Math.random() * (IDLE_BLINK_INTERVAL_MAX - IDLE_BLINK_INTERVAL_MIN)
        idleStartTime = now
      }
      params.ParamEyeROpen = params.ParamEyeLOpen
    }

    return params
  }

  return { init, setParams, setIdleMode, currentParams, idleMode, destroy, getIdleParams }
}
