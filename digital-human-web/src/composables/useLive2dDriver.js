import { ref } from 'vue'

export function useLive2dDriver() {
  const app = ref(null)
  const model = ref(null)
  const idleMode = ref(true)
  const error = ref(null)
  const loading = ref(false)

  let PIXI = null
  let Live2DModel = null

  async function ensureSDK() {
    if (PIXI && Live2DModel) return true
    try {
      const pixiModule = await import('pixi.js')
      PIXI = pixiModule
      window.PIXI = PIXI

      const l2dModule = await import('pixi-live2d-display')
      Live2DModel = l2dModule.Live2DModel
      return true
    } catch (e) {
      console.error('Live2D SDK 加载失败:', e)
      error.value = 'SDK加载失败: ' + e.message
      return false
    }
  }

  async function init(canvas, modelPath) {
    destroy()
    error.value = null
    loading.value = true

    try {
      const ok = await ensureSDK()
      if (!ok) {
        loading.value = false
        return
      }

      app.value = new PIXI.Application({
        view: canvas,
        width: canvas.width,
        height: canvas.height,
        backgroundAlpha: 0,
        antialias: true,
        resolution: window.devicePixelRatio || 1,
        autoDensity: true
      })

      model.value = await Live2DModel.from(modelPath, {
        autoUpdate: true,
        autoInteract: false
      })

      if (model.value) {
        const mw = model.value.internalModel.width || 600
        const mh = model.value.internalModel.height || 600
        const scale = Math.min(canvas.width / mw * 0.8, canvas.height / mh * 0.8)
        model.value.scale.set(scale)
        model.value.x = canvas.width / 2
        model.value.y = canvas.height / 2
        model.value.anchor.set(0.5, 0.5)
        app.value.stage.addChild(model.value)
      }

      loading.value = false
    } catch (e) {
      console.error('Live2D 模型加载失败:', modelPath, e)
      error.value = e.message
      loading.value = false
      if (model.value) { try { model.value.destroy() } catch (_) {} }
      if (app.value) { try { app.value.destroy(false, { children: true }) } catch (_) {} }
      app.value = null
      model.value = null
    }
  }

  function setParams(params) {
    if (!model.value) return
    idleMode.value = false
    try {
      const cm = model.value.internalModel.coreModel
      for (const [key, val] of Object.entries(params)) {
        try { cm.setParameterValueById(key, val) } catch (_) {}
      }
    } catch (e) { /* ignore */ }
  }

  function setIdleMode(idle) {
    idleMode.value = idle
  }

  function destroy() {
    if (model.value) {
      try { model.value.destroy() } catch (_) {}
      model.value = null
    }
    if (app.value) {
      try { app.value.destroy(false, { children: true }) } catch (_) {}
      app.value = null
    }
  }

  return { init, setParams, setIdleMode, idleMode, error, loading, destroy }
}
