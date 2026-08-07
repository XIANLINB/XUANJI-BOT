// 备份恢复 API
import { get, post, put } from './http'

export const backupApi = {
  // 立即备份：scope = business | log | all
  createBackup: (scope: string) => post('/console/backup/create?scope=' + encodeURIComponent(scope)),
  // 备份列表
  listBackups: () => get('/console/backup/list'),
  // 恢复备份
  restoreBackup: (name: string) => post(`/console/backup/restore?name=${encodeURIComponent(name)}`),
  // 删除备份
  deleteBackup: (name: string) => post(`/console/backup/delete?name=${encodeURIComponent(name)}`),
  // 自动备份设置
  getBackupSettings: () => get('/console/backup/settings'),
  putBackupSettings: (body: Record<string, unknown>) => put('/console/backup/settings', body)
}
