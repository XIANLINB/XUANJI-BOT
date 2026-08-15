// 插件管理 API
import { get, post, put, del, qs, upload } from './http'

export const pluginsApi = {
  getPlugins: () => get('/console/plugins'),
  /** 命令执行统计（插件市场页统计卡）：execCount/failCount/successRate/rateLimitHits。 */
  pluginCommandStats: () => get('/console/plugins/command-stats'),
  stopPlugin: (pluginId: string) => post(`/console/plugins/${encodeURIComponent(pluginId)}/stop`),
  startPlugin: (pluginId: string) => post(`/console/plugins/${encodeURIComponent(pluginId)}/start`),
  reloadPlugin: (pluginId: string) => post(`/console/plugins/${encodeURIComponent(pluginId)}/reload`),
  // 插件-机器人绑定
  listBindings: (pluginId: string) => get(`/console/plugins/${encodeURIComponent(pluginId)}/bindings`),
  bindPlugin: (pluginId: string, platform: string, botKey: string) =>
    post(`/console/plugins/${encodeURIComponent(pluginId)}/bindings`, { platform, botKey }),
  unbindPlugin: (pluginId: string, platform: string, botKey: string) =>
    del(`/console/plugins/${encodeURIComponent(pluginId)}/bindings` + qs({ platform, botKey })),
  // 插件配置面板（PluginConfigProvider schema）
  getPluginConfigSchema: (pluginId: string) => get(`/console/plugins/${encodeURIComponent(pluginId)}/config-schema`),
  getPluginConfig: (pluginId: string) => get(`/console/plugins/${encodeURIComponent(pluginId)}/config`),
  putPluginConfig: (pluginId: string, values: Record<string, string>) =>
    put(`/console/plugins/${encodeURIComponent(pluginId)}/config`, values),
  // 插件数据存储（KV 只读浏览 + 一键清空）
  getPluginKv: (pluginId: string) => get(`/console/plugins/${encodeURIComponent(pluginId)}/kv`),
  clearPluginKv: (pluginId: string) => post(`/console/plugins/${encodeURIComponent(pluginId)}/kv/clear`),
  // 插件结构化数据存储（方案 A：@PluginEntity 注解声明实体 + 框架自动建表）
  getPluginEntities: (pluginId: string) => get(`/console/plugins/${encodeURIComponent(pluginId)}/entities`),
  getPluginEntityDescribe: (pluginId: string, table: string) =>
    get(`/console/plugins/${encodeURIComponent(pluginId)}/entities/${encodeURIComponent(table)}/describe`),
  getPluginEntityRows: (
    pluginId: string,
    table: string,
    params: { page?: number; size?: number; orderBy?: string; desc?: boolean }
  ) => get(`/console/plugins/${encodeURIComponent(pluginId)}/entities/${encodeURIComponent(table)}/read`, params),
  // 运行时扫描 plugins 目录，加载新插件
  scanPlugins: () => post('/console/plugins/scan'),
  // 卸载插件（关闭容器 + 清理持久态 + 删 jar）
  unloadPlugin: (pluginId: string) => post(`/console/plugins/${encodeURIComponent(pluginId)}/unload`),

  // ===== 插件市场（中央插件库：浏览/上传/审核/安装） =====
  marketList: () => get('/console/market/plugins'),
  marketSettings: () => get('/console/market/settings'),
  saveMarketSettings: (body: any) => put('/console/market/settings', body),
  marketExtract: (form: FormData) => upload('/console/market/extract', form),
  marketSubmit: (form: FormData) => upload('/console/market/submit', form),
  mySubmissions: () => get('/console/market/submissions'),
  marketPending: () => get('/console/market/pending'),
  marketVerifyAdmin: (adminToken: string) => post('/console/market/pending/verify', { adminToken }),
  marketApprove: (id: string, official: boolean, category: string, adminToken: string) =>
    post(`/console/market/pending/${encodeURIComponent(id)}/approve`, { official, category, adminToken }),
  marketReject: (id: string, reason: string, adminToken: string) =>
    post(`/console/market/pending/${encodeURIComponent(id)}/reject`, { reason, adminToken }),
  marketAudit: () => get('/console/market/audit'),
  marketInstall: (pluginId: string, version: string) =>
    post('/console/market/install', { pluginId, version }),
  // 已上架（含已下架）插件列表 + 下架
  marketReleased: () => get('/console/market/released'),
  marketDelist: (pluginId: string, reason: string, adminToken: string) =>
    post(`/console/market/released/${encodeURIComponent(pluginId)}/delist`, { reason, adminToken }),
  marketRelist: (pluginId: string, adminToken: string) =>
    post(`/console/market/released/${encodeURIComponent(pluginId)}/relist`, { adminToken })
}
