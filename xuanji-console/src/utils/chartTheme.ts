// ECharts 主题适配 —— 从 Naive UI themeVars + 个性化品牌色派生图表配色，
// 随主题/品牌切换自动响应。CommonChart 及各页 ECharts option 用 applyChartTheme 注入。
import { computed } from 'vue'
import { useThemeVars } from 'naive-ui'
import { usePreferencesStore } from '../stores/preferences'
import { BRAND_PRESETS } from '../theme'

export interface ChartTheme {
  /** 系列色板（品牌主色 + 语义色，6 色） */
  series: string[]
  /** 主文字色（tooltip 标题等） */
  textColor: string
  /** 次文字色（坐标轴标签、图例） */
  subTextColor: string
  /** 坐标轴线色 */
  axisLineColor: string
  /** 分割线色 */
  splitLineColor: string
  /** tooltip 背景 */
  tooltipBg: string
  /** tooltip 边框 */
  tooltipBorder: string
}

/**
 * 响应式图表主题：返回 computed<ChartTheme>，主题/品牌变化时自动重算。
 * 在组件 setup 中调用，传给 applyChartTheme 或直接读取。
 */
export function useChartTheme() {
  const vars = useThemeVars()
  const prefs = usePreferencesStore()
  return computed<ChartTheme>(() => {
    const dark = prefs.isDark
    const b = BRAND_PRESETS[prefs.prefs.brand] ?? BRAND_PRESETS.indigo
    return {
      // 系列色：品牌主色领头，后续语义色保持稳定（多系列区分度）
      series: [b.primary, '#18a058', '#f0a020', '#e5484d', '#2090e0', '#722ed1'],
      textColor: dark ? 'rgba(255,255,255,0.85)' : 'rgba(0,0,0,0.85)',
      subTextColor: dark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.55)',
      axisLineColor: dark ? 'rgba(255,255,255,0.18)' : 'rgba(0,0,0,0.18)',
      splitLineColor: dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
      tooltipBg: dark ? 'rgba(30,30,30,0.95)' : 'rgba(255,255,255,0.95)',
      tooltipBorder: dark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.10)'
    }
  })
}

/**
 * 把图表主题注入 ECharts option：文字色、系列色、坐标轴/分割线、tooltip、图例。
 * 不破坏原有 option 结构（浅合并 + 补充缺失项）。
 */
export function applyChartTheme(option: any, t: ChartTheme): any {
  // 系列色：注入顶层 color（ECharts 按 series 顺序取色）
  option.color = option.color ?? t.series
  // tooltip
  option.tooltip = option.tooltip ?? {}
  option.tooltip.backgroundColor = option.tooltip.backgroundColor ?? t.tooltipBg
  option.tooltip.borderColor = option.tooltip.borderColor ?? t.tooltipBorder
  option.tooltip.textStyle = option.tooltip.textStyle ?? { color: t.textColor }
  // 文字
  option.textStyle = { color: t.textColor, ...(option.textStyle || {}) }
  // 坐标轴
  const injectAxis = (ax: any) => {
    if (!ax) return
    const arr = Array.isArray(ax) ? ax : [ax]
    arr.forEach((a: any) => {
      if (!a) return
      a.axisLine = a.axisLine ?? { lineStyle: { color: t.axisLineColor } }
      a.axisLabel = a.axisLabel ?? { color: t.subTextColor }
      a.splitLine = a.splitLine ?? { lineStyle: { color: t.splitLineColor } }
    })
  }
  injectAxis(option.xAxis)
  injectAxis(option.yAxis)
  // 图例
  if (option.legend) {
    option.legend.textStyle = option.legend.textStyle ?? { color: t.subTextColor }
  }
  return option
}
