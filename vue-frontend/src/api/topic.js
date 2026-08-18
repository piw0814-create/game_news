import api from './index.js'

export const topicApi = {
  getAll() {
    return api.get('/api/topics')
  },

  getById(id) {
    return api.get(`/api/topics/${id}`)
  },

  getComments(id) {
    return api.get(`/api/topics/${id}/comments`)
  },

  createComment(id, content) {
    return api.post(`/api/topics/${id}/comments`, { content })
  },

  deleteComment(id, commentId) {
    return api.delete(`/api/topics/${id}/comments/${commentId}`)
  },

  getLikeStatus(id) {
    return api.get(`/api/topics/${id}/likes`)
  },

  like(id) {
    return api.post(`/api/topics/${id}/likes`)
  },

  unlike(id) {
    return api.delete(`/api/topics/${id}/likes`)
  }
}
