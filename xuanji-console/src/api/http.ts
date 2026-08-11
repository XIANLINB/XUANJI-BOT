// 璇玑控制台 API 客户端 · 基础请求工具
//
// 全站唯一的后端地址定义处。后端侧对应 dev.xuanji.console.config.XuanjiApiRoutes.API_PREFIX，
// 两边必须同步；升级到 v2 时只改这一行 + 后端 XuanjiApiRoutes.API_VERSION。
// 注意：/actuator/** 是 Spring Boot Actuator 的固定路径，不在本前缀之下（见 getActuatorMetric）。
const BASE = '/xuanji/api/v1'

/**
 * 后端统一错误响应体的常见字段（见 console-server 全局异常处理器）。
 * 不同接口可能返回其中任意组合，解析时按优先级兜底。
 */
export interface ApiErrorBody {
  code?: string
  error?: string
  message?: string
  msg?: string
  detail?: unknown
}

/**
 * 结构化错误：非 2xx 响应统一抛此类型，调用方可用 `err instanceof ApiError`
 * 精确捕获并读取 status / code / detail（如字段级校验错误），同时仍兼容 `err.message`。
 */
export class ApiError extends Error {
  status: number
  code?: string
  detail?: unknown
  constructor(status: number, message: string, code?: string, detail?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
  }
}

/**
 * 将非 2xx 的 Response 解析为 ApiError：优先按 JSON 读取 { code, message/error/msg, detail }，
 * 解析失败或纯文本时回退到文本；都拿不到则用 `HTTP <status>` 兜底。
 */
async function toApiError(res: Response): Promise<ApiError> {
  let body: ApiErrorBody | null = null
  const ct = res.headers.get('Content-Type') || ''
  try {
    if (ct.includes('application/json')) {
      body = (await res.json()) as ApiErrorBody
    } else {
      const text = await res.text()
      if (text) body = { message: text }
    }
  } catch {
    // 响应体无法读取时，保留 null，下面走兜底
  }
  const message =
    (body && (body.message || body.error || body.msg)) ||
    (body ? JSON.stringify(body) : '') ||
    `HTTP ${res.status}`
  return new ApiError(res.status, message, body?.code, body?.detail)
}

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
  if (!res.ok) throw await toApiError(res)
  return res.json() as Promise<T>
}

export async function post<T = any>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw await toApiError(res)
  return res.json() as Promise<T>
}

export async function put<T = any>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw await toApiError(res)
  return res.json() as Promise<T>
}

/** multipart 文件上传（FormData；不手动设 Content-Type，浏览器自动带 boundary）。 */
export async function upload<T = any>(path: string, form: FormData): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'POST',
    credentials: 'include',
    body: form
  })
  if (!res.ok) throw await toApiError(res)
  return res.json() as Promise<T>
}

export async function del<T = any>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { method: 'DELETE', credentials: 'include' })
  if (!res.ok) throw await toApiError(res)
  return res.json() as Promise<T>
}

/** 纯文本响应（如日志接口）。 */
export async function getText(path: string, params?: Record<string, unknown>): Promise<string> {
  const res = await fetch(BASE + path + qs(params), { credentials: 'include' })
  if (!res.ok) throw await toApiError(res)
  return res.text()
}

/**
 * 文件下载（GET）：后端返回 Content-Disposition 附件流。取 blob + a[download] 触发下载，
 * 文件名优先响应头，缺省用 fallbackName。返回是否成功（非 2xx 抛错）。
 */
export async function download(path: string, params?: Record<string, unknown>, fallbackName = 'download'): Promise<void> {
  const res = await fetch(BASE + path + qs(params), { credentials: 'include' })
  if (!res.ok) throw await toApiError(res)
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
