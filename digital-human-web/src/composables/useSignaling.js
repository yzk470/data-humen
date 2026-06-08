import { ref } from 'vue'

export function useSignaling() {
  const ws = ref(null)
  const connected = ref(false)

  function connect(sessionId) {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${location.host}/ws/signaling?sessionId=${sessionId}`

    ws.value = new WebSocket(url)

    ws.value.onopen = () => {
      connected.value = true
      console.log('信令 WebSocket 已连接')
    }

    ws.value.onclose = () => {
      connected.value = false
      console.log('信令 WebSocket 已断开')
    }

    ws.value.onerror = (err) => {
      console.error('信令 WebSocket 错误:', err)
    }
  }

  function send(message) {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      ws.value.send(JSON.stringify(message))
    }
  }

  function onMessage(callback) {
    if (ws.value) {
      ws.value.onmessage = (event) => {
        const data = JSON.parse(event.data)
        callback(data)
      }
    }
  }

  function disconnect() {
    if (ws.value) {
      ws.value.close()
    }
  }

  return { connect, send, onMessage, disconnect, connected }
}
