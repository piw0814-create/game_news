import api from './index.js'

export const entityReviewApi = {
  getAll(status = 'PENDING') {
    return api.get('/api/admin/entity-reviews', { params: status ? { status } : {} })
  },

  getById(reviewId) {
    return api.get(`/api/admin/entity-reviews/${reviewId}`)
  },

  resolve(reviewId, payload) {
    return api.post(`/api/admin/entity-reviews/${reviewId}/resolve`, payload, { timeout: 60000 })
  },

  recheck(reviewId) {
    return api.post(`/api/admin/entity-reviews/${reviewId}/recheck`, null, { timeout: 60000 })
  },

  reopen(reviewId) {
    return api.post(`/api/admin/entity-reviews/${reviewId}/reopen`, null, { timeout: 60000 })
  }
}
