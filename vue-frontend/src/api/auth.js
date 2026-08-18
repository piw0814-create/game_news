import api from './index.js'
import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const authApi = {
  login(data) {
    // 로그인 401은 화면에서 직접 처리해야 하므로 공통 401 interceptor를 거치지 않는다.
    return axios.post(`${API_BASE_URL}/api/auth/login`, data, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  getMe() {
    return api.get('/api/users/me')
  },

  register(data) {
    return api.post('/api/users/register', data)
  }
}
