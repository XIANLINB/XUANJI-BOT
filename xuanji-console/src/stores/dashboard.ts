// 仪表盘全局状态（Pinia）——消息趋势数据与时间范围
import { defineStore } from 'pinia'
import api from '../api'

export interface TrendPoint {
  date: string
  c2c: number
  group: number
  total: number
}

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    /** 当前时间范围：7 / 15 / 30 天 */
    days: 7,
    trend: [] as TrendPoint[],
    loading: false,
    err: ''
  }),
  actions: {
    async loadTrend(days: number) {
      this.days = days
      this.loading = true
      this.err = ''
      try {
        const r = await api.getMessageTrend(days)
        this.trend = Array.isArray(r?.rows) ? (r.rows as TrendPoint[]) : []
      } catch (e: any) {
        this.err = e?.message || String(e)
        this.trend = []
      } finally {
        this.loading = false
      }
    }
  }
})
