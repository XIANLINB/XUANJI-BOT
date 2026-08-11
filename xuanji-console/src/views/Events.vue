<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, h } from 'vue'
import {
  NDataTable, NSelect, NButton, NAlert, NSpace, NIcon, NText,
  NTag, NEmpty, NSpin, NSwitch, NCard, NGrid, NGi, NNumberAnimation,
  NGradientText, useMessage, type DataTableColumns
} from 'naive-ui'
import { FlashOutline, PulseOutline, DownloadOutline, RefreshOutline } from '@vicons/ionicons5'
import api from '../api'
import { openStream } from '../api/http'
import { useBots } from '../composables/useBots'
import dayjs from 'dayjs'
import { exportCsv, exportJson } from '../utils/export'
import StatCard from '../components/StatCard.vue'

const { bots, loadBots } = useBots()

const botFilter = ref('')
const rows = ref<any[]>([])
const err = ref('')
const loading = ref(false)
const message = useMessage()
// 群 ID → 群名称 映射（getGroups 一次拉取，系统事件列展示用）
const groupNames = ref<Record<string, string>>({})

async function loadGroupNames() {
  try {
    const gs: any[] = (await api.getGroups()) || []
    const m: Record<string, string> = {}
    for (const g of gs) {
      const id = String(g.GROUP_ID || '')
      const name = String(g.GROUP_NAME || '')
      if (id && name && !m[id]) m[id] = name
    }
    groupNames.value = m
  } catch { /* 群列表不可用时仅展示群 ID */ }
}

// ══════ 日期筛选 + 导出 ══════
const dateFilter = ref(0)
const RANGE_OPTIONS = [
  { label: '全部时间', value: 0 },
  { label: '今天', value: 1 },
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 }
]
const filteredRows = computed(() => {
  if (!dateFilter.value) return rows.value
  const since = dayjs().subtract(dateFilter.value, 'day').startOf('day').unix()
  return (rows.value || []).filter((r) => Number(r.CREATE_TIME) >= since)
})

function exportEvents(format: 'csv' | 'json') {
  const data = filteredRows.value
  const stamp = dayjs().format('YYYYMMDD-HHmmss')
  if (format === 'json') {
    exportJson(data, `event-log-${stamp}.json`)
  } else {
    exportCsv(
      data,
      [
        { key: 'CREATE_TIME', label: '时间', value: (r: any) => fmtTime(r.CREATE_TIME) },
        { key: 'EVENT_TYPE', label: '事件类型' },
        { key: 'GROUP_ID', label: '群 ID' },
        { key: 'GROUP_NAME', label: '群名称', value: (r: any) => groupNames.value[r.GROUP_ID] || '' },
        { key: 'USER_ID', label: '用户 ID' },
        { key: 'BOT_APPID', label: '机器人' },
        { key: 'RAW_JSON', label: '原始数据' }
      ],
      `event-log-${stamp}.csv`
    )
  }
  message.success(`已导出 ${data.length} 条（${format.toUpperCase()}）`)
}

