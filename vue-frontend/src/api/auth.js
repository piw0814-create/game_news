import api from './index.js'
import axios from 'axios'

export const authApi = {
  login(data) {
    // 로그인 401은 화면에서 직접 처리해야 하므로 공통 401 interceptor를 거치지 않는다.
    // 상대 경로를 사용해 local/ngrok/production 모두 같은 코드로 동작하게 한다.
    return axios.post('/api/auth/login', data, {
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
