import type { GlobalThemeOverrides } from 'naive-ui'

// 品牌色：科技感靛紫，胜默认蓝，配合亮/暗双主题统一观感。
export const brand = {
  primary: '#5b5bd6',
  primaryHover: '#6f6fea',
  primaryPressed: '#4a4ac4',
  primarySuppl: '#6f6fea',
  success: '#18a058',
  warning: '#f0a020',
  error: '#e5484d',
  info: '#2090e0'
}

export const lightOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: brand.primary,
    primaryColorHover: brand.primaryHover,
    primaryColorPressed: brand.primaryPressed,
    primaryColorSuppl: brand.primarySuppl,
    successColor: brand.success,
    warningColor: brand.warning,
    errorColor: brand.error,
    infoColor: brand.info,
    borderRadius: '10px',
    fontSize: '14px',
    cubicBezierEaseInOut: 'cubic-bezier(0.4, 0, 0.2, 1)'
  },
  Card: { borderRadius: '14px', paddingMedium: '20px' },
  DataTable: { borderRadius: '10px' },
  Button: { borderRadius: '9px', fontWeight: '600' },
  Input: { borderRadius: '9px' }
}

export const darkOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: brand.primary,
    primaryColorHover: brand.primaryHover,
    primaryColorPressed: brand.primaryPressed,
    primaryColorSuppl: brand.primarySuppl,
    borderRadius: '10px'
  },
  Card: { borderRadius: '14px' },
  DataTable: { borderRadius: '10px' }
}
