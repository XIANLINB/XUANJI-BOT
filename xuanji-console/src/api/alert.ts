// 预警中心 API
import { get, post, put } from './http'

export const alertApi = {
  listConfigs: () => get('/console/alert/config'),
  saveConfig: (botKey: string, enabled: boolean, alertUserId: string) =>
    put('/console/alert/config', { botKey, enabled, alertUserId }),
  records: (limit = 100) => get('/console/alert/records', { limit }),
  check: () => post('/console/alert/check')
}
