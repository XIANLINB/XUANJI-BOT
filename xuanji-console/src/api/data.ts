// 数据中心 API：聚合统计 / 缓存清理 / 媒体文件存储
import { get, del, post } from './http'

export const dataApi = {
  // 聚合统计（startTime/endTime 为 epoch 秒，传 startTime 则用自定义范围覆盖 days）
  getStats: (days = 30, botKey?: string, startTime?: number, endTime?: number) =>
    get('/console/data/stats', { days, botKey: botKey || '', startTime: startTime || 0, endTime: endTime || 0 }),
  // 缓存状态（botKey 可选：机器人级项按 bot 过滤）
  getCache: (botKey?: string) => get('/console/data/cache', { botKey: botKey || '' }),
  // 缓存清理：传 categories 数组 + 可选 botKey（转 query string）
  clearCache: (categories: string[], botKey?: string) =>
    post(
      '/console/data/cache/clear?categories=' +
        encodeURIComponent(categories.join(',')) +
        (botKey ? '&botKey=' + encodeURIComponent(botKey) : '')
    ),
  // 媒体文件
  getFiles: () => get('/console/data/files'),
  deleteFile: (path: string) => del('/console/data/files' + '?path=' + encodeURIComponent(path)),
  // 按类型删除媒体（type=image|voice|video|file，空=全部）
  clearFiles: (type?: string) =>
    post('/console/data/files/clear' + (type ? '?type=' + encodeURIComponent(type) : ''))
}
