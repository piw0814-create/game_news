import api from './index.js'

export const gameApi = {
  getAll() {
    return api.get('/api/games')
  }
}
