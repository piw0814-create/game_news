import api from './index.js'

export const franchiseApi = {
  getAdminAll(params = {}) {
    return api.get('/api/admin/franchises', { params })
  },

  getAdminById(franchiseId) {
    return api.get(`/api/admin/franchises/${franchiseId}`)
  },

  createAdmin(payload) {
    return api.post('/api/admin/franchises', payload)
  },

  updateAdmin(franchiseId, payload) {
    return api.patch(`/api/admin/franchises/${franchiseId}`, payload)
  },

  syncIgdb(franchiseId) {
    return api.post(`/api/admin/franchises/${franchiseId}/sync-igdb`, null, { timeout: 60000 })
  },

  mergeAdmin(franchiseId, targetFranchiseId) {
    return api.post(`/api/admin/franchises/${franchiseId}/merge`, { targetFranchiseId })
  },

  linkGame(franchiseId, gameId, isPrimary = false) {
    return api.post(`/api/admin/franchises/${franchiseId}/games`, { gameId, isPrimary })
  },

  updateGameLink(franchiseId, gameId, isPrimary) {
    return api.patch(`/api/admin/franchises/${franchiseId}/games/${gameId}`, { isPrimary })
  },

  unlinkGame(franchiseId, gameId) {
    return api.delete(`/api/admin/franchises/${franchiseId}/games/${gameId}`)
  }
}
