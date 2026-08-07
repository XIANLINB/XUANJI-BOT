// 群聊 / 单聊联系人（Pinia，批次11）——Chat / Scheduler / Settings 等页面共享，避免重复拉取。
import { defineStore } from 'pinia'
import api from '../api'

export const useContactsStore = defineStore('contacts', {
  state: () => ({
    groups: [] as any[],
    friends: [] as any[],
    loaded: false,
    loading: false,
    err: ''
  }),
  actions: {
    async loadContacts(force = false) {
      if (!force && this.loaded) return
      this.loading = true
      this.err = ''
      try {
        const [g, f] = await Promise.all([api.getGroups(), api.getFriends()])
        this.groups = g || []
        this.friends = f || []
        this.loaded = true
      } catch (e: any) {
        this.err = e?.message || String(e)
        this.groups = []
        this.friends = []
      } finally {
        this.loading = false
      }
    }
  }
})
