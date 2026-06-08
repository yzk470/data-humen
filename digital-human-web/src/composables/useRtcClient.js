import { ref } from 'vue'

const ICE_SERVERS = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' }
  ]
}

export function useRtcClient() {
  const pc = ref(null)
  const localStream = ref(null)
  const remoteStream = ref(null)
  const dataChannel = ref(null)
  const pcState = ref('NEW')

  async function createPeerConnection(signaling) {
    pc.value = new RTCPeerConnection(ICE_SERVERS)

    pc.value.onconnectionstatechange = () => {
      pcState.value = pc.value.connectionState
    }

    pc.value.ontrack = (event) => {
      remoteStream.value = event.streams[0]
    }

    pc.value.ondatachannel = (event) => {
      dataChannel.value = event.channel
      dataChannel.value.onmessage = (e) => {
        const animData = JSON.parse(e.data)
        if (animData.type === 'animation_frame') {
          onAnimationFrameCallback(animData)
        }
      }
    }

    pc.value.onicecandidate = (event) => {
      if (event.candidate) {
        signaling.send({
          type: 'ice-candidate',
          candidate: event.candidate
        })
      }
    }

    try {
      localStream.value = await navigator.mediaDevices.getUserMedia({
        audio: true
      })
      localStream.value.getTracks().forEach(track => {
        pc.value.addTrack(track, localStream.value)
      })
    } catch (err) {
      console.error('麦克风访问失败:', err)
    }

    const offer = await pc.value.createOffer()
    await pc.value.setLocalDescription(offer)
    signaling.send({ type: 'offer', sdp: offer.sdp })

    signaling.onMessage(async (msg) => {
      if (msg.type === 'answer') {
        await pc.value.setRemoteDescription(
          new RTCSessionDescription({ type: 'answer', sdp: msg.sdp })
        )
      } else if (msg.type === 'ice-candidate') {
        await pc.value.addIceCandidate(new RTCIceCandidate(msg.candidate))
      }
    })
  }

  let onAnimationFrameCallback = () => {}

  function onAnimationFrame(callback) {
    onAnimationFrameCallback = callback
  }

  function close() {
    if (localStream.value) {
      localStream.value.getTracks().forEach(t => t.stop())
    }
    if (pc.value) {
      pc.value.close()
    }
    pcState.value = 'CLOSED'
  }

  return {
    pc, localStream, remoteStream, dataChannel, pcState,
    createPeerConnection, onAnimationFrame, close
  }
}
