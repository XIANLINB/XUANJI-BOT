// 浏览器端 CSV / JSON 导出工具（批次6：日志中心 / 事件日志 / 插件 KV / 后续通用）
// CSV 统一 UTF-8 BOM（Excel 直接打开中文不乱码）+ CRLF 换行 + 字段转义。

/** 通用 blob 下载（前端生成内容）。 */
export function downloadBlob(content: string, filename: string, mime: string): void {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/** CSV 字段转义：含逗号/引号/换行时双引号包裹，内部引号翻倍。 */
function csvEscape(v: unknown): string {
  const s = v == null ? '' : String(v)
  if (s.includes(',') || s.includes('"') || s.includes('\n') || s.includes('\r')) {
    return '"' + s.replace(/"/g, '""') + '"'
  }
  return s
}

/**
 * 导出 CSV：columns 定义列（key=取值字段，label=表头）。
 * 支持列值函数（value = (row) => string），用于时间格式化等。
 */
export function exportCsv(
  rows: Record<string, any>[],
  columns: { key: string; label: string; value?: (row: any) => unknown }[],
  filename: string
): void {
  const header = columns.map((c) => csvEscape(c.label)).join(',')
  const lines = rows.map((r) =>
    columns.map((c) => csvEscape(c.value ? c.value(r) : r[c.key])).join(',')
  )
  downloadBlob('\uFEFF' + header + '\r\n' + lines.join('\r\n'), filename, 'text/csv;charset=utf-8')
}

/** 导出 JSON（pretty 打印）。 */
export function exportJson(data: unknown, filename: string): void {
  downloadBlob(JSON.stringify(data, null, 2), filename, 'application/json;charset=utf-8')
}
