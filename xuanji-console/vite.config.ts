import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期：前端跑在 5173，后端 Spring Boot 跑在 8668。
// 把 /xuanji、/actuator、/onebot、/webhook 代理到后端，避免跨域。
// 生产期：npm run build 产物由 scripts/deploy.mjs 拷进 Spring 静态目录，
// 直接由后端以静态资源服务，这些代理不再需要。
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/xuanji': { target: 'http://localhost:8668', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8668', changeOrigin: true },
      '/onebot': { target: 'http://localhost:8668', changeOrigin: true },
      '/webhook': { target: 'http://localhost:8668', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
