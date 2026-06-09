import { ref } from 'vue'
import { CubismFramework, Option } from '../cubism-framework/live2dcubismframework'
import { CubismUserModel } from '../cubism-framework/model/cubismusermodel'
import { CubismModelSettingJson } from '../cubism-framework/cubismmodelsettingjson'
import { CubismMoc } from '../cubism-framework/model/cubismmoc'
import { CubismDefaultParameterId } from '../cubism-framework/cubismdefaultparameterid'
import { CubismBreath } from '../cubism-framework/effect/cubismbreath'
import { CubismEyeBlink } from '../cubism-framework/effect/cubismeyeblink'
import { CubismMatrix44 } from '../cubism-framework/math/cubismmatrix44'

export function useLive2dDriver() {
  const model = ref(null)
  const glRef = ref(null)
  const error = ref(null)
  const loaded = ref(false)
  let animationFrame = null
  let lastTime = 0
  let started = false

  async function ensureFramework() {
    if (started) return
    CubismFramework.startUp()
    CubismFramework.initialize()
    started = true
    console.log('[Live2D] Framework initialized')
  }

  async function init(canvas, modelPath) {
    destroy()
    loaded.value = false
    error.value = null

    try {
      await ensureFramework()

      const gl = canvas.getContext('webgl', {
        alpha: true,
        premultipliedAlpha: true
      })
      if (!gl) { error.value = 'WebGL unavailable'; return }
      glRef.value = gl

      // 加载 model3.json
      const baseDir = modelPath.substring(0, modelPath.lastIndexOf('/') + 1)
      const jsonBuffer = await (await fetch(modelPath)).arrayBuffer()
      const setting = new CubismModelSettingJson(jsonBuffer, jsonBuffer.byteLength)

      // 加载 moc3
      const mocName = setting.getModelFileName()
      const mocBuffer = await (await fetch(baseDir + mocName)).arrayBuffer()

      // 创建模型
      const m = new CubismUserModel()
      m.loadModel(mocBuffer)

      // 创建渲染器并绑定 GL context
      const pixelW = canvas.clientWidth * (window.devicePixelRatio || 1)
      const pixelH = canvas.clientHeight * (window.devicePixelRatio || 1)
      m.createRenderer(pixelW, pixelH)
      m.getRenderer().startUp(gl)
      model.value = m

      // 加载纹理
      const texCount = setting.getTextureCount()
      console.log('[Live2D] 加载', texCount, '个纹理...')
      for (let i = 0; i < texCount; i++) {
        const texFile = baseDir + setting.getTextureFileName(i)
        const img = await loadImage(texFile)

        // 创建 WebGL 纹理
        const tex = gl.createTexture()
        gl.bindTexture(gl.TEXTURE_2D, tex)
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img)
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR)
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
        gl.generateMipmap(gl.TEXTURE_2D)

        // CubismUserModel 内部 renderer 绑定纹理
        m.getRenderer().bindTexture(i, tex)
        console.log('[Live2D] 纹理', i, '绑定:', texFile)
      }

      // 设置参数
      m.getModel().setParameterValueById(CubismDefaultParameterId.ParamAngleX, 0)
      m.getModel().setParameterValueById(CubismDefaultParameterId.ParamAngleY, 0)
      m.getModel().setParameterValueById(CubismDefaultParameterId.ParamAngleZ, 0)

      // 呼吸
      const breath = CubismBreath.create()
      breath.setParameters([{
        parameterId: CubismDefaultParameterId.ParamBreath,
        offset: 0, peak: 0.5, cycle: 3.5, weight: 0.5
      }])

      // 眨眼
      const eyeBlink = CubismEyeBlink.create(setting)

      // 设置渲染目标尺寸
      canvas.width = pixelW
      canvas.height = pixelH
      m.getRenderer().setRenderTargetSize(pixelW, pixelH)
      const logicalW = m.getModel().getCanvasWidth()
      const logicalH = m.getModel().getCanvasHeight()

      // 视口
      gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight)

      // 矩阵
      const matrix = new CubismMatrix44()
      const ratio = canvas.width / logicalW
      const ratioY = canvas.height / logicalH
      matrix.scale(ratio, ratioY)
      m.getRenderer().setMvpMatrix(matrix)
      m.getRenderer().setIsPremultipliedAlpha(true)

      loaded.value = true
      console.log('[Live2D] 初始化完成')

      // 渲染循环
      lastTime = performance.now()
      const render = () => {
        const now = performance.now()
        const dt = (now - lastTime) / 1000
        lastTime = now

        if (m && gl) {
          breath.updateParameters(m.getModel(), dt)
          eyeBlink.updateParameters(m.getModel(), dt)
          m.getModel().update()

          gl.clearColor(0, 0, 0, 0)
          gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
          gl.enable(gl.BLEND)
          gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)

          m.getRenderer().drawModel()
        }
        animationFrame = requestAnimationFrame(render)
      }
      render()

    } catch (e) {
      console.error('[Live2D] 初始化失败:', e)
      error.value = e.message
    }
  }

  function setParams(params) {
    if (!model.value) return
    try {
      const m = model.value.getModel()
      for (const [key, val] of Object.entries(params)) {
        try { m.setParameterValueById(key, val) } catch (_) {}
      }
    } catch (_) {}
  }

  function destroy() {
    if (animationFrame) cancelAnimationFrame(animationFrame)
    if (model.value) {
      model.value.release()
      model.value = null
    }
    loaded.value = false
  }

  function loadImage(src) {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('Failed: ' + src))
      img.src = src
    })
  }

  return { init, setParams, destroy, error, loaded }
}
