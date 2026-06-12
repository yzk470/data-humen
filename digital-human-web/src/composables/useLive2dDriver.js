import { ref } from 'vue'
import { CubismFramework } from '../cubism-framework/live2dcubismframework'
import { CubismDefaultParameterId } from '../cubism-framework/cubismdefaultparameterid'
import { CubismModelSettingJson } from '../cubism-framework/cubismmodelsettingjson'
import { CubismBreath, BreathParameterData } from '../cubism-framework/effect/cubismbreath'
import { CubismEyeBlink } from '../cubism-framework/effect/cubismeyeblink'
import { CubismMatrix44 } from '../cubism-framework/math/cubismmatrix44'
import { CubismMotion } from '../cubism-framework/motion/cubismmotion'
import { CubismLipSyncUpdater } from '../cubism-framework/motion/cubismlipsyncupdater'
import { CubismUpdateScheduler } from '../cubism-framework/motion/cubismupdatescheduler'
import { CubismBreathUpdater } from '../cubism-framework/motion/cubismbreathupdater'
import { CubismEyeBlinkUpdater } from '../cubism-framework/motion/cubismeyeblinkupdater'
import { CubismExpressionUpdater } from '../cubism-framework/motion/cubismexpressionupdater'
import { CubismPhysicsUpdater } from '../cubism-framework/motion/cubismphysicsupdater'
import { CubismPoseUpdater } from '../cubism-framework/motion/cubismposeupdater'
import { CubismShaderManager_WebGL } from '../cubism-framework/rendering/cubismshader_webgl'
import { CubismUserModel } from '../cubism-framework/model/cubismusermodel'
import { Live2dLipSyncValueProvider } from './live2dLipSyncValueProvider'

const SHADER_PATH = '/Framework/Shaders/WebGL/'
const IDLE_GROUP = 'Idle'
const SKETCH_PART_ID = 'Part01Sketch'

