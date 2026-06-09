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

// Admin API instance with Basic Auth
const adminApi = axios.create({
  baseURL: '/api/admin',
  timeout: 30000
})

adminApi.interceptors.request.use(config => {
  config.headers.Authorization = 'Basic ' + btoa('admin:dhAdmin2024')
  return config
})

// ---- 形象管理 API ----

export function uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH) {
  const formData = new FormData()
  formData.append('image', imageFile)
  formData.append('name', name)
  formData.append('cropX', cropX)
  formData.append('cropY', cropY)
  formData.append('cropW', cropW)
  formData.append('cropH', cropH)
  return adminApi.post('/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listAdminAvatars() {
  return adminApi.get('/avatar/list')
}

export function deleteAdminAvatar(id) {
  return adminApi.delete(`/avatar/${id}`)
}

export function listAvatars() {
  return api.get('/avatar/list')
}

export function switchSessionAvatar(sessionId, avatarId) {
  return api.put(`/session/${sessionId}/avatar`, { avatarId })
}

export { adminApi }
export default api
