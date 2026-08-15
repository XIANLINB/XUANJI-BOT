// 个性化设置 store（Pinia）——配置持久化到后端 xuanji_config 表的 console.preferences 键
// （JSON 字符串），跨设备同步、重启不丢；复用现有 /console/config 接口，后端零改动。
import { defineStore } from 'pinia'
import api from '../api'

export type ThemeMode = 'light' | 'dark' | 'system'
export type BrandPreset = 'indigo' | 'blue' | 'green' | 'pink'
export type Density = 'comfortable' | 'compact'
export type SidebarMode = 'expanded' | 'collapsed' | 'auto'
export type TimeFormat = '24h' | '12h'
export type DateFormat = 'short' | 'full'
export type Motion = 'smooth' | 'moderate' | 'off'

export interface Preferences {
  themeMode: ThemeMode
  brand: BrandPreset
  fontSize: number        // 13 / 14 / 16
  density: Density
  sidebarMode: SidebarMode
  homeRoute: string       // 登录后默认落地页路由 name
  timeFormat: TimeFormat
  dateFormat: DateFormat
  motion: Motion
  confirmDestructive: boolean
}

export const DEFAULT_PREFERENCES: Preferences = {
  themeMode: 'light',
  brand: 'indigo',
  fontSize: 14,
  density: 'comfortable',
  sidebarMode: 'expanded',
  homeRoute: 'dashboard',
  timeFormat: '24h',
  dateFormat: 'full',
  motion: 'smooth',
  confirmDestructive: true
}

const STORAGE_KEY = 'console.preferences'

export const usePreferencesStore = defineStore('preferences', {
  state: () => ({
    loaded: false,
    saving: false,
    /** 系统深色偏好（themeMode='system' 时用，由 App.vue 监听 matchMedia 同步） */
    systemDark: false,
    prefs: { ...DEFAULT_PREFERENCES } as Preferences
  }),
  getters: {
    isDark: (s): boolean => {
      if (s.prefs.themeMode === 'dark') return true
      if (s.prefs.themeMode === 'light') return false
      return s.systemDark
    }
  },
  actions: {
    /** 从后端加载个性化配置；失败用默认值（不影响应用启动）。 */
    async load() {
      try {
        const cfg = await api.getConfig()
        const raw = cfg?.global?.[STORAGE_KEY]
        if (raw) {
          const parsed = JSON.parse(raw) as Partial<Preferences>
          this.prefs = { ...DEFAULT_PREFERENCES, ...parsed }
        }
      } catch {
        // 拉取失败（如未登录/接口异常）用默认值
      } finally {
        this.loaded = true
      }
    },
    /** 持久化到后端 xuanji_config。 */
    async save() {
      this.saving = true
      try {
        await api.putGlobalConfig({ [STORAGE_KEY]: JSON.stringify(this.prefs) })
      } finally {
        this.saving = false
      }
    },
    /** 更新单项并立即持久化（设置页「改即生效」）。 */
    async update(patch: Partial<Preferences>) {
      this.prefs = { ...this.prefs, ...patch }
      await this.save()
    },
    /** 重置为出厂默认。 */
    async reset() {
      this.prefs = { ...DEFAULT_PREFERENCES }
      await this.save()
    },
    /** 导出当前配置为 JSON 字符串（导入导出用）。 */
    exportJson(): string {
      return JSON.stringify(this.prefs, null, 2)
    },
    /** 导入配置 JSON（合并校验后持久化）。 */
    async importJson(json: string) {
      const parsed = JSON.parse(json) as Partial<Preferences>
      this.prefs = { ...DEFAULT_PREFERENCES, ...parsed }
      await this.save()
    }
  }
})
