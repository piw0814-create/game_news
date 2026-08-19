import api from './index.js'

export const gameApi = {
  getAll() {
    return api.get('/api/games')
  },

  getAdminAll(params = {}) {
    return api.get('/api/admin/games', { params })
  },

  getAdminById(gameId) {
    return api.get(`/api/admin/games/${gameId}`)
  },

  updateAdmin(gameId, payload) {
    return api.patch(`/api/admin/games/${gameId}`, payload)
  },

  confirmAdmin(gameId) {
    return api.post(`/api/admin/games/${gameId}/confirm`)
  },

  mergeAdmin(sourceGameId, targetGameId) {
    return api.post(`/api/admin/games/${sourceGameId}/merge`, { targetGameId })
  },

  rejectAdmin(gameId) {
    return api.post(`/api/admin/games/${gameId}/reject`)
  }
}
