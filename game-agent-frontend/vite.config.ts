import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 可通过环境变量覆盖后端地址: BACKEND_URL，例如 http://localhost:8088
const backendTarget = process.env.BACKEND_URL || 'http://localhost:8088'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',  // 监听所有网络接口，允许局域网访问
    port: 5173,
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true
      }
    }
  }
})
