import { ref, onUnmounted } from 'vue'

export function useAudioAnalyzer() {
  const mouthOpenY = ref(0)
  const audioContext = ref(null)
  const analyserNode = ref(null)
  const isAnalyzing = ref(false)

  let animationFrameId = null

  function connect(audioElement) {
    if (!audioContext.value) {
      audioContext.value = new (window.AudioContext || window.webkitAudioContext)()
    }
    analyserNode.value = audioContext.value.createAnalyser()
    analyserNode.value.fftSize = 2048

    const source = audioContext.value.createMediaElementSource(audioElement)
    source.connect(analyserNode.value)
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
  }

  function destroy() {
    stop()
    if (audioContext.value) {
      audioContext.value.close()
    }
  }

  onUnmounted(() => destroy())

  return { connect, stop, destroy, mouthOpenY, isAnalyzing }
}
