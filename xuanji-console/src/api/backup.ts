// 备份恢复 API
import { get, post, put } from './http'

export const backupApi = {
  // 立即备份：categories = 可选分类数组（framework/platform/business/logs/messages，缺省全选），tag = 文件名标记（如 msg）
  createBackup: (categories: string[] = [], tag = '') =>
    post(`/console/backup/create?categories=${encodeURIComponent(categories.join(','))}${tag ? `&tag=${encodeURIComponent(tag)}` : ''}`),
  // 备份列表
  listBackups: () => get('/console/backup/list'),
  // 恢复备份
  restoreBackup: (name: string) => post(`/console/backup/restore?name=${encodeURIComponent(name)}`),
  // 删除备份
  deleteBackup: (name: string) => post(`/console/backup/delete?name=${encodeURIComponent(name)}`),
  // 立即备份聊天消息（群聊+单聊，表级 qqbot_message），文件名带 -msg- 标记，不受定时间隔限制
  createMessageBackup: () => post('/console/backup/create?categories=messages&tag=msg'),
  // 自动备份设置
  getBackupSettings: () => get('/console/backup/settings'),
  putBackupSettings: (body: Record<string, unknown>) => put('/console/backup/settings', body)
}
