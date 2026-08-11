// LLM 模块 API（AI 能力 → AI 设置 / AI 对话 / 人格管理）
import { get, post, del, upload } from './http'

export interface LlmProviderInfo {
  id: string
  name: string
  capabilities: string[]
  defaultModel: string
}

export interface LlmConfig {
  providerId: string
  apiKey: string
  baseUrl: string
  model: string
  // 能力绑定（多供应商多模型：每能力选供应商+模型，空=用上方旧配置）
  chatProviderId?: number | null
  chatModel?: string | null
  visionProviderId?: number | null
  visionModelBinding?: string | null
  imageProviderId?: number | null
  imageModelBinding?: string | null
  ttsProviderId?: number | null
  ttsModelBinding?: string | null
  // 多选能力绑定（"providerId:modelName" 列表，按序尝试，主选+备选）
  chatBindings?: string[] | null
  visionBindings?: string[] | null
  imageBindings?: string[] | null
  ttsBindings?: string[] | null
  temperature: number
  maxTokens: number
  enabled: boolean
  groupIds: string[]
  mentionRequired: boolean
  dailyTokenLimit: number
  cooldownSeconds: number
  intentRouting: boolean
  aiAudit: boolean
  // P1.6 用户画像
  profileEnabled: boolean
  profileExtractHours: number
  profileExtractMsgThreshold: number
  // P1.6 主动搭话
  proactiveEnabled: boolean
  proactiveDailyLimit: number
  proactiveCooldownMinutes: number
  proactiveIdleMinutes: number
  proactiveTimeStart: string
  proactiveTimeEnd: string
  // P4 多模态行为参数（凭据已迁至「供应商管理」+「能力选择」）
  ttsVoice: string
  ttsStylePrompt: string
  ttsAudioFormat: string

  // P4+ TTS 备选（Fish Audio S2.1 Pro 免费）行为参数
  fishVoice: string
  fishStylePrompt: string
  fishSpeed: number
  // 图文卡片渲染（Playwright，可选）
  renderEnabled: boolean
  // AI 能力总开关（高级页）
  toolCallingEnabled?: boolean
  toolConfirmRequired?: boolean
  mcpEnabled?: boolean
  visionEnabled?: boolean
  imageGenEnabled?: boolean
  videoUnderstandEnabled?: boolean
  videoGenEnabled?: boolean
  ttsEnabled?: boolean
  voiceCloneEnabled?: boolean
  dailyReportEnabled?: boolean
  // 单聊维度
  c2cEnabled?: boolean
  c2cUserIds?: string[]
  c2cDailyTokenLimit?: number
  c2cCooldownSeconds?: number
}

export interface LlmTestResult {
  ok: boolean
  reply?: string
  error?: string
}

export interface LlmPersona {
  id: number
  scope: 'BOT' | 'GROUP' | 'USER'
  botKey: string
  groupId?: string | null
  userId?: string | null
  name?: string
  age?: string
  gender?: string
  personality?: string
  background?: string
  scenario?: string
  speechStyle?: string
  firstMes?: string
  mesExample?: string
  systemExtra?: string
  legacyPersona?: string
  anchors?: string
  roleplayMode?: boolean
  updatedAt?: string | null
}

export interface LlmMemoryRow {
  id: number
  botKey: string
  groupId?: string | null
  userId?: string | null
  key: string
  value: string
  type?: string
  updatedAt?: string | null
  expired?: boolean
}

export interface UserProfileRow {
  botKey: string
  groupId: string
  userId: string
  nickname?: string | null
  role?: string | null
  summary?: string | null
  style?: string | null
  msgCount: number
  lastSeen?: string | null
}

export interface ProactiveLogRow {
  id: number
  botKey: string
  groupId?: string | null
  userId?: string | null
  type: string
  content: string
  createdAt?: string | null
}

export interface LlmToolInfo {
  name: string
  description: string
  descriptionZh?: string
  confirm: boolean
  source: string
  parameters?: Record<string, any>
}