// 事件类型 → 中文标签 + 配色
const TYPE_LABEL: Record<string, string> = {
  GROUP_ADD_ROBOT: '机器人入群',
  GROUP_DEL_ROBOT: '机器人退群',
  GROUP_MEMBER_ADD: '成员加入',
  GROUP_MEMBER_REMOVE: '成员退出',
  GROUP_JOIN_REQUEST: '加群申请',
  GROUP_MSG_REJECT: '群消息被拒',
  GROUP_MSG_RECEIVE: '群消息接收',
  FRIEND_ADD: '好友添加',
  FRIEND_DEL: '好友删除',
  C2C_MSG_REJECT: '单聊消息被拒',
  C2C_MSG_RECEIVE: '单聊消息接收',
  GROUP_MESSAGE_CREATE: '群消息',
  GROUP_AT_MESSAGE_CREATE: '群@消息',
  C2C_MESSAGE_CREATE: '单聊消息'
}
const TYPE_COLOR: Record<string, 'success' | 'error' | 'warning' | 'info' | 'default'> = {
  GROUP_ADD_ROBOT: 'success', GROUP_DEL_ROBOT: 'error',
  GROUP_MEMBER_ADD: 'success', GROUP_MEMBER_REMOVE: 'warning',
  GROUP_JOIN_REQUEST: 'warning', GROUP_MSG_REJECT: 'error', GROUP_MSG_RECEIVE: 'info',
  FRIEND_ADD: 'success', FRIEND_DEL: 'error',
  C2C_MSG_REJECT: 'error', C2C_MSG_RECEIVE: 'info',
  GROUP_MESSAGE_CREATE: 'info', GROUP_AT_MESSAGE_CREATE: 'success', C2C_MESSAGE_CREATE: 'info'
}

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n)) return String(v)
  const d = new Date(n <= 9999999999 ? n * 1000 : n)
  if (isNaN(d.getTime())) return String(v)
  const p = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function load() {
  loading.value = true
  err.value = ''
  try {
    const r = await api.getEvents(botFilter.value, 1000)
    rows.value = Array.isArray(r?.rows) ? r.rows : []
  } catch (e: any) {
    err.value = e?.message || String(e)
    rows.value = []
  } finally {
    loading.value = false
  }
}

// ══════ 实时消息处理流水（SSE） ══════
const EVENTS_LIVE_KEY = 'xuanji.events.live'
const live = ref(localStorage.getItem(EVENTS_LIVE_KEY) === 'true')
const liveEvents = ref<any[]>([])
const streamEs = ref<EventSource | null>(null)
const LIVE_CAP = 200
// SSE 连接状态：off(关闭) / connecting(连接中) / open(已连接) / error(断线自动重连中)
const streamStatus = ref<'off' | 'connecting' | 'open' | 'error'>('off')
const reconnectCount = ref(0)

function startStream() {
  stopStream()
  streamStatus.value = 'connecting'
  reconnectCount.value = 0
  try {
    const es = openStream()
    es.onopen = () => {
      streamStatus.value = 'open'
      reconnectCount.value = 0
    }
    es.onerror = () => {
      // EventSource 断线会自动重连，这里只标记状态与累计次数，不弹重复 toast
      streamStatus.value = 'error'
      reconnectCount.value++
    }
    es.addEventListener('event', (e: MessageEvent) => {
      try {
        const d = JSON.parse((e as MessageEvent).data)
        liveEvents.value.unshift(d)
        if (liveEvents.value.length > LIVE_CAP) liveEvents.value.pop()
      } catch { /* 忽略畸形帧 */ }
    })
    streamEs.value = es
  } catch {
    // SSE 不可用时标记为断开状态，供界面展示，不影响表格
    streamStatus.value = 'error'
    reconnectCount.value++
  }
}
function stopStream() {
  if (streamEs.value) { streamEs.value.close(); streamEs.value = null }
  streamStatus.value = 'off'
}
function toggleLive(v: boolean) {
  live.value = v
  localStorage.setItem(EVENTS_LIVE_KEY, String(v))
  if (v) startStream(); else stopStream()
}

// 纯文本单元格：超长截断 + 原生 title 悬停查看全文。
// 刻意不用 NTooltip 包裹超长 JSON，规避富文本渲染在异常数据下的崩溃风险。
function textCell(v: unknown, max = 200) {
  const s = v == null ? '—' : String(v)
  return h('span', { title: s, style: 'cursor: help; word-break: break-all' }, s.length > max ? s.slice(0, max) + '…' : s)
}

