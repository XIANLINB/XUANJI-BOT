import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'

// 开发期：前端跑在 5173，后端 Spring Boot 跑在 8668。
// 把 /xuanji、/actuator、/onebot、/webhook 代理到后端，避免跨域。
// 生产期：npm run build 产物由 scripts/deploy.mjs 拷进 Spring 静态目录，
// 直接由后端以静态资源服务，这些代理不再需要。
export default defineConfig({
  plugins: [
    vue(),
    // 技术栈约定：auto-import（vue/vue-router/pinia API 免手动 import）+ Naive UI 按需自动导入
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
      eslintrc: { enabled: false }
    }),
    Components({
      resolvers: [NaiveUiResolver()],
      dts: 'src/components.d.ts'
    })
  ],
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
    emptyOutDir: true,
    chunkSizeWarningLimit: 800
  }
})
