import { ref } from 'vue'
import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display/cubism4'

window.PIXI = PIXI

async function loadModelTextures(model, basePath) {
  const resp = await fetch(basePath)
  const modelJson = await resp.json()
  const textures = modelJson.FileReferences?.Textures || []

  for (let i = 0; i < textures.length; i++) {
    const texPath = basePath.replace(/[^/]*$/, '') + textures[i]
    console.log('[Live2D] 手动加载纹理:', texPath)

    const img = await new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error(`纹理加载失败: ${texPath}`))
      img.src = texPath
    })

    // 创建 PIXI 纹理并直接设到 WebGL renderer
    const pixiTex = PIXI.Texture.from(img)
    const renderer = model.internalModel.renderer
    console.log('[Live2D] renderer 类型:', renderer?.constructor?.name)
    console.log('[Live2D] renderer keys:', Object.keys(renderer || {}))
    console.log('[Live2D] renderer._textures:', renderer?._textures)

    // 尝试找到真正的 WebGL renderer
    let realRenderer = renderer
    if (renderer?._renderer) {
      realRenderer = renderer._renderer
      console.log('[Live2D] 找到内部 _renderer:', realRenderer?.constructor?.name)
    }

    try {
      realRenderer.bindTexture(i, pixiTex)
      console.log('[Live2D] 纹理', i, '绑定成功')
    } catch(e) {
      console.warn('[Live2D] bindTexture 失败:', i, e.message)
    }

    // 提取 WebGL 原生纹理
    const glTex = pixiTex.baseTexture?._glTextures
    const glTexObj = glTex ? Object.values(glTex)[0] : null
    console.log('[Live2D] WebGL 纹理对象:', glTexObj)

    // 用原生 WebGL 纹理替换 PIXI 纹理
    if (glTexObj && realRenderer?._textures) {
      // Proxy 可能拦截，用 Object.defineProperty 绕过
      try {
        realRenderer._textures[i] = glTexObj
        console.log('[Live2D] 已设原生纹理 _textures[', i, '] =', glTexObj)
      } catch(e) {
        console.warn('[Live2D] 设原生纹理失败:', e.message)
      }
    }
  }
  console.log('[Live2D] 纹理加载完成')
}

export function useLive2dDriver() {
  const app = ref(null)
  const model = ref(null)
  const error = ref(null)
  const loaded = ref(false)

  async function init(canvas, modelPath) {
    destroy()

    try {
      app.value = new PIXI.Application({
        view: canvas, width: canvas.width, height: canvas.height,
        backgroundAlpha: 0, antialias: false, resolution: 1
      })

      console.log('[Live2D] 加载模型:', modelPath)
      model.value = await Live2DModel.from(modelPath, {
        autoUpdate: true, autoHitTest: false, autoFocus: false
      })

      // 手动加载纹理
      await loadModelTextures(model.value, modelPath)

      console.log('[Live2D] 纹理数:', model.value.internalModel.textures?.length)

      // 居中缩放
      const im = model.value.internalModel
      const scale = Math.min(canvas.width / 2000, canvas.height / 2000) * 0.12
      model.value.scale.set(scale)
      model.value.x = canvas.width / 2
      model.value.y = canvas.height / 2
      model.value.anchor.set(0.5, 0.5)

      app.value.stage.addChild(model.value)
      loaded.value = true

    } catch (e) {
      console.error('[Live2D] 加载失败:', e)
      error.value = e.message
      if (model.value) { try { model.value.destroy() } catch (_) {}; model.value = null }
      if (app.value) { try { app.value.destroy(false, { children: true }) } catch (_) {}; app.value = null }
    }
  }

  function setParams(params) {
    if (!model.value) return
    try {
      const cm = model.value.internalModel?.coreModel
      if (cm) {
        for (const [key, val] of Object.entries(params)) {
          try { cm.setParameterValueById(key, val) } catch (_) {}
        }
      }
    } catch (_) {}
  }

  function destroy() {
    if (model.value) { try { model.value.destroy() } catch (_) {}; model.value = null }
    if (app.value) { try { app.value.destroy(false, { children: true }) } catch (_) {}; app.value = null }
    loaded.value = false
  }

  return { init, setParams, destroy, error, loaded }
}