// ══════ 统计卡 ══════
const statCards = computed(() => {
  const today0 = dayjs().startOf('day').unix()
  const today = rows.value.filter((r) => Number(r.CREATE_TIME) >= today0).length
  const memberEvt = rows.value.filter((r) => ['GROUP_ADD_ROBOT', 'GROUP_DEL_ROBOT', 'GROUP_MEMBER_ADD', 'GROUP_MEMBER_REMOVE'].includes(r.EVENT_TYPE)).length
  const friendEvt = rows.value.filter((r) => ['FRIEND_ADD', 'FRIEND_DEL'].includes(r.EVENT_TYPE)).length
  return [
    { key: 'total', label: '事件总数', val: rows.value.length, color: '#5b5bd6', icon: FlashOutline },
    { key: 'today', label: '今日事件', val: today, color: '#07c160', icon: RefreshOutline },
    { key: 'member', label: '群成员变动', val: memberEvt, color: '#fa8c16', icon: PulseOutline },
    { key: 'friend', label: '好友变动', val: friendEvt, color: '#2090e0', icon: DownloadOutline }
  ]
})

const columns = computed<DataTableColumns>(() => [
  {
    title: '时间', key: 'CREATE_TIME', width: 170,
    render: (row: any) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, fmtTime(row.CREATE_TIME))
  },
  {
    title: '事件类型', key: 'EVENT_TYPE', width: 140,
    render: (row: any) => {
      const raw = row.EVENT_TYPE || '—'
      return h(NTag, { size: 'small', round: true, bordered: false, type: TYPE_COLOR[raw] ?? 'default' },
        { default: () => TYPE_LABEL[raw] || raw })
    }
  },
  { title: '群', key: 'GROUP_ID', width: 180, ellipsis: { tooltip: true }, render: (row: any) => textCell(row.GROUP_ID, 60) },
  {
    title: '群名称', key: 'GROUP_NAME', width: 160, ellipsis: { tooltip: true },
    render: (row: any) => {
      const n = groupNames.value[row.GROUP_ID]
      if (!n) return h('span', { style: 'color: #86909c' }, '—')
      return textCell(n, 60)
    }
  },
  { title: '用户', key: 'USER_ID', width: 180, ellipsis: { tooltip: true }, render: (row: any) => textCell(row.USER_ID, 60) },
  { title: 'Bot', key: 'BOT_APPID', width: 130, ellipsis: { tooltip: true }, render: (row: any) => h('span', { style: 'font-variant-numeric: tabular-nums' }, textCell(row.BOT_APPID, 30)) },
  { title: '原始数据', key: 'RAW_JSON', width: 320, ellipsis: { tooltip: true }, render: (row: any) => textCell(row.RAW_JSON, 120) }
])

const pagination = computed(() =>
  filteredRows.value.length > 20
    ? { pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100, 200], showTotal: (t: number) => `共 ${t} 条` }
    : false
)

