import type { GlobalThemeOverrides } from 'naive-ui'
import { DEFAULT_PREFERENCES, type Preferences, type BrandPreset } from './stores/preferences'

/**
 * 品牌色预设：4 套主题色板，覆盖 indigo(默认)/blue/green/pink。
 * 每套含主色 + hover + pressed + suppl（按钮、链接、选中态等统一来源）。
 */
export const BRAND_PRESETS: Record<BrandPreset, {
  primary: string; hover: string; pressed: string; suppl: string
}> = {
  indigo: { primary: '#5b5bd6', hover: '#6f6fea', pressed: '#4a4ac4', suppl: '#6f6fea' },
  blue:   { primary: '#2090e0', hover: '#3aa5f0', pressed: '#1a7cc4', suppl: '#3aa5f0' },
  green:  { primary: '#18a058', hover: '#1cb365', pressed: '#148c4d', suppl: '#1cb365' },
  pink:   { primary: '#d03050', hover: '#e04060', pressed: '#b82848', suppl: '#e04060' }
}

/** 语义色（与品牌无关，全主题稳定：成功/警告/错误/信息）。 */
const SEMANTIC = {
  success: '#18a058',
  warning: '#f0a020',
  error: '#e5484d',
  info: '#2090e0'
}

/** 向后兼容：默认靛紫品牌色（未接入 preferences 的旧引用可继续 import）。 */
export const brand = {
  primary: BRAND_PRESETS.indigo.primary,
  primaryHover: BRAND_PRESETS.indigo.hover,
  primaryPressed: BRAND_PRESETS.indigo.pressed,
  primarySuppl: BRAND_PRESETS.indigo.suppl,
  success: SEMANTIC.success,
  warning: SEMANTIC.warning,
  error: SEMANTIC.error,
  info: SEMANTIC.info
}

/**
 * 根据个性化配置动态生成 themeOverrides。
 * 亮/暗对称：品牌色、语义色、圆角、字号、密度统一注入；暗色不再「只定主色」。
 *
 * @param prefs 个性化配置
 * @param isDark 当前是否深色（决定是否应用暗色微调）
 */
export function buildThemeOverrides(prefs: Preferences, isDark: boolean): GlobalThemeOverrides {
  const b = BRAND_PRESETS[prefs.brand] ?? BRAND_PRESETS.indigo
  const fontSize = `${prefs.fontSize}px`
  const compact = prefs.density === 'compact'
  // 密度：紧凑模式缩小卡片内边距
  const cardPadding = compact ? '14px' : '20px'

  const common = {
    primaryColor: b.primary,
    primaryColorHover: b.hover,
    primaryColorPressed: b.pressed,
    primaryColorSuppl: b.suppl,
    successColor: SEMANTIC.success,
    warningColor: SEMANTIC.warning,
    errorColor: SEMANTIC.error,
    infoColor: SEMANTIC.info,
    borderRadius: '10px',
    fontSize,
    cubicBezierEaseInOut: 'cubic-bezier(0.4, 0, 0.2, 1)'
  }

  // 亮/暗共用同一套品牌+语义+圆角+字号；暗色仅微调 body 背景由 NGlobalStyle 处理
  const base: GlobalThemeOverrides = {
    common,
    Card: { borderRadius: '14px', paddingMedium: cardPadding },
    DataTable: { borderRadius: '10px' },
    Button: { borderRadius: '9px', fontWeight: '600' },
    Input: { borderRadius: '9px' }
  }

  if (isDark) {
    // 暗色与亮色对称（语义色/品牌色一致），保证切换不丢样式
    return { ...base, common: { ...common } }
  }
  return base
}

/** 向后兼容：默认靛紫的亮/暗 overrides（未接入 preferences 的入口可用）。 */
export const lightOverrides: GlobalThemeOverrides = buildThemeOverrides(
  { ...DEFAULT_PREFERENCES, themeMode: 'light' },
  false
)
export const darkOverrides: GlobalThemeOverrides = buildThemeOverrides(
  { ...DEFAULT_PREFERENCES, themeMode: 'dark' },
  true
)
