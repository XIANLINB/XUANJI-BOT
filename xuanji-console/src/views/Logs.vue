<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { NCard, NSpace, NButton, NInputNumber, NSelect, NSwitch, NIcon, NTooltip, NRadioGroup, NRadioButton, useMessage } from 'naive-ui'
import { DocumentTextOutline, ReloadOutline, ArrowDownOutline, TimerOutline, PulseOutline, DownloadOutline } from '@vicons/ionicons5'
import api from '../api'
import { openStream } from '../api/http'
import { useFillHeight } from '../composables/useFillHeight'
import dayjs from 'dayjs'
import { exportCsv, exportJson } from '../utils/export'
import EmptyState from '../components/EmptyState.vue'

// 日志页工具栏较高（一行放不下会换行），预留更多空间
const { fillHeight } = useFillHeight(90)

const message = useMessage()

const lines = ref(300)
const raw = ref('')
const err = ref('')
const loading = ref(false)
const autoScroll = ref(true)
const fontSize = ref(13)
const minLevel = ref('ALL')

// ══════ 自动更新模式：关闭 / 轮询 / 实时（SSE）三态单选 ══════
// 轮询与实时是互斥的两种「自动更新」手段，合并为一个三态避免同时运行/状态打架
const LOGS_MODE_KEY = 'xuanji.logs.mode'
type UpdateMode = 'off' | 'poll' | 'realtime'
function migrateMode(): UpdateMode {
  const saved = localStorage.getItem(LOGS_MODE_KEY) as UpdateMode | null
  if (saved === 'off' || saved === 'poll' || saved === 'realtime') return saved
  // 旧版两个独立开关迁移：实时优先，其次轮询
  const wasRealtime = localStorage.getItem('xuanji.logs.realtime') === 'true'
  const wasPoll = localStorage.getItem('xuanji.logs.pollEnabled') === 'true'
  if (wasRealtime) return 'realtime'
  if (wasPoll) return 'poll'
  return 'off'
}
const updateMode = ref<UpdateMode>(migrateMode())
const MODE_OPTIONS = [
  { label: '关闭', value: 'off' },
  { label: '轮询', value: 'poll' },
  { label: '实时(SSE)', value: 'realtime' }
]

// 轮询间隔（仅轮询模式生效）；状态持久化到本地存储
const LOGS_POLL_SEC_KEY = 'xuanji.logs.pollSec'
const pollSec = ref(Number(localStorage.getItem(LOGS_POLL_SEC_KEY)) || 30) // 默认 30 秒
let pollTimer: number | null = null

// 追踪链路：点击某行 traceId 后，仅显示同一 traceId 的日志
const traceFilter = ref('')

// 实时流（SSE）：连接后服务端推送新增日志行
const streamEs = ref<EventSource | null>(null)
const streamError = ref(false)
const LIVE_CAP = 5000 // 实时累积行数上限，避免失控增长

const LEVEL_ORDER: Record<string, number> = { TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4, RAW: -1 }
const LEVEL_COLOR: Record<string, string> = {
  ERROR: '#ff6b6b',
  WARN: '#ffa94d',
  INFO: '#69db7c',
  DEBUG: '#9aa5b1',
  TRACE: '#7d8794',
  RAW: '#c9d6e3'
}

interface Entry {
  level: string
  message: string
  logger: string
  time: string
  thread: string
  stack?: string
  trace?: string
}