export interface McpServerRow {
  botKey: string
  name: string
  url: string
  description?: string | null
  whitelist: boolean
  enabled: boolean
  updatedAt?: string | null
}

export interface KbDocRow {
  id: number
  botKey: string
  name?: string | null
  charCount: number
  chunkCount: number
  createdAt?: string | null
}

export interface AuditLogRow {
  id: number
  botKey?: string | null
  groupId?: string | null
  userId?: string | null
  text?: string | null
  action?: string | null
  reason?: string | null
  createdAt?: string | null
}

export interface SummaryConfigRow {
  botKey: string
  groupId: string
  enabled: boolean
  runHour: number
  runMinute: number
  imageMode?: boolean
}

export interface SummaryLogRow {
  id: number
  botKey?: string | null
  groupId?: string | null
  content?: string | null
  createdAt?: string | null
}

export interface UsageOverview {
  totalToday: number
  bots: { botKey: string; tokens: number }[]
}

export interface GroupUsageRow {
  groupId: string
  dailyLimit: number
  todayUsed: number
  updatedAt?: string | null
  trend?: { day: number; tokens: number }[]
}

const BASE = '/xuanji/api/v1'

export interface ProviderRow {
  id: number
  name: string
  providerType: string
  baseUrl: string | null
  apiKey: string
  status: number
  modelCount?: number
}

export interface ModelRow {
  id: number
  providerId: number
  modelName: string
  capabilities: string
  enabled: number
}

export interface ApiKeyRow {
  id: number
  providerId: number
  apiKey: string
  remark?: string | null
  enabled: number
}

