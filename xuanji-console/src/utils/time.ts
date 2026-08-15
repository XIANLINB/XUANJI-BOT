// 统一时间格式化工具 —— 全站替换散落的 dayjs(...).utcOffset(8).format(...) 调用。
// 格式由个性化设置驱动：12/24 小时制 + 日期格式（简洁/完整），改设置即时生效。
import dayjs from 'dayjs'
import { usePreferencesStore } from '../stores/preferences'

/** 规范化输入为 dayjs（支持 epoch 秒/毫秒、字符串、Date）。 */
function toDayjs(ts: number | string | Date): dayjs.Dayjs | null {
  if (ts == null || ts === '') return null
  let n = ts
  if (typeof n === 'number') {
    // epoch 秒（≤ 10 位）转毫秒
    if (n <= 9999999999) n = (n as number) * 1000
  } else if (typeof n === 'string' && /^\d+$/.test(n)) {
    const num = Number(n)
    if (num <= 9999999999) return dayjs(num * 1000)
    return dayjs(num)
  }
  return dayjs(n)
}

/**
 * 完整时间格式化：日期 + 时间，按个性化配置。
 * @param ts 时间戳/字符串/Date
 * @param opts 可选覆盖 dateFmt/timeFmt（不传走 preferences）
 */
export function fmtTime(ts: number | string | Date, opts?: { dateFmt?: string; timeFmt?: string }): string {
  const prefs = usePreferencesStore()
  const dateFmt = opts?.dateFmt ?? (prefs.prefs.dateFormat === 'short' ? 'MM-DD' : 'YYYY-MM-DD')
  const timeFmt = opts?.timeFmt ?? (prefs.prefs.timeFormat === '12h' ? 'hh:mm:ss A' : 'HH:mm:ss')
  const d = toDayjs(ts)
  if (!d || !d.isValid()) return String(ts ?? '')
  return d.utcOffset(8).format(`${dateFmt} ${timeFmt}`)
}

/** 仅时间（不含日期），用于日志/消息列表紧凑展示。 */
export function fmtTimeOnly(ts: number | string | Date): string {
  const prefs = usePreferencesStore()
  const fmt = prefs.prefs.timeFormat === '12h' ? 'hh:mm:ss A' : 'HH:mm:ss'
  const d = toDayjs(ts)
  if (!d || !d.isValid()) return String(ts ?? '')
  return d.utcOffset(8).format(fmt)
}

/** 仅日期，用于分组标签/表格列。 */
export function fmtDate(ts: number | string | Date): string {
  const prefs = usePreferencesStore()
  const fmt = prefs.prefs.dateFormat === 'short' ? 'MM-DD' : 'YYYY-MM-DD'
  const d = toDayjs(ts)
  if (!d || !d.isValid()) return String(ts ?? '')
  return d.utcOffset(8).format(fmt)
}

/** 文件名用时间戳（导出文件名，固定 24h YYYYMMDD-HHmmss）。 */
export function fmtStamp(ts: number | string | Date = Date.now()): string {
  const d = toDayjs(ts)
  if (!d || !d.isValid()) return String(ts ?? '')
  return d.utcOffset(8).format('YYYYMMDD-HHmmss')
}
