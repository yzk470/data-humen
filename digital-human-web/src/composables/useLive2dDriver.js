import { ref } from 'vue'
import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display'

// pixi-live2d-display 需要在 window 上暴露 PIXI
window.PIXI = PIXI

export function useLive2dDriver() {
  const app = ref(null)
  const model = ref(null)
  const idleMode = ref(true)
  const error = ref(null)

  async function init(canvas, modelPath) {
    destroy()
    error.value = null

    try {
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

      const scale = Math.min(
        canvas.width / (model.value.width || 600) * 0.8,
        canvas.height / (model.value.height || 600) * 0.8
      )
      model.value.scale.set(scale)
      model.value.x = canvas.width / 2
      model.value.y = canvas.height / 2
      model.value.anchor.set(0.5, 0.5)

      app.value.stage.addChild(model.value)
    } catch (e) {
      console.error('Live2D 模型加载失败:', modelPath, e)
      error.value = e.message
      // 加载失败时清理
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
      const im = model.value.internalModel
      const cm = im.coreModel
      for (const [key, val] of Object.entries(params)) {
        try { cm.setParameterValueById(key, val) } catch (_) {}
      }
    } catch (e) {
      console.warn('设置Live2D参数失败:', e)
    }
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

  return { init, setParams, setIdleMode, idleMode, error, destroy }
}
