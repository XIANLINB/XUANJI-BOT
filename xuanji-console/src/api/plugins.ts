// 插件管理 API
import { get, post, del, qs } from './http'

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
    del(`/console/plugins/${encodeURIComponent(pluginId)}/bindings` + qs({ platform, botKey }))
}
