// 预警中心 API
import { get, post, put } from './http'

export const alertApi = {
  listConfigs: () => get('/console/alert/config'),
  saveConfig: (botKey: string, enabled: boolean, alertUserId: string, rules?: Record<string, any>) =>
    put('/console/alert/config', { botKey, enabled, alertUserId, rules }),
  records: (limit = 100) => get('/console/alert/records', { limit }),
  check: () => post('/console/alert/check'),
  settings: () => get('/console/alert/settings'),
  saveSettings: (s: { checkIntervalMs?: number; cooldownMinutes?: number }) =>
    put('/console/alert/settings', s)
}
