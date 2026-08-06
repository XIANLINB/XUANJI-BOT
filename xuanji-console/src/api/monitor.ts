// 仪表盘 / 健康 / 配置 API
import { get, put, del, getActuatorMetric } from './http'

export const monitorApi = {
  getDashboard: () => get('/console/dashboard'),
  getHealth: () => get('/console/health'),
  // 运行时配置快照：全局 KV + 每机器人配置 + 群级配置
  getConfig: () => get('/console/config'),
  putGlobalConfig: (body: Record<string, string>) => put('/console/config/global', body),
  putBotConfig: (botKey: string, body: Record<string, string>) =>
    put('/console/config/bot/' + encodeURIComponent(botKey), body),
  // 三级配置：机器人 + 群
  putGroupConfig: (botKey: string, groupId: string, body: Record<string, string>) =>
    put(`/console/config/group/${encodeURIComponent(botKey)}/${encodeURIComponent(groupId)}`, body),
  /** 删除单键（一键重置）：scope ∈ global/bot/group，groupId 仅 group 必填 */
  deleteConfigKey: (scope: 'global' | 'bot' | 'group', botKey: string, key: string, groupId?: string) =>
    del(`/console/config/${scope}/${encodeURIComponent(botKey)}/${encodeURIComponent(key)}` + (groupId ? `?groupId=${encodeURIComponent(groupId)}` : '')),
  getActuatorMetric
}
