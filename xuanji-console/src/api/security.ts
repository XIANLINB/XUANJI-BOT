// 安全中心 API（修改口令 / 审计日志）
import { get, post, download } from './http'

export interface AuditFilter {
  action?: string
  ip?: string
  keyword?: string
  deviceType?: string
  startTime?: number
  endTime?: number
}

export const securityApi = {
  // 修改访问口令：{oldPin, newPin}
  changePin: (oldPin: string, newPin: string) => post('/console/security/pin', { oldPin, newPin }),
  // 审计日志（筛选：动作/IP/关键词/设备类型/时间范围）
  getAudit: (limit = 200, f: AuditFilter = {}) =>
    get('/console/security/audit', { limit, ...f }),
  // 审计动作清单（筛选下拉数据源）
  auditActions: () => get('/console/security/audit/actions'),
  // 导出（csv / json），带筛选
  exportAudit: (format: 'csv' | 'json', f: AuditFilter = {}) =>
    download('/console/security/audit/export', { format, ...f }, `audit.${format}`),
  clearAudit: () => post('/console/security/audit/clear')
}
