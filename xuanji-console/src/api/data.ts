// 数据中心 API：聚合统计 / 缓存清理 / 媒体文件存储
import { get, del } from './http'

const BASE = '/xuanji/api/v1'

export const dataApi = {
  // 聚合统计
  getStats: (days = 30, botKey?: string) => get('/console/data/stats', { days, botKey: botKey || '' }),
  // 缓存状态（botKey 可选：机器人级项按 bot 过滤）
  getCache: (botKey?: string) => get('/console/data/cache', { botKey: botKey || '' }),
  // 缓存清理：传 categories 数组 + 可选 botKey（转 query string）
  async clearCache(categories: string[], botKey?: string) {
    let url = `${BASE}/console/data/cache/clear?categories=${encodeURIComponent(categories.join(','))}`
    if (botKey) url += `&botKey=${encodeURIComponent(botKey)}`
    const res = await fetch(url, {
      method: 'POST',
      credentials: 'include'
    })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    return res.json()
  },
  // 媒体文件
  getFiles: () => get('/console/data/files'),
  deleteFile: (path: string) => del('/console/data/files' + '?path=' + encodeURIComponent(path)),
  // 按类型删除媒体（type=image|voice|video|file，空=全部）
  async clearFiles(type?: string) {
    let url = `${BASE}/console/data/files/clear`
    if (type) url += `?type=${encodeURIComponent(type)}`
    const res = await fetch(url, { method: 'POST', credentials: 'include' })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    return res.json()
  }
}