export function useLive2dDriver() {
  const model = ref(null)
  const error = ref(null)
  const loaded = ref(false)

  let animationFrame = null
  let lastTime = 0
  let frameworkStarted = false
  let activeParams = {}
  let runtime = null

  async function ensureFramework() {
    if (frameworkStarted) return
    CubismFramework.startUp()
    CubismFramework.initialize()
    frameworkStarted = true
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

      if (!gl) {
        error.value = 'WebGL unavailable'
        return
      }

      const pixelRatio = window.devicePixelRatio || 1
      const pixelW = Math.max(1, Math.round(canvas.clientWidth * pixelRatio))
      const pixelH = Math.max(1, Math.round(canvas.clientHeight * pixelRatio))

      canvas.width = pixelW
      canvas.height = pixelH

      const baseDir = modelPath.substring(0, modelPath.lastIndexOf('/') + 1)
      const jsonBuffer = await fetchArrayBuffer(modelPath)
      const setting = new CubismModelSettingJson(jsonBuffer, jsonBuffer.byteLength)

      const mocBuffer = await fetchArrayBuffer(baseDir + setting.getModelFileName())

      const userModel = new CubismUserModel()
      userModel.loadModel(mocBuffer)
      userModel.createRenderer(pixelW, pixelH)
      userModel.setRenderTargetSize(pixelW, pixelH)
      userModel.getRenderer().startUp(gl)
      userModel.getRenderer().setIsPremultipliedAlpha(true)
      userModel.getRenderer().loadShaders(SHADER_PATH)

      await loadTextures(gl, userModel, setting, baseDir)
      await ensureShadersReady(gl)

      const state = {
        gl,
        model: userModel,
        setting,
        updateScheduler: new CubismUpdateScheduler(),
        expressions: new Map(),
        idleMotion: null,
        eyeBlinkIds: [],
        lipSyncIds: [],
        lipSyncProvider: null,
        motionUpdated: false,
        sketchPartId: CubismFramework.getIdManager().getId(SKETCH_PART_ID)
      }

      await loadExpressions(state, baseDir)
      await loadPhysics(state, baseDir)
      await loadPose(state, baseDir)
      setupEyeBlink(state)
      setupBreath(state)
      setupEffectIds(state)
      await loadIdleMotion(state, baseDir)
      setupModelMatrix(state)

      model.value = userModel
      runtime = state
      loaded.value = true
      lastTime = performance.now()

      console.log('[Live2D] model loaded', {
        modelPath,
        logicalW: userModel.getModel().getCanvasWidth(),
        logicalH: userModel.getModel().getCanvasHeight(),
        pixelW,
        pixelH,
        textures: setting.getTextureCount()
      })

      render()
      setParams(activeParams)
    } catch (e) {
      console.error('[Live2D] init failed', e)
      error.value = e?.message || 'Live2D init failed'
      destroy()
    }
  }

  function render() {
    animationFrame = requestAnimationFrame(render)

    if (!runtime?.model) return

    const now = performance.now()
    const dt = Math.max(0, (now - lastTime) / 1000)
    lastTime = now

    const { gl, model: userModel, updateScheduler, idleMotion, sketchPartId } = runtime
    const liveModel = userModel.getModel()

    liveModel.loadParameters()

    runtime.motionUpdated = false
    if (idleMotion) {
      if (userModel._motionManager.isFinished()) {
        userModel._motionManager.startMotionPriority(idleMotion, false, 1)
      } else {
        runtime.motionUpdated = userModel._motionManager.updateMotion(liveModel, dt)
      }
    }

    liveModel.saveParameters()
    updateScheduler.onLateUpdate(liveModel, dt)
    applyParams(liveModel, activeParams)
    liveModel.setPartOpacityById(sketchPartId, 0)
    liveModel.update()

    gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight)
    gl.clearColor(0, 0, 0, 0)
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
    gl.enable(gl.BLEND)
    gl.blendFuncSeparate(gl.ONE, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA)

    const framebuffer = gl.getParameter(gl.FRAMEBUFFER_BINDING)
    const viewport = gl.getParameter(gl.VIEWPORT)
    userModel.getRenderer().setRenderState(framebuffer, viewport)
    userModel.getRenderer().drawModel(SHADER_PATH)
  }

  function applyParams(liveModel, params) {
    for (const [key, value] of Object.entries(params || {})) {
      if (typeof value !== 'number' || Number.isNaN(value)) continue
      if (key === 'ParamMouthOpenY' && runtime?.lipSyncProvider && runtime.lipSyncIds.length > 0) {
        continue
      }
      try {
        const id = CubismFramework.getIdManager().getId(key)
        liveModel.setParameterValueById(id, value)
      } catch (_) {
        // Ignore parameters that do not exist on the current model.
      }
    }
  }

  function setParams(params) {
    activeParams = { ...(params || {}) }
    runtime?.lipSyncProvider?.setValue(activeParams.ParamMouthOpenY ?? 0)
    if (!runtime?.model) return
    applyParams(runtime.model.getModel(), activeParams)
  }

  function destroy() {
    activeParams = {}

    if (animationFrame) {
      cancelAnimationFrame(animationFrame)
      animationFrame = null
    }

    if (runtime) {
      try {
        runtime.model?._motionManager?.stopAllMotions()
        runtime.idleMotion?.release?.()
        for (const expression of runtime.expressions.values()) {
          expression?.release?.()
        }
        runtime.updateScheduler?.release?.()
        runtime.model?.release?.()
      } catch (_) {
        // Ignore teardown errors during hot reload / re-init.
      }
    }

    runtime = null
    if (model.value) {
      model.value = null
    }
    loaded.value = false
  }

  function disposeFramework() {
    if (!frameworkStarted) return

    try {
      CubismFramework.dispose()
      CubismFramework.cleanUp()
    } catch (_) {
      // Ignore framework disposal errors during final teardown.
    }

    frameworkStarted = false
  }

  async function loadTextures(gl, userModel, setting, baseDir) {
    for (let i = 0; i < setting.getTextureCount(); i += 1) {
      const img = await loadImage(baseDir + setting.getTextureFileName(i))
      const tex = gl.createTexture()

      gl.bindTexture(gl.TEXTURE_2D, tex)
      gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 1)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img)
      gl.bindTexture(gl.TEXTURE_2D, null)

      userModel.getRenderer().bindTexture(i, tex)
    }

    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 0)
  }

  async function ensureShadersReady(gl) {
    const shader = CubismShaderManager_WebGL.getInstance().getShader(gl)
    if (!shader) {
      throw new Error('Live2D shader manager unavailable')
    }

    if (shader._isShaderLoaded) {
      return
    }

    await new Promise((resolve, reject) => {
      const startedAt = performance.now()

      const poll = () => {
        if (shader._isShaderLoaded) {
          resolve()
          return
        }

        if (shader._isShaderLoading === false && shader._shaderSets?.length > 0) {
          reject(new Error('Live2D shaders failed to initialize'))
          return
        }

        if (performance.now() - startedAt > 8000) {
          reject(new Error('Live2D shader initialization timed out'))
          return
        }

        requestAnimationFrame(poll)
      }

      poll()
    })
  }

  async function loadExpressions(state, baseDir) {
    const count = state.setting.getExpressionCount()
    if (count <= 0) return

    for (let i = 0; i < count; i += 1) {
      const name = state.setting.getExpressionName(i)
      const file = state.setting.getExpressionFileName(i)
      const buffer = await fetchArrayBuffer(baseDir + file)
      const expression = state.model.loadExpression(buffer, buffer.byteLength, name)
      if (expression) {
        state.expressions.set(name, expression)
      }
    }

    if (state.model._expressionManager && state.expressions.size > 0) {
      state.updateScheduler.addUpdatableList(
        new CubismExpressionUpdater(state.model._expressionManager)
      )
    }
  }

  async function loadPhysics(state, baseDir) {
    const file = state.setting.getPhysicsFileName()
    if (!file) return

    const buffer = await fetchArrayBuffer(baseDir + file)
    state.model.loadPhysics(buffer, buffer.byteLength)

    if (state.model._physics) {
      state.updateScheduler.addUpdatableList(new CubismPhysicsUpdater(state.model._physics))
    }
  }

  async function loadPose(state, baseDir) {
    const file = state.setting.getPoseFileName()
    if (!file) return

    const buffer = await fetchArrayBuffer(baseDir + file)
    state.model.loadPose(buffer, buffer.byteLength)

    if (state.model._pose) {
      state.updateScheduler.addUpdatableList(new CubismPoseUpdater(state.model._pose))
    }
  }

  function setupEyeBlink(state) {
    if (state.setting.getEyeBlinkParameterCount() <= 0) return

    state.model._eyeBlink = CubismEyeBlink.create(state.setting)
    state.updateScheduler.addUpdatableList(
      new CubismEyeBlinkUpdater(() => state.motionUpdated, state.model._eyeBlink)
    )
  }

  function setupBreath(state) {
    state.model._breath = CubismBreath.create()
    state.model._breath.setParameters([
      new BreathParameterData(
        CubismFramework.getIdManager().getId(CubismDefaultParameterId.ParamAngleX),
        0,
        10,
        6.5,
        0.5
      ),
      new BreathParameterData(
        CubismFramework.getIdManager().getId(CubismDefaultParameterId.ParamAngleY),
        0,
        8,
        3.5,
        0.5
      ),
      new BreathParameterData(
        CubismFramework.getIdManager().getId(CubismDefaultParameterId.ParamAngleZ),
        0,
        10,
        5.5,
        0.5
      ),
      new BreathParameterData(
        CubismFramework.getIdManager().getId(CubismDefaultParameterId.ParamBodyAngleX),
        0,
        4,
        15.5,
        0.5
      ),
      new BreathParameterData(
        CubismFramework.getIdManager().getId(CubismDefaultParameterId.ParamBreath),
        0.5,
        0.5,
        3.2,
        1
      )
    ])

    state.updateScheduler.addUpdatableList(new CubismBreathUpdater(state.model._breath))
  }

  function setupEffectIds(state) {
    const eyeBlinkCount = state.setting.getEyeBlinkParameterCount()
    for (let i = 0; i < eyeBlinkCount; i += 1) {
      state.eyeBlinkIds.push(state.setting.getEyeBlinkParameterId(i))
    }

    const lipSyncCount = state.setting.getLipSyncParameterCount()
    for (let i = 0; i < lipSyncCount; i += 1) {
      state.lipSyncIds.push(state.setting.getLipSyncParameterId(i))
    }

    if (state.lipSyncIds.length > 0) {
      state.lipSyncProvider = new Live2dLipSyncValueProvider()
      state.updateScheduler.addUpdatableList(
        new CubismLipSyncUpdater(state.lipSyncIds, state.lipSyncProvider)
      )
    }
  }

  async function loadIdleMotion(state, baseDir) {
    if (state.setting.getMotionCount(IDLE_GROUP) <= 0) return

    const file = state.setting.getMotionFileName(IDLE_GROUP, 0)
    const buffer = await fetchArrayBuffer(baseDir + file)
    const motion = CubismMotion.create(buffer, buffer.byteLength)

    if (!motion) return

    motion.setLoop(true)
    motion.setLoopFadeIn(true)
    motion.setEffectIds(state.eyeBlinkIds, state.lipSyncIds)

    const fadeIn = state.setting.getMotionFadeInTimeValue(IDLE_GROUP, 0)
    const fadeOut = state.setting.getMotionFadeOutTimeValue(IDLE_GROUP, 0)
    if (fadeIn >= 0) motion.setFadeInTime(fadeIn)
    if (fadeOut >= 0) motion.setFadeOutTime(fadeOut)

    state.idleMotion = motion
  }

  function setupModelMatrix(state) {
    const liveModel = state.model.getModel()
    const modelMatrix = state.model.getModelMatrix()
    const layout = new Map()

    if (state.setting.getLayoutMap(layout)) {
      modelMatrix.setupFromLayout(layout)
    } else {
      modelMatrix.setHeight(1.8)
      modelMatrix.setCenterPosition(1.0, 1.0)
    }

    const viewMatrix = new CubismMatrix44()
    viewMatrix.multiplyByMatrix(modelMatrix)
    state.model.getRenderer().setMvpMatrix(viewMatrix)

    liveModel.setPartOpacityById(state.sketchPartId, 0)
  }

  async function fetchArrayBuffer(url) {
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`Failed to load resource: ${url} (${response.status})`)
    }
    return response.arrayBuffer()
  }

  function loadImage(src) {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error(`Failed to load texture: ${src}`))
      img.src = src
    })
  }

  return { init, setParams, destroy, disposeFramework, error, loaded }
}
