// 全局机器人列表（Pinia，批次11）——所有页面共享，避免重复请求 /console/bots。
import { defineStore } from 'pinia'
import api from '../api'

export interface BotRow {
  appId: string
  botKey?: string
  name?: string
  avatar?: string
  status?: string
  connectionType?: string
  domain?: string
  groupsTotal?: number
}

export const useBotsStore = defineStore('bots', {
  state: () => ({
    bots: [] as BotRow[],
    loaded: false,
    loading: false,
    err: ''
  }),
  getters: {
    /** 机器人下拉选项（label 优先显示名，兜底 Bot #appId）。 */
    botOptions: (s) =>
      s.bots.map((b) => ({ label: (b.name || '') ? b.name as string : `Bot #${b.appId}`, value: b.appId })),
    /** appId → 显示名（名称缺失回退 Bot #appId）。 */
    nameMap: (s) => new Map<string, string>(s.bots.map((b) => [String(b.appId), (b.name || '') ? b.name as string : `Bot #${b.appId}`]))
  },
  actions: {
    async loadBots(force = false) {
      if (!force && this.loaded) return
      this.loading = true
      this.err = ''
      try {
        const list: any[] = await api.getBots()
        this.bots = (list || []).map((b) => ({
          appId: String(b.appId ?? b.botKey ?? ''),
          botKey: b.botKey ? String(b.botKey) : undefined,
          name: b.name ? String(b.name) : undefined,
          avatar: b.avatar ? String(b.avatar) : undefined,
          status: b.status ? String(b.status) : undefined,
          connectionType: b.connectionType ? String(b.connectionType) : undefined,
          domain: b.domain ? String(b.domain) : undefined,
          groupsTotal: b.groupsTotal != null ? Number(b.groupsTotal) : undefined
        })).filter((b) => b.appId)
        this.loaded = true
      } catch (e: any) {
        this.err = e?.message || String(e)
        this.bots = []
      } finally {
        this.loading = false
      }
    }
  }
})
