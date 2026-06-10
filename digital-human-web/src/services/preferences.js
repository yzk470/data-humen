import api, { adminApi } from './api'

export function getUserPreferences() {
  return api.get('/user/preferences')
}

export function updateUserPreferences(payload) {
  return api.put('/user/preferences', payload)
}

export function getAdminPreferences() {
  return adminApi.get('/config/preferences')
}

export function updateAdminPreferences(payload) {
  return adminApi.put('/config/preferences', payload)
}