const entries = computed<Entry[]>(() => {
  const text = raw.value
  if (!text) return []
  const out: Entry[] = []
  for (const line of text.split('\n')) {
    const t = line.trim()
    if (!t) continue
    // 三级解析：
    //   1) 直接 JSON（来自某些结构化日志格式）
    //   2) SSE 包装的 JSON（type=log，line 字段是真日志行）
    //   3) 纯文本 logback 行（来自 /logs 接口文件尾部读取），用正则从中提取 level/time/thread/logger/message
    let o: any = null
    try {
      o = JSON.parse(t)
    } catch {
      // 进入第 3 条：用正则解析 logback 行，缺失时仍 fallback RAW
      // 时间戳可带日期（yyyy-MM-dd HH:mm:ss.SSS）或不带（仅 HH:mm:ss.SSS），兼容两种格式
      const m = /^(?:\d{4}-\d{2}-\d{2}\s+)?(\d{2}:\d{2}:\d{2}\.\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+\[([^\]]+)\]\s+(\S+)\s*(.*)$/.exec(t)
      if (m) {
        o = {
          time: m[1],
          level: m[2],
          thread_name: m[3],
          logger_name: m[4],
          message: m[5]
        }
      }
    }
    if (o && typeof o === 'object' && o !== null) {
      // SSE wrapper（{ type, line }）：解包后递归解析真实日志行
      if (o.type === 'log' && typeof o.line === 'string' && !o.level) {
        const inner = o.line.trim()
        let innerParsed: any = null
        try {
          innerParsed = JSON.parse(inner)
        } catch {
          const m2 = /^(?:\d{4}-\d{2}-\d{2}\s+)?(\d{2}:\d{2}:\d{2}\.\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+\[([^\]]+)\]\s+(\S+)\s*(.*)$/.exec(inner)
          if (m2) {
            innerParsed = {
              time: m2[1],
              level: m2[2],
              thread_name: m2[3],
              logger_name: m2[4],
              message: m2[5]
            }
          }
        }
        if (innerParsed && typeof innerParsed === 'object') o = innerParsed
      }
      const mdc = (o.mdc && typeof o.mdc === 'object') ? o.mdc : {}
      out.push({
        level: (o.level || 'INFO').toUpperCase(),
        message: o.message ?? '',
        logger: shortLogger(o.logger_name || o.loggerName || ''),
        time: fmtTime(o['@timestamp'] || o.timestamp || o.time || ''),
        thread: o.thread_name || o.threadName || '',
        stack: o.stack || '',
        trace: mdc.traceId || ''
      })
    } else {
      out.push({ level: 'RAW', message: t, logger: '', time: '', thread: '' })
    }
  }
  let result = out
  if (minLevel.value !== 'ALL') {
    const min = LEVEL_ORDER[minLevel.value] ?? 0
    result = result.filter((e) => (LEVEL_ORDER[e.level] ?? 0) >= min)
  }
  if (traceFilter.value) {
    result = result.filter((e) => e.trace === traceFilter.value)
  }
  return result
})

// ══════ 日期筛选 + 导出（批次6） ══════
const dateFilter = ref(0)
const LOG_RANGE_OPTIONS = [
  { label: '全部时间', value: 0 },
  { label: '今天', value: 1 },
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 }
]
const filteredEntries = computed<Entry[]>(() => {
  if (!dateFilter.value) return entries.value
  const since = dayjs().utcOffset(8).subtract(dateFilter.value, 'day').startOf('day')
  return entries.value.filter((e) => {
    if (!e.time) return true // 原始行无时间戳不参与日期过滤
    const t = dayjs(e.time)
    return t.isValid() && t.isAfter(since)
  })
})

const exporting = ref(false)
function exportLogs(format: 'csv' | 'json') {
  exporting.value = true
  try {
    const data = filteredEntries.value
    const stamp = dayjs().utcOffset(8).format('YYYYMMDD-HHmmss')
    if (format === 'json') {
      exportJson(data, `runtime-log-${stamp}.json`)
    } else {
      exportCsv(
        data,
        [
          { key: 'time', label: '时间' },
          { key: 'level', label: '级别' },
          { key: 'thread', label: '线程' },
          { key: 'logger', label: '记录器' },
          { key: 'message', label: '消息' },
          { key: 'trace', label: 'TraceId' },
          { key: 'stack', label: '堆栈' }
        ],
        `runtime-log-${stamp}.csv`
      )
    }
    message.success(`已导出 ${data.length} 条（${format.toUpperCase()}）`)
  } finally {
    exporting.value = false
  }
}

