// 璇玑控制台 API 客户端 · 基础请求工具
//
// 全站唯一的后端地址定义处。后端侧对应 dev.xuanji.console.config.XuanjiApiRoutes.API_PREFIX，
// 两边必须同步；升级到 v2 时只改这一行 + 后端 XuanjiApiRoutes.API_VERSION。
// 注意：/actuator/** 是 Spring Boot Actuator 的固定路径，不在本前缀之下（见 getActuatorMetric）。
const BASE = '/xuanji/api/v1'

export function qs(params?: Record<string, unknown>): string {
  if (!params) return ''
  const s = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v != null && v !== '') s.set(k, String(v))
  })
  const str = s.toString()
  return str ? '?' + str : ''
}

export async function get<T = any>(path: string, params?: Record<string, unknown>): Promise<T> {
  const res = await fetch(BASE + path + qs(params), { credentials: 'include' })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

export async function post<T = any>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

export async function put<T = any>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

export async function del<T = any>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { method: 'DELETE', credentials: 'include' })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

/** 纯文本响应（如日志接口）。 */
export async function getText(path: string, params?: Record<string, unknown>): Promise<string> {
  const res = await fetch(BASE + path + qs(params), { credentials: 'include' })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.text()
}

/**
 * 文件下载（GET）：后端返回 Content-Disposition 附件流。取 blob + a[download] 触发下载，
 * 文件名优先响应头，缺省用 fallbackName。返回是否成功（非 2xx 抛错）。
 */
export async function download(path: string, params?: Record<string, unknown>, fallbackName = 'download'): Promise<void> {
  const res = await fetch(BASE + path + qs(params), { credentials: 'include' })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const blob = await res.blob()
  const cd = res.headers.get('Content-Disposition') || ''
  const m = cd.match(/filename="?([^";]+)"?/)
  const name = m ? m[1] : fallbackName
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

// actuator 指标：返回 measurements[0].value（失败返回 null，前端优雅降级）
export async function getActuatorMetric(name: string, tag?: string): Promise<number | null> {
  const url = `/actuator/metrics/${name}${tag ? '?tag=' + encodeURIComponent(tag) : ''}`
  try {
    const res = await fetch(url)
    if (!res.ok) return null
    const j = await res.json()
    return j?.measurements?.[0]?.value ?? null
  } catch {
    return null
  }
}

/**
 * 打开实时事件流（SSE）。返回 EventSource，路径经后端 XuanjiApiRoutes 前缀装配为
 * /xuanji/api/v1/console/stream。事件按 `event:` 字段区分类型（event / log / heartbeat），
 * 调用方用 es.addEventListener(type, handler) 订阅，es.close() 关闭。
 */
export function openStream(): EventSource {
  return new EventSource(BASE + '/console/stream')
}
