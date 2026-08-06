// 璇玑控制台 API 客户端 · 基础请求工具
const BASE = '/xuanji/api'

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
  const res = await fetch(BASE + path + qs(params))
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

export async function post<T = any>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

export async function put<T = any>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

export async function del<T = any>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { method: 'DELETE' })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json() as Promise<T>
}

/** 纯文本响应（如日志接口）。 */
export async function getText(path: string, params?: Record<string, unknown>): Promise<string> {
  const res = await fetch(BASE + path + qs(params))
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.text()
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
