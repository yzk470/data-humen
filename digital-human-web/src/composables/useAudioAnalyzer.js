import { ref, onUnmounted } from 'vue'

export function useAudioAnalyzer() {
  const mouthOpenY = ref(0)
  const audioContext = ref(null)
  const analyserNode = ref(null)
  const isAnalyzing = ref(false)
  const sourceNode = ref(null)
  const bufferSource = ref(null)

  let animationFrameId = null

  async function ensureAudioContext() {
    if (!audioContext.value) {
      audioContext.value = new (window.AudioContext || window.webkitAudioContext)()
    }

    if (audioContext.value.state === 'suspended') {
      await audioContext.value.resume()
    }
  }

  async function connect(audioElement) {
    await ensureAudioContext()

    if (sourceNode.value) {
      try { sourceNode.value.disconnect() } catch (_) { /* ignore */ }
      sourceNode.value = null
    }

    if (analyserNode.value) {
      try { analyserNode.value.disconnect() } catch (_) { /* ignore */ }
    }

    analyserNode.value = audioContext.value.createAnalyser()
    analyserNode.value.fftSize = 2048

    sourceNode.value = audioContext.value.createMediaElementSource(audioElement)
    sourceNode.value.connect(analyserNode.value)
    analyserNode.value.connect(audioContext.value.destination)

    isAnalyzing.value = true
    startLoop()
  }

  /**
   * 使用 decodeAudioData + AudioBufferSourceNode 播放音频，
   * 完全绕过 HTML Audio 元素和 createMediaElementSource。
   */
  async function playFromBase64(base64) {
    await ensureAudioContext()
    stop()

    const ctx = audioContext.value

    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }

    const audioBuffer = await ctx.decodeAudioData(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength))

    const source = ctx.createBufferSource()
    source.buffer = audioBuffer
    bufferSource.value = source

    analyserNode.value = ctx.createAnalyser()
    analyserNode.value.fftSize = 2048

    source.connect(analyserNode.value)
    analyserNode.value.connect(ctx.destination)

    source.onended = () => {
      stop()
    }

    source.start(0)
    isAnalyzing.value = true
    startLoop()
  }

  function startLoop() {
    const loop = () => {
      if (!isAnalyzing.value) return
      const dataArray = new Float32Array(analyserNode.value.fftSize)
      analyserNode.value.getFloatTimeDomainData(dataArray)

      let sum = 0
      for (let i = 0; i < dataArray.length; i++) {
        sum += dataArray[i] * dataArray[i]
      }
      const rms = Math.sqrt(sum / dataArray.length)
      mouthOpenY.value = Math.min(1.0, rms * 8.0)

      animationFrameId = requestAnimationFrame(loop)
    }
    animationFrameId = requestAnimationFrame(loop)
  }

  function stop() {
    isAnalyzing.value = false
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
    animationFrameId = null
    mouthOpenY.value = 0

    if (bufferSource.value) {
      try { bufferSource.value.stop() } catch (_) { /* already stopped */ }
      bufferSource.value = null
    }
    if (sourceNode.value) {
      try { sourceNode.value.disconnect() } catch (_) { /* ignore */ }
      sourceNode.value = null
    }
    if (analyserNode.value) {
      try { analyserNode.value.disconnect() } catch (_) { /* ignore */ }
      analyserNode.value = null
    }
  }

  function destroy() {
    stop()
    if (audioContext.value) {
      audioContext.value.close()
      audioContext.value = null
    }
  }

  onUnmounted(() => destroy())

  return { connect, playFromBase64, stop, destroy, mouthOpenY, isAnalyzing, ensureAudioContext }
}
