// 机器人管理 API
import { get, post, del } from './http'

export const botsApi = {
  getBots: () => get('/console/bots'),
  getBot: (botKey: string) => get(`/console/bots/${encodeURIComponent(botKey)}`),
  stopBot: (botKey: string) => post(`/console/bots/${encodeURIComponent(botKey)}/stop`),
  startBot: (botKey: string) => post(`/console/bots/${encodeURIComponent(botKey)}/start`),
  // 机器人配置（BotConfigController）：新增/更新 → 热重载启用
  saveBot: (body: Record<string, string>) => post('/bot-config', body),
  reloadBots: () => post('/bot-config/reload'),
  deleteBot: (appId: string) => del('/bot-config/' + encodeURIComponent(appId))
}