onMounted(async () => {
  await loadBots()
  await loadGroupNames()
  await load()
  if (live.value) startStream()
})
onUnmounted(stopStream)
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NGradientText :gradient="{ deg: 90, from: '#18a058', to: '#2090e0' }" :size="18" style="font-weight: 700">
          系统事件
        </NGradientText>
        <NText depth="3" style="font-size: 13px">共 {{ filteredRows.length }} 条</NText>
      </div>
      <NSpace align="center" :size="8" wrap>
        <NSelect
          v-model:value="botFilter"
          :options="bots"
          placeholder="按 Bot 过滤"
          clearable
          size="small"
          style="width: 170px"
          @update:value="load"
        />
        <NSelect v-model:value="dateFilter" :options="RANGE_OPTIONS" size="small" style="width: 110px" />
        <NSpace align="center" :size="6">
          <NSwitch v-model:value="live" size="small" @update:value="toggleLive" />
          <NIcon size="14" color="#18a058"><PulseOutline /></NIcon>
          <span class="ctl-label" title="开启后通过 SSE 实时接收消息处理流水">实时</span>
          <NTag v-if="live" size="tiny" round :bordered="false"
            :type="streamStatus === 'open' ? 'success' : streamStatus === 'error' ? 'error' : 'warning'">
            {{ streamStatus === 'open' ? '已连接' : streamStatus === 'error' ? '重连中(' + reconnectCount + ')' : '连接中' }}
          </NTag>
        </NSpace>
        <NButton size="small" secondary :disabled="!filteredRows.length" @click="exportEvents('csv')">
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          CSV
        </NButton>
        <NButton size="small" secondary :disabled="!filteredRows.length" @click="exportEvents('json')">
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          JSON
        </NButton>
        <NButton size="small" type="primary" :loading="loading" @click="load">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <!-- 统计卡 -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
      <NGi v-for="c in statCards" :key="c.key" span="24 s:12 m:6">
        <StatCard :icon="c.icon" :color="c.color" :value="c.val" :label="c.label" :duration="800" />
      </NGi>
    </NGrid>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <!-- 实时消息处理流水（SSE），与上方 DB 系统事件表互补 -->
    <div v-if="live" class="live-panel">
      <div class="live-head">
        <NIcon size="14" color="#18a058"><PulseOutline /></NIcon>
        <span>实时消息处理流水</span>
        <NText depth="3" style="font-size: 12px">共 {{ liveEvents.length }} 条（最新置顶）</NText>
      </div>
      <div class="live-box">
        <div v-for="(e, i) in liveEvents" :key="i" class="live-row">
          <span class="live-time">{{ (e.time || '').slice(11, 19) || '--:--:--' }}</span>
          <span class="live-badge" :class="'lv-' + String(e.level).toLowerCase()">{{ e.level }}</span>
          <span class="live-cat">{{ e.category }}</span>
          <span v-if="e.botId" class="live-bot">{{ e.botId }}</span>
          <span class="live-msg">{{ e.message }}</span>
        </div>
        <div v-if="!liveEvents.length" class="live-empty">暂无实时事件（有消息进出时这里会出现流水）</div>
      </div>
    </div>

    <NSpin :show="loading" style="min-height: 200px">
      <NEmpty
        v-if="!loading && !filteredRows.length"
        :description="'暂无系统事件（加群/退群/加好友等会写入）'"
        style="padding: 48px 0"
      />
      <NDataTable
        v-else
        :columns="columns"
        :data="filteredRows"
        :pagination="pagination"
        :bordered="false"
        size="small"
        striped
        style="margin-bottom: 12px"
      />
    </NSpin>
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
  gap: 10px;
}
.ctl-label {
  color: #8a93a6;
  font-size: 13px;
  white-space: nowrap;
}
.live-panel {
  margin-bottom: 16px;
  border: 1px solid rgba(128, 128, 128, 0.18);
  border-radius: 12px;
  overflow: hidden;
}
.live-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: rgba(24, 160, 88, 0.08);
  font-size: 13px;
  font-weight: 600;
}
.live-box {
  background: #0b0f14;
  color: #c9d6e3;
  padding: 10px 14px;
  max-height: 280px;
  overflow-y: auto;
  line-height: 1.7;
  font-family: ui-monospace, "JetBrains Mono", Consolas, monospace;
  font-size: 12.5px;
}
.live-row {
  padding: 2px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.035);
  word-break: break-all;
  white-space: pre-wrap;
}
.live-time { color: #5c6b7a; margin-right: 8px; }
.live-badge {
  display: inline-block;
  min-width: 42px;
  text-align: center;
  padding: 0 5px;
  border-radius: 4px;
  font-weight: 700;
  font-size: 0.82em;
  margin-right: 8px;
}
.live-badge.lv-info { background: rgba(24, 160, 88, 0.25); color: #6fdba6; }
.live-badge.lv-warn { background: rgba(240, 160, 32, 0.25); color: #f5c066; }
.live-badge.lv-error { background: rgba(229, 72, 77, 0.25); color: #f59aa0; }
.live-badge.lv-debug { background: rgba(128, 128, 128, 0.22); color: #aab4c0; }
.live-cat { color: #7a8aa0; margin-right: 8px; }
.live-bot { color: #8ab4f8; margin-right: 8px; }
.live-msg { font-weight: 500; }
.live-empty { color: #6b7785; padding: 14px 0; text-align: center; }
</style>