function shortLogger(n: string): string {
  if (!n) return ''
  const parts = n.split('.')
  return parts[parts.length - 1]
}
// 完整日期时间：yyyy-MM-dd HH:mm:ss（UTC+8 口径，与全局时间展示统一）
function fmtTime(ts: string): string {
  if (!ts) return ''
  const d = new Date(ts)
  if (!isNaN(d.getTime())) {
    return dayjs(d.getTime()).utcOffset(8).format('YYYY-MM-DD HH:mm:ss')
  }
  const m = ts.match(/(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/)
  return m ? `${m[1]} ${m[2]}` : ts
}
function hexToRgba(hex: string, a: number): string {
  const h = hex.replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r},${g},${b},${a})`
}
function levelColor(level: string): string {
  return LEVEL_COLOR[level] || LEVEL_COLOR.RAW
}
function badgeStyle(level: string) {
  const c = levelColor(level)
  return {
    color: c,
    background: hexToRgba(c, 0.14),
    border: `1px solid ${hexToRgba(c, 0.35)}`
  }
}

const boxRef = ref<HTMLElement | null>(null)
function scrollToBottom() {
  nextTick(() => {
    const el = boxRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function load() {
  loading.value = true
  try {
    raw.value = await api.getLogs(lines.value)
    err.value = ''
  } catch (e: any) {
    err.value = e.message
  } finally {
    loading.value = false
    if (autoScroll.value) scrollToBottom()
  }
}

function startPoll() {
  stopPoll()
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'hidden') return // 后台标签页不轮询，省资源
    load()
  }, Math.max(1, pollSec.value) * 1000)
}
function stopPoll() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(autoScroll, (v) => { if (v) scrollToBottom() })
watch(updateMode, (v) => {
  localStorage.setItem(LOGS_MODE_KEY, updateMode.value)
  // 切到「轮询」或「实时」时先拉一次历史，再启动订阅，保证切模式时立刻能看到日志
  if (v === 'off') {
    applyUpdateMode()
  } else {
    load().finally(() => applyUpdateMode())
  }
})
watch(pollSec, () => {
  localStorage.setItem(LOGS_POLL_SEC_KEY, String(pollSec.value))
  if (updateMode.value === 'poll') startPoll()
})

// ══════ 自动更新模式统一调度：off → 全停；poll → 轮询；realtime → SSE（异常降级轮询） ══════
function applyUpdateMode() {
  stopStream()
  stopPoll()
  streamError.value = false
  if (updateMode.value === 'poll') {
    startPoll()
  } else if (updateMode.value === 'realtime') {
    startStream()
  }
}

function startStream() {
  const es = openStream()
  es.addEventListener('log', (e: MessageEvent) => appendLiveLine((e as MessageEvent).data))
  es.onopen = () => { streamError.value = false }
  es.onerror = () => {
    // EventSource 会自动重连；持续失败则降级为轮询保证可用性
    streamError.value = true
    if (updateMode.value === 'realtime') {
      updateMode.value = 'poll' // 自动降级并同步 UI 三态
      message.warning('实时(SSE)连接异常，已自动降级为轮询模式')
    }
  }
  streamEs.value = es
}

function stopStream() {
  if (streamEs.value) {
    streamEs.value.close()
    streamEs.value = null
  }
}

function appendLiveLine(line: string) {
  if (!line) return
  raw.value = raw.value ? raw.value + '\n' + line : line
  const parts = raw.value.split('\n')
  if (parts.length > LIVE_CAP) {
    raw.value = parts.slice(parts.length - LIVE_CAP).join('\n')
  }
  if (autoScroll.value) scrollToBottom()
}

function clickTrace(trace?: string) {
  if (!trace) return
  traceFilter.value = traceFilter.value === trace ? '' : trace
}

onMounted(() => {
  load().then(applyUpdateMode) // 恢复上次的三态模式
})
onUnmounted(() => {
  stopStream()
  stopPoll()
})

const fontOptions = [11, 12, 13, 14, 15, 16, 17, 18, 19, 20].map((n) => ({ label: n + ' px', value: n }))
const levelOptions = [
  { label: '全部级别', value: 'ALL' },
  { label: '≥ TRACE', value: 'TRACE' },
  { label: '≥ DEBUG', value: 'DEBUG' },
  { label: '≥ INFO', value: 'INFO' },
  { label: '≥ WARN', value: 'WARN' },
  { label: '≥ ERROR', value: 'ERROR' }
]
const pollOptions = [
  { label: '1 秒', value: 1 },
  { label: '2 秒', value: 2 },
  { label: '3 秒', value: 3 },
  { label: '5 秒', value: 5 },
  { label: '10 秒', value: 10 },
  { label: '15 秒', value: 15 },
  { label: '30 秒', value: 30 }
]
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#e5484d"><DocumentTextOutline /></NIcon>
        <span>运行日志</span>
        <NTooltip v-if="traceFilter" trigger="hover">
          <template #trigger>
            <span class="trace-chip" @click="traceFilter = ''">链路: {{ traceFilter }} ✕</span>
          </template>
          仅显示该 traceId 的日志（点击清除过滤）
        </NTooltip>
      </div>
      <NSpace align="center" :size="10">
        <NTooltip trigger="hover">
          <template #trigger>
            <span class="ctl-label">行数</span>
          </template>
          从日志文件尾部读取的最新行数（抓取的窗口大小）。调大可看更多历史，但解析与渲染开销略增。
        </NTooltip>
        <NInputNumber v-model:value="lines" :min="50" :max="5000" :step="50" style="width: 110px" />
        <NSelect v-model:value="minLevel" :options="levelOptions" style="width: 130px" />
        <NSelect v-model:value="fontSize" :options="fontOptions" style="width: 96px" />
        <NSelect v-model:value="dateFilter" :options="LOG_RANGE_OPTIONS" style="width: 110px" />
        <NButton size="small" secondary :disabled="!filteredEntries.length" :loading="exporting" @click="exportLogs('csv')">
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          CSV
        </NButton>
        <NButton size="small" secondary :disabled="!filteredEntries.length" :loading="exporting" @click="exportLogs('json')">
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          JSON
        </NButton>
        <NSpace align="center" :size="6">
          <NSwitch v-model:value="autoScroll" size="small" />
          <span class="ctl-label">自动滚动</span>
        </NSpace>
        <NTooltip trigger="hover">
          <template #trigger>
            <span class="ctl-label">自动更新</span>
          </template>
          三态互斥：关闭 / 轮询（定时拉取，后台标签页暂停）/ 实时（SSE 服务端推送）。实时连接异常时自动降级为轮询。
        </NTooltip>
        <NRadioGroup v-model:value="updateMode" size="small">
          <NRadioButton v-for="opt in MODE_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
        </NRadioGroup>
        <NSelect v-model:value="pollSec" :options="pollOptions" style="width: 90px" :disabled="updateMode !== 'poll'" />
        <NTooltip trigger="hover">
          <template #trigger>
            <span class="ctl-label" :style="{ color: updateMode === 'realtime' ? (streamError ? '#ffa94d' : '#18a058') : 'inherit' }">
              <NIcon size="14" style="vertical-align:-2px; margin-right: 2px">
                <PulseOutline v-if="updateMode === 'realtime'" />
                <TimerOutline v-else />
              </NIcon>
              {{ updateMode === 'realtime' ? (streamError ? '实时异常' : '实时中') : updateMode === 'poll' ? '轮询中' : '已停止' }}
            </span>
          </template>
          {{ updateMode === 'realtime' ? 'SSE 实时接收新增日志行（服务端推送，无需轮询）' : updateMode === 'poll' ? `每 ${pollSec} 秒自动拉取最新日志（live tail）` : '未开启自动更新，可手动刷新' }}
        </NTooltip>
        <NButton type="primary" :loading="loading" @click="load">
          <template #icon><NIcon><ReloadOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <NCard :bordered="false" content-style="padding: 0">
      <div ref="boxRef" class="log-box" :style="{ fontSize: fontSize + 'px', maxHeight: fillHeight + 'px' }">
        <template v-if="filteredEntries.length">
          <div v-for="(e, i) in filteredEntries" :key="i" class="log-row">
            <span class="log-time">{{ e.time || '--:--:--' }}</span>
            <span class="log-badge" :style="badgeStyle(e.level)">{{ e.level }}</span>
            <span
              v-if="e.trace"
              class="log-trace"
              :title="'点击只看该链路'"
              @click="clickTrace(e.trace)"
            >{{ e.trace }}</span>
            <span v-if="e.logger" class="log-logger">{{ e.logger }}</span>
            <span class="log-msg" :style="{ color: levelColor(e.level) }">{{ e.message }}</span>
            <details v-if="e.stack" class="log-stack">
              <summary>异常堆栈</summary>
              <pre>{{ e.stack }}</pre>
            </details>
          </div>
        </template>
        <div v-else-if="err" class="log-err">{{ err }}</div>
        <EmptyState v-else description="暂无日志" />
      </div>
    </NCard>

    <div v-if="autoScroll" class="scroll-hint">
      <NIcon size="14"><ArrowDownOutline /></NIcon>
      <span>已自动滚动到底部</span>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
.trace-chip {
  font-size: 12px;
  color: #8ab4f8;
  background: rgba(138, 180, 248, 0.12);
  border: 1px solid rgba(138, 180, 248, 0.35);
  border-radius: 4px;
  padding: 1px 6px;
  cursor: pointer;
  font-family: ui-monospace, Consolas, monospace;
}
.ctl-label {
  color: #8a93a6;
  font-size: 13px;
  white-space: nowrap;
}
.log-box {
  background: #0b0f14;
  color: #c9d6e3;
  padding: 14px 16px;
  margin: 0;
  border-radius: 0 0 14px 14px;
  overflow-y: auto;
  line-height: 1.7;
  font-family: ui-monospace, "JetBrains Mono", "SFMono-Regular", Consolas, "Liberation Mono", monospace;
}
.log-box::-webkit-scrollbar {
  width: 10px;
}
.log-box::-webkit-scrollbar-thumb {
  background: #2a323d;
  border-radius: 6px;
}
.log-box::-webkit-scrollbar-thumb:hover {
  background: #3a4452;
}
.log-row {
  padding: 3px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.035);
  white-space: pre-wrap;
  word-break: break-all;
}
.log-time {
  color: #5c6b7a;
  margin-right: 8px;
}
.log-badge {
  display: inline-block;
  min-width: 54px;
  text-align: center;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 0.82em;
  margin-right: 8px;
  font-weight: 700;
  letter-spacing: 0.3px;
  vertical-align: middle;
}
.log-trace {
  display: inline-block;
  color: #8ab4f8;
  background: rgba(138, 180, 248, 0.1);
  border: 1px solid rgba(138, 180, 248, 0.28);
  border-radius: 4px;
  font-size: 0.78em;
  padding: 0 5px;
  margin-right: 8px;
  cursor: pointer;
  font-family: ui-monospace, Consolas, monospace;
  vertical-align: middle;
}
.log-trace:hover {
  background: rgba(138, 180, 248, 0.22);
}
.log-logger {
  color: #7a8aa0;
  margin-right: 8px;
}
.log-msg {
  font-weight: 500;
}
.log-stack {
  margin: 4px 0 6px 62px;
  color: #ff8585;
}
.log-stack summary {
  cursor: pointer;
  color: #ff8585;
  opacity: 0.85;
  font-size: 0.92em;
  user-select: none;
}
.log-stack pre {
  margin: 6px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
  opacity: 0.7;
  font-size: 0.9em;
}
.log-err {
  color: #e88080;
  padding: 16px;
}
.scroll-hint {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #6b7785;
  font-size: 12px;
  justify-content: flex-end;
}
</style>
