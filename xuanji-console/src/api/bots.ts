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
  // 变动数据（统计卡用）。bot 既可为 botKey 也可为 appId（后端 resolveAppId 兜底）
  getBotGroupVariation: (bot: string) => get(`/console/bots/${encodeURIComponent(bot)}/group-variation`),
  getBotFriendVariation: (bot: string) => get(`/console/bots/${encodeURIComponent(bot)}/friend-variation`),
  // 全机器人聚合群变动（不选机器人时统计卡显示全量）
  getGroupsVariationAll: () => get('/console/bots/group-variation'),
  // 全机器人聚合单聊用户变动
  getFriendsVariationAll: () => get('/console/bots/friend-variation'),
  // 生成机器人分享链接（callbackData 可选，≤32 字符，添加机器人时透传给开发者）
  generateShareLink: (botKey: string, callbackData?: string) =>
    post(`/console/bots/${encodeURIComponent(botKey)}/share-link`, { callbackData: callbackData || '' })
}