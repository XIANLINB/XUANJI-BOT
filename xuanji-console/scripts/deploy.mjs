import { cp, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
const dist = resolve(root, 'dist')
const target = resolve(root, '../xuanji-starter/src/main/resources/static/xuanji/console')

if (!existsSync(dist)) {
  console.error('找不到 dist/，请先运行 npm run build')
  process.exit(1)
}

// 先清掉旧的前端产物（保留目录），再拷入新的构建结果。
await rm(target, { recursive: true, force: true })
await cp(dist, target, { recursive: true })
console.log('✅ 控制台已部署到', target)