export const llmApi = {
  /** 供应商清单（下拉 + 能力矩阵） */
  providers: () => get<LlmProviderInfo[]>('/console/llm/providers'),
  /** 当前 LLM 配置 */
  getConfig: () => get<LlmConfig>('/console/llm/config'),
  /** 保存 LLM 配置 */
  saveConfig: (config: LlmConfig) => post<{ ok: boolean }>('/console/llm/config', config),
  /** 连通性测试 */
  test: () => post<LlmTestResult>('/console/llm/test'),

  /** 某 bot 的人格列表 */
  personas: (botKey?: string) => get<LlmPersona[]>(`/console/llm/personas${botKey ? `?botKey=${encodeURIComponent(botKey)}` : ''}`),
  /** 保存人格（UPSERT，结构化） */
  savePersona: (p: Partial<LlmPersona>) => post<{ ok: boolean }>('/console/llm/personas', p),
  /** 删除人格 */
  deletePersona: (id: number) => del<{ ok: boolean }>(`/console/llm/personas/${id}`),
  /** 内置人格模版 */
  personaTemplates: () => get<LlmPersona[]>('/console/llm/persona-templates'),

  /** 用量总览 */
  usageOverview: () => get<UsageOverview>('/console/llm/usage/overview'),
  /** 群用量 + 限额 + 趋势 */
  usageGroups: (botKey: string, days = 7) =>
    get<GroupUsageRow[]>(`/console/llm/usage/groups?botKey=${encodeURIComponent(botKey)}&days=${days}`),
  /** 设置群每日限额 */
  setGroupQuota: (botKey: string, groupId: string, dailyLimit: number) =>
    post<{ ok: boolean }>('/console/llm/group-quota', { botKey, groupId, dailyLimit }),

  /** 记忆列表 */
  memory: (botKey: string, groupId?: string, userId?: string) =>
    get<LlmMemoryRow[]>(`/console/llm/memory?botKey=${encodeURIComponent(botKey)}${groupId ? `&groupId=${encodeURIComponent(groupId)}` : ''}${userId ? `&userId=${encodeURIComponent(userId)}` : ''}`),
  /** 新增记忆 */
  saveMemory: (m: { botKey: string; groupId?: string; userId?: string; key: string; value: string }) =>
    post<{ ok: boolean }>('/console/llm/memory', m),
  /** 删除记忆 */
  deleteMemory: (id: number) => del<{ ok: boolean }>(`/console/llm/memory/${id}`),

  /** 用户画像列表 */
  userProfiles: (botKey?: string, groupId?: string) =>
    get<UserProfileRow[]>(`/console/llm/user-profiles${botKey ? `?botKey=${encodeURIComponent(botKey)}` : ''}${groupId ? `&groupId=${encodeURIComponent(groupId)}` : ''}`),
  /** 删除用户画像 */
  deleteUserProfile: (botKey: string, groupId: string, userId: string) =>
    del<{ ok: boolean }>(`/console/llm/user-profiles?botKey=${encodeURIComponent(botKey)}&groupId=${encodeURIComponent(groupId)}&userId=${encodeURIComponent(userId)}`),

  /** 主动搭话记录 */
  proactiveLogs: (botKey?: string, limit = 50) =>
    get<ProactiveLogRow[]>(`/console/llm/proactive-logs${botKey ? `?botKey=${encodeURIComponent(botKey)}` : ''}&limit=${limit}`),
  /** 主动搭话测试触发 */
  proactiveTest: (botKey: string, groupId: string) =>
    post<{ ok: boolean; type?: string; error?: string }>('/console/llm/proactive/test', { botKey, groupId }),

  /** LLM 工具清单（@LlmTool） */
  tools: () => get<LlmToolInfo[]>('/console/llm/tools'),

  /** MCP server 列表 */
  mcpList: (botKey?: string) =>
    get<McpServerRow[]>(`/console/llm/mcp${botKey ? `?botKey=${encodeURIComponent(botKey)}` : ''}`),
  /** 注册/更新 MCP server */
  mcpRegister: (m: { botKey: string; name: string; url: string; description?: string; whitelist?: boolean; enabled?: boolean }) =>
    post<{ ok: boolean }>('/console/llm/mcp', m),
  /** 删除 MCP server */
  mcpDelete: (botKey: string, name: string) =>
    del<{ ok: boolean }>(`/console/llm/mcp?botKey=${encodeURIComponent(botKey)}&name=${encodeURIComponent(name)}`),
  /** 连接 MCP server（注册工具） */
  mcpConnect: (botKey: string, name: string) =>
    post<{ ok: boolean; tools?: number }>('/console/llm/mcp/connect', { botKey, name }),
  /** 断开 MCP server */
  mcpDisconnect: (botKey: string, name: string) =>
    post<{ ok: boolean }>('/console/llm/mcp/disconnect', { botKey, name }),

  // ── 知识库（P4） ──
  kbUpload: (m: { botKey: string; name?: string; content: string }) =>
    post<{ ok: boolean; id: number }>('/console/llm/kb/upload', m),
  kbUploadFile: (botKey: string, file: File) => {
    const form = new FormData()
    form.append('botKey', botKey)
    form.append('file', file)
    return upload<{ ok: boolean; id: number; name?: string; error?: string }>('/console/llm/kb/upload-file', form)
  },
  kbList: (botKey?: string) =>
    get<KbDocRow[]>(`/console/llm/kb${botKey ? `?botKey=${encodeURIComponent(botKey)}` : ''}`),
  kbDelete: (id: number) => del<{ ok: boolean }>(`/console/llm/kb/${id}`),
  kbSearch: (botKey: string, q: string) =>
    get<{ result: string }>(`/console/llm/kb/search?botKey=${encodeURIComponent(botKey)}&q=${encodeURIComponent(q)}`),

  // ── AI 审核（P4） ──
  auditLogs: (limit = 100) =>
    get<AuditLogRow[]>(`/console/llm/audit/logs?limit=${limit}`),

  // ── AI 日报（P4） ──
  summaryConfigs: (botKey: string) =>
    get<SummaryConfigRow[]>(`/console/llm/summary/config?botKey=${encodeURIComponent(botKey)}`),
  summarySave: (m: { botKey: string; groupId: string; enabled: boolean; hour: number; minute: number; imageMode?: boolean }) =>
    post<{ ok: boolean }>('/console/llm/summary/config', m),
  summaryDelete: (botKey: string, groupId: string) =>
    post<{ ok: boolean }>('/console/llm/summary/config/delete', { botKey, groupId }),
  summaryGenerate: (botKey: string, groupId: string) =>
    post<{ ok: boolean; content: string }>('/console/llm/summary/generate', { botKey, groupId }),
  summaryHistory: (limit = 50) =>
    get<SummaryLogRow[]>(`/console/llm/summary/history?limit=${limit}`),

  // ── 图文卡片渲染（HtmlRenderService） ──
  renderTemplates: () => get<string[]>('/console/llm/render/templates'),
  renderPreview: (m: { templateId: string; data?: Record<string, any>; botKey?: string; groupId?: string; groupName?: string; summary?: string }) =>
    post<{ ok: boolean; templateId?: string; size?: number; base64?: string; error?: string }>('/console/llm/render/preview', m),

  // ── 用户反馈（P2-F：👍/👎 → 偏好蒸馏） ──
  feedback: (m: { botKey?: string; groupId?: string; userId?: string; replyText: string; score: 1 | -1 }) =>
    post<{ ok: boolean; error?: string }>('/console/llm/feedback', m),

  // ── 供应商 / 模型管理（多供应商多模型） ──
  providerList: () => get<ProviderRow[]>('/console/llm/providers-config'),
  providerSave: (m: { id?: number; name: string; providerType: string; baseUrl: string; apiKey: string; status?: number }) =>
    post<{ ok: boolean; id: number }>('/console/llm/providers-config', m),
  providerDelete: (id: number) => del<{ ok: boolean }>(`/console/llm/providers-config/${id}`),
  modelList: (providerId: number) =>
    get<ModelRow[]>(`/console/llm/providers-config/models?providerId=${providerId}`),
  modelSave: (m: { providerId: number; modelName: string; capabilities: string }) =>
    post<{ ok: boolean; id: number }>('/console/llm/providers-config/models', m),
  modelDelete: (id: number) => del<{ ok: boolean }>(`/console/llm/providers-config/models/${id}`),
  fetchModels: (id: number) =>
    post<{ ok: boolean; models?: string[]; error?: string }>(`/console/llm/providers-config/${id}/fetch-models`),
  // 供应商多 API Key
  keyList: (providerId: number) =>
    get<ApiKeyRow[]>(`/console/llm/providers-config/keys?providerId=${providerId}`),
  keySave: (m: { providerId: number; apiKey: string; remark?: string }) =>
    post<{ ok: boolean; id: number }>('/console/llm/providers-config/keys', m),
  keyDelete: (id: number) => del<{ ok: boolean }>(`/console/llm/providers-config/keys/${id}`)
}

export interface ChatStreamHandlers {
  onDelta: (piece: string) => void
  onDone?: () => void
  onError?: (err: string) => void
  signal?: AbortSignal
}

/** SSE 流式对话：逐段回调增量文本，结束回调 done，出错回调 error。 */
export async function chatStream(
  body: { botKey?: string; message: string },
  handlers: ChatStreamHandlers
): Promise<void> {
  try {
    const res = await fetch(BASE + '/console/llm/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(body),
      signal: handlers.signal
    })
    if (!res.ok || !res.body) {
      handlers.onError?.('HTTP ' + res.status)
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx: number
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx).trim()
        buffer = buffer.slice(idx + 1)
        if (!line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (!payload) continue
        try {
          const obj = JSON.parse(payload)
          if (typeof obj.delta === 'string') handlers.onDelta(obj.delta)
          else if (obj.done) handlers.onDone?.()
          else if (obj.error) handlers.onError?.(String(obj.error))
        } catch {
          // 忽略无法解析的 SSE 片段
        }
      }
    }
  } catch (e: any) {
    if (e?.name === 'AbortError') return
    handlers.onError?.(e?.message || String(e))
  }
}
