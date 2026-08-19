import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // SSE 流式：只对 /chat/stream 设 Accept 头(不能全局设,否则普通 JSON 接口 406)
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            if (req.url && req.url.includes('/chat/stream')) {
              proxyReq.setHeader('Accept', 'text/event-stream')
            }
          })
        },
        proxyTimeout: 600000,
        timeout: 600000
      }
    }
  }
})
