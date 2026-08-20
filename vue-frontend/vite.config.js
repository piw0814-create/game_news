import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const allowedHosts = (env.VITE_ALLOWED_HOSTS || '')
    .split(',')
    .map((host) => host.trim())
    .filter(Boolean)

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(import.meta.dirname, 'src')
      }
    },
    server: {
      // localhost 개발은 그대로 지원하면서 ngrok 같은 외부 터널도 받을 수 있게 한다.
      host: '0.0.0.0',
      port: 3000,
      strictPort: true,
      ...(allowedHosts.length > 0 ? { allowedHosts } : {}),
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          secure: false
        }
      }
    }
  }
})
