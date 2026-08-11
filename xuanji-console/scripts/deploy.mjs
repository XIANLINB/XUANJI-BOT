import { cp, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
// vite.config.ts 配置 outDir 为 frontend-dist7，但历史上也用过 dist/，
// 这里优先找 frontend-dist7，回退到 dist，避免构建产物目录改名后部署脚本永远失败。
const candidateDirs = ['frontend-dist7', 'dist']
const dist = candidateDirs.map(d => resolve(root, d)).find(existsSync) || null
const target = resolve(root, '../xuanji-starter/src/main/resources/static/xuanji/console')

if (!dist) {
  console.error('找不到构建产物（frontend-dist7/ 或 dist/），请先运行 npm run build')
  process.exit(1)
}

// 先清掉旧的前端产物（保留目录），再拷入新的构建结果。
await rm(target, { recursive: true, force: true })
await cp(dist, target, { recursive: true })
console.log('✅ 控制台已部署到', target)
