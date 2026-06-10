/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

import { LAppDelegate } from './lappdelegate';
import * as LAppDefine from './lappdefine';
import { LAppLive2DManager } from './lapplive2dmanager';
import { LAppModel } from './lappmodel';
import { CubismDefaultParameterId } from '@framework/cubismdefaultparameterid';
import { CubismFramework } from '@framework/live2dcubismframework';
import { CubismIdHandle } from '@framework/id/cubismid';

// 全局 API 给父窗口调用
declare global {
  interface Window {
    live2dAPI: {
      setExpression: (name: string) => void
      speak: (text: string, emotion?: string) => void
      stopSpeak: () => void
      setParam: (id: string, value: number) => void
    }
  }
}

/**
 * ブラウザロード後の処理
 */
window.addEventListener(
  'load',
  (): void => {
    if (!LAppDelegate.getInstance().initialize()) {
      return;
    }
    LAppDelegate.getInstance().run();

    // 动态获取当前模型（异步加载后会变化）
    function getModel(): LAppModel | null {
      const delegates = (LAppDelegate.getInstance() as any)._subdelegates
      const mgr: LAppLive2DManager = delegates?.[0]?.getLive2DManager()
      return mgr?._models?.[0] || null
    }

    let speakingTimer: number | null = null
    const mouthOpenId = CubismFramework.getIdManager().getId(
      CubismDefaultParameterId.ParamMouthOpenY)

    // 表情映射
    const emotionMap: Record<string, string> = {
      happy: 'F01', puzzled: 'F04', surprised: 'F06',
      sorry: 'F02', thinking: 'F03', neutral: 'F01'
    }

    const angleXId = CubismFramework.getIdManager().getId(
      CubismDefaultParameterId.ParamAngleX)

    // 测试: 2秒后用 index 方式设置参数
    setTimeout(() => {
      const model = getModel()
      const m = model?.getModel()
      if (m) {
        const paramCount = m.getParameterCount?.() || 0
        console.log('[Live2D] 模型参数总数:', paramCount)

        // 遍历前几个参数
        for (let i = 0; i < Math.min(5, paramCount); i++) {
          const id = m.getParameterId(i)
          const min = m.getParameterMinimumValue(i)
          const max = m.getParameterMaximumValue(i)
          const val = m.getParameterValueByIndex(i)
          console.log(`[Live2D] 参数[${i}]: id=${id} range=[${min},${max}] value=${val}`)
        }

        // 测试: 闭合眼睛 + update()
        m.setParameterValueByIndex(3, 0)
        m.setParameterValueByIndex(4, 0)
        m.update()
        console.log('[Live2D] 测试: 闭眼!')
        setTimeout(() => {
          m.setParameterValueByIndex(3, 1)
          m.setParameterValueByIndex(4, 1)
          m.update()
          console.log('[Live2D] 测试: 睁眼!')
        }, 2000)
      } else {
        console.log('[Live2D] 测试: model 或 getModel() 为空')
      }
    }, 2000)

    window.live2dAPI = {
      setExpression(name: string) {
        const model = getModel()
        if (!model) return
        const expName = emotionMap[name] || name
        console.log('[Live2D] setExpression:', expName)
        try { model.setExpression(expName) } catch (e) { console.error(e) }
      },
      speak(text: string, emotion?: string) {
        const model = getModel()
        if (!model) return
        if (emotion) window.live2dAPI.setExpression(emotion)
        const m = model.getModel()
        if (!m) return
        let phase = 0
        if (speakingTimer) clearInterval(speakingTimer)
        const duration = Math.max(3000, text.length * 100)
        const startTime = Date.now()
        speakingTimer = window.setInterval(() => {
          const elapsed = Date.now() - startTime
          if (elapsed > duration) {
            m.setParameterValueById(mouthOpenId, 0)
            m.update()  // 强制更新顶点，绕过 motion 覆盖
            if (speakingTimer) clearInterval(speakingTimer)
            return
          }
          phase += 0.3
          m.setParameterValueById(mouthOpenId,
            Math.abs(Math.sin(phase)) * 0.6 + Math.random() * 0.2)
          m.update()
        }, 50)
      },
      stopSpeak() {
        if (speakingTimer) clearInterval(speakingTimer)
        getModel()?.getModel()?.setParameterValueById(mouthOpenId, 0)
      },
      setParam(id: string, value: number) {
        try {
          const cid = CubismFramework.getIdManager().getId(id)
          getModel()?.getModel()?.setParameterValueById(cid, value)
        } catch (_) {}
      }
    }

    console.log('[Live2D] API ready:', window.live2dAPI)
  },
  { passive: true }
);

/**
 * 終了時の処理
 */
window.addEventListener(
  'beforeunload',
  (): void => LAppDelegate.releaseInstance(),
  { passive: true }
);

// 接收父窗口 postMessage
window.addEventListener('message', (event) => {
  console.log('[Live2D] 收到 postMessage:', event.data)
  if (!window.live2dAPI) { console.log('[Live2D] API未就绪'); return }
  const { type, data } = event.data || {}
  switch (type) {
    case 'speak':
      console.log('[Live2D] 执行 speak:', data?.[0]?.substring(0, 20), data?.[1])
      window.live2dAPI.speak(data?.[0] || '', data?.[1])
      break
    case 'stopSpeak':
      window.live2dAPI.stopSpeak()
      break
    case 'setExpression':
      window.live2dAPI.setExpression(data?.[0] || 'neutral')
      break
    case 'setParam':
      window.live2dAPI.setParam(data?.[0], data?.[1])
      break
  }
});
