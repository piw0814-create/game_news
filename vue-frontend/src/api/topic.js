import api from './index.js'

export const topicApi = {
  getAll() {
    return api.get('/api/topics')
  },

  getById(id) {
    return api.get(`/api/topics/${id}`)
  }
}
