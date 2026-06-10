import { ref, onUnmounted } from 'vue'

export function useAudioAnalyzer() {
  const mouthOpenY = ref(0)
  const audioContext = ref(null)
  const analyserNode = ref(null)
  const isAnalyzing = ref(false)
  const sourceNode = ref(null)

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
      try {
        sourceNode.value.disconnect()
      } catch (_) {
        // Ignore disconnect failures for replaced elements.
      }
      sourceNode.value = null
    }

    if (analyserNode.value) {
      try {
        analyserNode.value.disconnect()
      } catch (_) {
        // Ignore disconnect failures during rebind.
      }
    }

    analyserNode.value = audioContext.value.createAnalyser()
    analyserNode.value.fftSize = 2048

    sourceNode.value = audioContext.value.createMediaElementSource(audioElement)
    sourceNode.value.connect(analyserNode.value)
    analyserNode.value.connect(audioContext.value.destination)

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
      const mapped = Math.min(1.0, rms * 8.0)
      mouthOpenY.value = mapped

      animationFrameId = requestAnimationFrame(loop)
    }
    animationFrameId = requestAnimationFrame(loop)
  }

  function stop() {
    isAnalyzing.value = false
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
    animationFrameId = null
    mouthOpenY.value = 0
  }

  function destroy() {
    stop()
    if (sourceNode.value) {
      try {
        sourceNode.value.disconnect()
      } catch (_) {
        // Ignore teardown disconnect failures.
      }
      sourceNode.value = null
    }
    if (analyserNode.value) {
      try {
        analyserNode.value.disconnect()
      } catch (_) {
        // Ignore teardown disconnect failures.
      }
      analyserNode.value = null
    }
    if (audioContext.value) {
      audioContext.value.close()
      audioContext.value = null
    }
  }

  onUnmounted(() => destroy())

  return { connect, stop, destroy, mouthOpenY, isAnalyzing, ensureAudioContext }
}
