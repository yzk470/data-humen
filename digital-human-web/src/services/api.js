import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000
})

export function createSession() {
  return api.post('/session/create')
}

export function getSession(sessionId) {
  return api.get(`/session/${sessionId}`)
}

export function closeSession(sessionId) {
  return api.delete(`/session/${sessionId}`)
}

export function sendTextMessage(sessionId, text) {
  return api.post('/chat/text', { sessionId, text })
}

export default api
