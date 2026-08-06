<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { NCard, NSpace, NButton, NInputNumber, NSelect, NSwitch, NIcon, NEmpty, NTooltip } from 'naive-ui'
import { DocumentTextOutline, ReloadOutline, ArrowDownOutline, TimerOutline } from '@vicons/ionicons5'
import api from '../api'
import { useFillHeight } from '../composables/useFillHeight'

// 日志页工具栏较高（一行放不下会换行），预留更多空间
const { fillHeight } = useFillHeight(90)

const lines = ref(100)
const raw = ref('')
const err = ref('')
const loading = ref(false)
const autoScroll = ref(true)
const fontSize = ref(13)
const minLevel = ref('ALL')

// 轮询（live tail）：可手动开关与设置间隔；状态持久化到本地存储（切换页面不丢失）
const LOGS_POLL_ENABLED_KEY = 'xuanji.logs.pollEnabled'
const LOGS_POLL_SEC_KEY = 'xuanji.logs.pollSec'
const pollEnabled = ref(localStorage.getItem(LOGS_POLL_ENABLED_KEY) === 'true') // 默认关
const pollSec = ref(Number(localStorage.getItem(LOGS_POLL_SEC_KEY)) || 30) // 默认 30 秒
let pollTimer: number | null = null

// 追踪链路：点击某行 traceId 后，仅显示同一 traceId 的日志
const traceFilter = ref('')

const LEVEL_ORDER: Record<string, number> = { TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4 }
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
    try {
      const o = JSON.parse(t)
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
    } catch {
      out.push({ level: 'RAW', message: t, logger: '', time: '', thread: '' })
    }
  }
  let result = out
  if (minLevel.value !== 'ALL') {
    const min = LEVEL_ORDER[minLevel.value] ?? 0
    result = result.filter((e) => (LEVEL_ORDER[e.level] ?? 99) >= min)
  }
  if (traceFilter.value) {
    result = result.filter((e) => e.trace === traceFilter.value)
  }
  return result
})

function shortLogger(n: string): string {
  if (!n) return ''
  const parts = n.split('.')
  return parts[parts.length - 1]
}
// 完整日期时间：yyyy-MM-dd HH:mm:ss（保留毫秒用于排序可读性的同时，主显示到秒）
function fmtTime(ts: string): string {
  if (!ts) return ''
  const d = new Date(ts)
  if (!isNaN(d.getTime())) {
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
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
  if (!pollEnabled.value) return
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
watch([pollEnabled, pollSec], () => {
  localStorage.setItem(LOGS_POLL_ENABLED_KEY, String(pollEnabled.value))
  localStorage.setItem(LOGS_POLL_SEC_KEY, String(pollSec.value))
  startPoll()
})

function clickTrace(trace?: string) {
  if (!trace) return
  traceFilter.value = traceFilter.value === trace ? '' : trace
}

onMounted(() => {
  load().then(() => {
    if (pollEnabled.value) startPoll() // 若上次开启轮询，进入页面即恢复
  })
})
onUnmounted(stopPoll)

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
        <NSpace align="center" :size="6">
          <NSwitch v-model:value="autoScroll" size="small" />
          <span class="ctl-label">自动滚动</span>
        </NSpace>
        <NTooltip trigger="hover">
          <template #trigger>
            <NSpace align="center" :size="6">
              <NSwitch v-model:value="pollEnabled" size="small" />
              <NIcon size="14" color="#8a93a6"><TimerOutline /></NIcon>
              <span class="ctl-label">轮询</span>
            </NSpace>
          </template>
          开启后按设定间隔自动拉取最新日志（live tail）。后台标签页不轮询。默认 30 秒，可在右侧设置。切换页面后开关状态会自动保留。
        </NTooltip>
        <NSelect v-model:value="pollSec" :options="pollOptions" style="width: 90px" :disabled="!pollEnabled" />
        <NButton type="primary" :loading="loading" @click="load">
          <template #icon><NIcon><ReloadOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <NCard :bordered="false" content-style="padding: 0">
      <div ref="boxRef" class="log-box" :style="{ fontSize: fontSize + 'px', maxHeight: fillHeight + 'px' }">
        <template v-if="entries.length">
          <div v-for="(e, i) in entries" :key="i" class="log-row">
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
        <div v-else class="log-empty">
          <NEmpty description="暂无日志" />
        </div>
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
.log-empty {
  padding: 48px 0;
  text-align: center;
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
