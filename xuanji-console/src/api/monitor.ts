// 仪表盘 / 健康 / 配置 API
import { get, put, del, getActuatorMetric } from './http'

export const monitorApi = {
  getDashboard: () => get('/console/dashboard'),
  getHealth: () => get('/console/health'),
  // 运行监控指标：系统资源 + QPS + 线程池 + 框架统计（真实数据）
  getMetricsOverview: () => get('/console/metrics/overview'),
  // QPS 逐秒曲线（默认近 60 秒）
  getMetricsQps: (seconds = 60) => get('/console/metrics/qps', { seconds }),
  // 性能模板推荐：mode = eco（经济）| perf（性能）
  getTuneRecommend: (mode: 'eco' | 'perf' = 'eco') => get('/console/tune/recommend', { mode }),
  // 命令清单（命令管理页）
  getCommands: () => get('/console/commands'),
  // 框架版本日志（仪表盘时间线）
  getVersionLog: () => get('/console/version-log'),
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
