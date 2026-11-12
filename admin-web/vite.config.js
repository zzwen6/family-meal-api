import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 构建产物直接放进 Spring Boot 的静态资源目录，由后端在 /admin/ 提供
export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  build: {
    outDir: '../src/main/resources/static/admin',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  },
  server: {
    port: 5174,
    proxy: {
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
    },
  },
})
