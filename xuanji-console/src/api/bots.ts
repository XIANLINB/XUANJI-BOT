// 机器人管理 API
import { get, post, put, del } from './http'

export const botsApi = {
  getBots: () => get('/console/bots'),
  getBot: (botKey: string) => get(`/console/bots/${encodeURIComponent(botKey)}`),
  stopBot: (botKey: string) => post(`/console/bots/${encodeURIComponent(botKey)}/stop`),
  startBot: (botKey: string) => post(`/console/bots/${encodeURIComponent(botKey)}/start`),
  // 切换连接方式（websocket ↔ webhook），成功后 stop+start 自动重启连接
  updateConnMode: (botKey: string, mode: 'websocket' | 'webhook') =>
    put(`/console/bots/${encodeURIComponent(botKey)}/conn-mode?mode=${mode}`),
  // webhook 模式所需的回调域名（写 xuanji_bot_setting.webhookUrl）
  saveBotConfig: (botKey: string, body: Record<string, string>) =>
    put(`/console/config/bot/${encodeURIComponent(botKey)}`, body),
  // 机器人配置（BotConfigController）：新增/更新 → 热重载启用
  saveBot: (body: Record<string, string>) => post('/bot-config', body),
  reloadBots: () => post('/bot-config/reload'),
  deleteBot: (appId: string) => del('/bot-config/' + encodeURIComponent(appId)),
  // 删除并归档（防误删，30 天内可恢复）
  archives: () => get('/bot-config/archives'),
  restoreBot: (id: number | string) => post(`/bot-config/archives/${id}/restore`),
  // 变动数据（统计卡用）
  getBotGroupVariation: (botKey: string) => get(`/console/bots/${encodeURIComponent(botKey)}/group-variation`),
  getBotFriendVariation: (botKey: string) => get(`/console/bots/${encodeURIComponent(botKey)}/friend-variation`)
}