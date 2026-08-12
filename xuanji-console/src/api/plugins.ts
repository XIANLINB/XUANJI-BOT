// 插件管理 API
import { get, post, put, del, qs, upload } from './http'

export const pluginsApi = {
  getPlugins: () => get('/console/plugins'),
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
  // 运行时扫描 plugins 目录，加载新插件
  scanPlugins: () => post('/console/plugins/scan'),
  // 卸载插件（关闭容器 + 清理持久态 + 删 jar）
  unloadPlugin: (pluginId: string) => post(`/console/plugins/${encodeURIComponent(pluginId)}/unload`),

  // ===== 插件市场（中央插件库：浏览/上传/审核/安装） =====
  marketList: () => get('/console/market/plugins'),
  marketSettings: () => get('/console/market/settings'),
  saveMarketSettings: (body: any) => put('/console/market/settings', body),
  marketSubmit: (form: FormData) => upload('/console/market/submit', form),
  mySubmissions: () => get('/console/market/submissions'),
  marketPending: () => get('/console/market/pending'),
  marketApprove: (id: string, official: boolean) =>
    post(`/console/market/pending/${encodeURIComponent(id)}/approve` + qs({ official })),
  marketReject: (id: string, reason: string) =>
    post(`/console/market/pending/${encodeURIComponent(id)}/reject`, { reason }),
  marketInstall: (pluginId: string, version: string) =>
    post('/console/market/install', { pluginId, version })
}
