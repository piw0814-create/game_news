import api from './index.js'

const IGDB_REQUEST_TIMEOUT_MS = 20000

export const gameApi = {
  getAll() {
    return api.get('/api/games')
  },

  getFranchises(gameId) {
    return api.get(`/api/games/${gameId}/franchises`)
  },

  getFranchiseIds(gameIds) {
    return api.get('/api/games/franchise-ids', {
      params: { gameIds: gameIds.join(',') }
    })
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

  getAdminReviewContext(gameId) {
    return api.get(`/api/admin/games/${gameId}/review-context`)
  },

  previewAdminEnrichment(gameId) {
    return api.post(`/api/admin/games/${gameId}/enrichment/preview`, null, { timeout: IGDB_REQUEST_TIMEOUT_MS })
  },

  applyAdminEnrichment(gameId, igdbId) {
    return api.post(`/api/admin/games/${gameId}/enrichment/apply`, { igdbId }, { timeout: IGDB_REQUEST_TIMEOUT_MS })
  },

  mergeAdmin(sourceGameId, targetGameId) {
    return api.post(`/api/admin/games/${sourceGameId}/merge`, { targetGameId })
  },

  resolveAdminAsFranchise(gameId, franchiseId) {
    return api.post(`/api/admin/games/${gameId}/resolve-franchise`, { franchiseId })
  },

  rejectAdmin(gameId) {
    return api.post(`/api/admin/games/${gameId}/reject`)
  }
}
