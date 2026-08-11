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
      // dts 关闭：生成的 auto-imports.d.ts 常被 IDE 锁住导致构建 EPERM；类型提示仅影响 IDE，不影响构建
      dts: false,
      eslintrc: { enabled: false }
    }),
    Components({
      resolvers: [NaiveUiResolver()],
      dts: false
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
    // 构建到独立目录，避免 dist 被 IDE/安全钩子锁导致 EPERM；部署脚本同步到 Spring static
    outDir: 'frontend-dist7',
    emptyOutDir: true,
    chunkSizeWarningLimit: 800
  }
})
