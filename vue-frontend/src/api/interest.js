import api from './index.js'

export const interestApi = {
  getMyGames() {
    return api.get('/api/interests/games')
  },

  getMyGameIds() {
    return api.get('/api/interests/game-ids')
  },

  addGame(gameId) {
    return api.post(`/api/interests/games/${gameId}`)
  },

  removeGame(gameId) {
    return api.delete(`/api/interests/games/${gameId}`)
  }
}
