<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h } from 'vue'
import {
  NGrid, NGi, NCard, NStatistic, NButton, NSpace, NIcon, NText,
  NEmpty, NTag, NDataTable, NAlert, NDivider, NTooltip,
  type DataTableColumns
} from 'naive-ui'
import {
  PulseOutline, SpeedometerOutline, ServerOutline,
  ShieldCheckmarkOutline, AlertCircleOutline, StatsChartOutline, TimeOutline,
  LayersOutline, WarningOutline,
  InformationCircleOutline, RefreshOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'

// ══════════════ 数据 ══════════════
const overview = ref<Record<string, any>>({})
const health = ref<Record<string, any>>({})
const qps = ref<Record<string, any>>({ labels: [] as string[], values: [] as number[], outValues: [] as number[] })
const alarms = ref<any[]>([])
const err = ref('')
const loading = ref(false)

const AUTO_REFRESH_KEY = 'xuanji.health.autoRefresh'
const autoRefresh = ref(localStorage.getItem(AUTO_REFRESH_KEY) !== 'false')
let timer: number | null = null
let qpsTimer: number | null = null

// 健康告警类型分布（来自风控数据源，与异常历史表同源）
const alarmStats = ref<Record<string, any>>({})
const ALARM_STATS_META: Record<string, { label: string; color: string }> = {
  CIRCUIT_OPEN: { label: '熔断打开', color: '#e5484d' },
  WS_DISCONNECT: { label: '连接异常', color: '#f0a020' },
  SLOW_STAGE: { label: '慢阶段', color: '#2090e0' },
  QPS_SPIKE: { label: 'QPS 突增', color: '#722ed1' }
}
const alarmStatCards = computed(() => {
  const items = Object.entries(alarmStats.value).filter(([k]) => k !== 'total')
  if (!items.length) return []
  return items.map(([k, v]) => ({ key: k, ...(ALARM_STATS_META[k] || { label: k, color: '#86909c' }), val: Number(v) || 0 }))
})
async function loadAlarmStats() {
  try {
    const o = await api.riskOverview()
    alarmStats.value = o.healthAlarm || {}
  } catch { /* 静默 */ }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [ov, h, q, al] = await Promise.all([
      api.getMetricsOverview(), api.getHealth(), api.getMetricsQps(60), api.getHealthAlarms(50)
    ])
    overview.value = ov
    health.value = h
    qps.value = q
    alarms.value = (al?.rows) || []
    err.value = ''
  } catch (e: any) {
    err.value = e.message
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadQps() {
  try { qps.value = await api.getMetricsQps(60) } catch { /* 静默 */ }
}
async function loadAlarms() {
  try { const al = await api.getHealthAlarms(50); alarms.value = (al?.rows) || [] } catch { /* 静默 */ }
}

// ══════════════ QPS 曲线（5s 轮询，入站 + 出站双通道） ══════════════
const qpsOption = computed(() => {
  const labels = (qps.value.labels as string[]) || []
  const inVals = (qps.value.values as number[]) || []
  const outVals = (qps.value.outValues as number[]) || []
  return {
    animation: false,
    grid: { left: 40, right: 16, top: 30, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['入站', '出站'], top: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 10, interval: 11 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } } },
    series: [
      {
        name: '入站', type: 'line', data: inVals, smooth: true, symbol: 'none',
        lineStyle: { width: 2, color: '#5b5bd6' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
          { offset: 0, color: 'rgba(91,91,214,0.35)' },
          { offset: 1, color: 'rgba(91,91,214,0.02)' }
        ] } }
      },
      {
        name: '出站', type: 'line', data: outVals, smooth: true, symbol: 'none',
        lineStyle: { width: 2, color: '#f0a020' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
          { offset: 0, color: 'rgba(240,160,32,0.30)' },
          { offset: 1, color: 'rgba(240,160,32,0.02)' }
        ] } }
      }
    ]
  }
})

// ══════════════ 自动刷新（无 UI 控制，默认开启） ══════════════
function startTimer() {
  stopTimer()
  if (autoRefresh.value) {
    timer = window.setInterval(() => load(true), 30000)
    qpsTimer = window.setInterval(loadQps, 5000)
  }
}
function stopTimer() {
  if (timer != null) { clearInterval(timer); timer = null }
  if (qpsTimer != null) { clearInterval(qpsTimer); qpsTimer = null }
}
onMounted(() => { load().then(startTimer); loadAlarmStats() })
onUnmounted(() => { stopTimer() })

// ══════════════ 派生 ══════════════
const sys = computed(() => overview.value.system ?? {})
const qpsStat = computed(() => overview.value.qps ?? {})
const pools = computed(() => overview.value.pools ?? [])
const slowStages = computed(() => health.value.pipelineSlowStages ?? {})
const platforms = computed(() => health.value.platforms ?? {})
const connections = computed(() => health.value.connections ?? {})

function fmtBytes(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  if (n >= 1024 ** 3) return (n / 1024 ** 3).toFixed(1) + ' GB'
  if (n >= 1024 ** 2) return (n / 1024 ** 2).toFixed(1) + ' MB'
  return (n / 1024).toFixed(1) + ' KB'
}
function fmtUptime(sec: any): string {
  const s = Number(sec)
  if (!isFinite(s) || s < 0) return '—'
  const d = Math.floor(s / 86400), hh = Math.floor((s % 86400) / 3600)
  const mm = Math.floor((s % 3600) / 60), ss = s % 60
  return (d > 0 ? d + ' 天 ' : '') + hh + ' 时 ' + mm + ' 分 ' + ss + ' 秒'
}
function fmtInt(v: any): string {
  const n = Number(v)
  return isFinite(n) && n >= 0 ? String(n) : '—'
}
function fmtAlarmTime(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  return new Date(n * 1000).toLocaleString('zh-CN', { hour12: false })
}

// 资源状态色
function barColor(v: number): string {
  if (v >= 85) return '#e5484d'
  if (v >= 65) return '#f0a020'
  return '#18a058'
}

// ══════════════ 健康状态派生 ══════════════
const circuitRows = computed<any[]>(() => {
  const rows: any[] = []
  Object.entries(platforms.value).forEach(([plat, m]: any) => {
    const cb = m?.circuitBreaker
    if (Array.isArray(cb)) cb.forEach((r: any) => rows.push({ ...r, platform: plat }))
    else if (cb && typeof cb === 'object') rows.push({ ...cb, platform: plat })
  })
  return rows
})
const connRows = computed<any[]>(() => {
  const rows: any[] = []
  Object.entries(connections.value).forEach(([plat, list]: any) => {
    const sessions = Array.isArray(list) ? list : (list?.sessions ?? [])
    sessions.forEach((r: any) => rows.push({ ...r, platform: plat }))
  })
  return rows
})
const wsConnected = computed(() => connRows.value.filter((r) => r.state === 'CONNECTED').length)
const wsTotal = computed(() => connRows.value.length)
const circuitOpenCount = computed(() => circuitRows.value.filter((r) => r.state === 'OPEN').length)
const circuitTotal = computed(() => circuitRows.value.length)
const slowStageTotal = computed(() =>
  Object.values(slowStages.value).reduce((s, v) => s + Number(v), 0)
)

// WS 连接健康度：已连比例 0~100；0 个会话视为正常（无数据不报错）
const wsPct = computed(() => wsTotal.value === 0 ? 0 : Math.round((wsConnected.value / wsTotal.value) * 100))
function wsBarColor(): string {
  if (wsTotal.value === 0 || wsConnected.value === wsTotal.value) return '#18a058'
  if (wsConnected.value === 0) return '#e5484d'
  return '#f0a020'
}
// 熔断健康度：非 OPEN 的熔断器占比；无熔断器视为 100（正常）
const cbPct = computed(() =>
  circuitTotal.value === 0 ? 100 : Math.round((1 - circuitOpenCount.value / circuitTotal.value) * 100)
)
function cbBarColor(): string {
  return circuitOpenCount.value > 0 ? '#e5484d' : '#18a058'
}

// ══════════════ 异常优先表格 ══════════════
const slowStageRows = computed(() =>
  Object.entries(slowStages.value).map(([name, cnt]) => ({ name, count: Number(cnt) }))
)
const openCircuitRows = computed(() => circuitRows.value.filter((r) => r.state === 'OPEN'))
const brokenConnRows = computed(() => connRows.value.filter((r) => r.state !== 'CONNECTED'))

function stateType(s: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (s) {
    case 'CLOSED': case 'CONNECTED': return 'success'
    case 'OPEN': return 'error'
    case 'RECONNECTING': case 'CONNECTING': case 'IDENTIFYING': case 'RESUMING': return 'warning'
    default: return 'default'
  }
}
function stateLabel(s: string): string {
  const map: Record<string, string> = {
    CONNECTED: '已连接', DISCONNECTED: '已断开', CONNECTING: '连接中',
    IDENTIFYING: '鉴权中', RESUMING: '恢复中', RECONNECTING: '重连中',
    CLOSED: '已关闭', OPEN: '已熔断'
  }
  return map[s] ?? s
}

const openCircuitCols: DataTableColumns = [
  { title: '平台', key: 'platform', width: 90, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => r.platform }) },
  { title: 'AppID', key: 'appId', ellipsis: { tooltip: true } },
  { title: '状态', key: 'state', width: 90, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: stateType(r.state) }, { default: () => stateLabel(r.state) }) },
  { title: '连续失败', key: 'consecutiveFailures', width: 90 },
  { title: '冷却剩余(ms)', key: 'cooldownRemainingMs', width: 110 }
]
const brokenConnCols: DataTableColumns = [
  { title: '平台', key: 'platform', width: 90, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => r.platform }) },
  { title: 'RobotID', key: 'robotId', ellipsis: { tooltip: true } },
  { title: '环境', key: 'envType', width: 100 },
  { title: '状态', key: 'state', width: 100, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: stateType(r.state) }, { default: () => stateLabel(r.state) }) },
  { title: '累计重连', key: 'totalReconnects', width: 90 }
]
const slowCols: DataTableColumns = [
  {
    title: '阶段', key: 'name',
    render: (r: any) => h(NTooltip, { trigger: 'hover', placement: 'top' }, {
      trigger: () => h('span', { style: 'cursor: help; border-bottom: 1px dashed var(--n-text-color-3); padding-bottom: 1px' }, r.name),
      default: () => PIPELINE_STAGE_DESC[r.name] || '暂无说明'
    })
  },
  { title: '超 100ms 次数', key: 'count', width: 130 }
]
// Pipeline 各阶段含义（慢阶段表头悬停可查）
const PIPELINE_STAGE_DESC: Record<string, string> = {
  'pre-process': '事件预处理。提取发送者、群组、机器人上下文，补全平台数据缺失字段。',
  'waking-check': '唤醒检查（P3 预留位）。后续判定机器人是否需要被唤醒处理此事件。',
  'whitelist': '权限检查。主人/超管放行 + 黑名单一票否决，其他用户继续流转。',
  'dedup': '事件幂等去重。同一事件 ID 在 5 分钟 TTL 内只处理一次，先查本地窗口再写 xuanji_dedup 表防多实例重复。',
  'rate-limit': '每用户冷却限流。默认不启用，开启后同用户窗口内重复事件被丢弃。',
  'content-safety': '内容安全检查（P3 预留位）。后续用于检测违规词/敏感内容。',
  'dispatch': '事件分发。按机器人/群/类型路由到具体命令处理器或插件。',
  'result-decorate': '返回结果装饰（P3 预留位）。如 @ 提醒方、附加元数据等。',
  'respond': '实际响应阶段（P3 预留位）。调用平台 SDK 向用户发送回复消息。'
}
const alarmCols: DataTableColumns = [
  { title: '时间', key: 'CREATE_TIME', width: 160, render: (r: any) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, fmtAlarmTime(r.CREATE_TIME)) },
  {
    title: '级别', key: 'LEVEL', width: 80,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, round: true, type: r.LEVEL === 'ERROR' ? 'error' : r.LEVEL === 'INFO' ? 'info' : 'warning' },
      { default: () => r.LEVEL || 'WARN' })
  },
  {
    title: '类型', key: 'TYPE', width: 110,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, round: true, type: 'default' },
      { default: () => ALARM_TYPE_LABEL[r.TYPE] || r.TYPE })
  },
  { title: '内容', key: 'MESSAGE', ellipsis: { tooltip: true }, render: (r: any) => h('span', String(r.MESSAGE || '—')) }
]
const ALARM_TYPE_LABEL: Record<string, string> = {
  CIRCUIT_OPEN: '熔断打开', WS_DISCONNECT: '连接异常', SLOW_STAGE: '慢阶段', QPS_SPIKE: 'QPS 突增'
}

const poolCols: DataTableColumns = [
  { title: '线程池 / 连接池', key: 'name', minWidth: 180 },
  { title: '类型', key: 'type', width: 190 },
  { title: '核心', key: 'core', width: 70, render: (r: any) => fmtInt(r.core) },
  { title: '最大', key: 'max', width: 70, render: (r: any) => fmtInt(r.max) },
  { title: '活跃', key: 'active', width: 70, render: (r: any) => fmtInt(r.active) },
  { title: '当前池大小', key: 'poolSize', width: 96, render: (r: any) => fmtInt(r.poolSize) },
  { title: '排队', key: 'queueSize', width: 70, render: (r: any) => fmtInt(r.queueSize) },
  { title: '完成任务', key: 'completed', width: 96, render: (r: any) => fmtInt(r.completed) },
  { title: '说明', key: 'note', minWidth: 200, ellipsis: { tooltip: true } }
]
</script>

<template>
  <div>
    <NEmpty v-if="err" :description="'加载失败：' + err" style="padding: 60px 0" />

    <template v-else>
      <!-- ═══════ ① 系统资源（顶部，含 WS 连接 + 熔断） ═══════ -->
      <NCard title="系统资源" class="b-card">
        <template #header-extra>
          <NSpace align="center" :size="6">
            <NText depth="3" style="font-size: 12px">资源 30s · QPS 曲线 5s 自动刷新</NText>
            <NButton size="tiny" quaternary @click="load" :loading="loading">
              <template #icon><NIcon size="13"><RefreshOutline /></NIcon></template>
              刷新
            </NButton>
          </NSpace>
        </template>
        <NText depth="3" class="sec-desc">
          运行环境资源占用与机器人连接状态一览。CPU/内存/磁盘/JVM 使用率 <span class="warn">≥65% 黄色注意</span>、<span class="danger">≥85% 红色告警</span>；WS 连接显示各机器人 WebSocket 在线状态；熔断显示平台接口熔断器是否被打开。
        </NText>
        <NGrid :cols="24" :x-gap="14" :y-gap="12" responsive="screen" item-responsive>
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>CPU 占用率</NText><NText :style="{ color: barColor(sys.cpuLoad ?? 0) }" strong>{{ sys.cpuLoad ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.cpuLoad ?? 0) + '%', background: barColor(sys.cpuLoad ?? 0) }" /></div>
              <div class="res-sub">CPU 核数 {{ sys.cpuCores ?? '—' }} · 负载 {{ sys.systemLoadAvg ?? '—' }}</div>
            </div>
          </NGi>
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>内存占用率</NText><NText :style="{ color: barColor(sys.memRatio ?? 0) }" strong>{{ sys.memRatio ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.memRatio ?? 0) + '%', background: barColor(sys.memRatio ?? 0) }" /></div>
              <div class="res-sub">已用 {{ fmtBytes(sys.memUsed) }} / 共 {{ fmtBytes(sys.memTotal) }}</div>
            </div>
          </NGi>
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>硬盘使用率</NText><NText :style="{ color: barColor(sys.diskRatio ?? 0) }" strong>{{ sys.diskRatio ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.diskRatio ?? 0) + '%', background: barColor(sys.diskRatio ?? 0) }" /></div>
              <div class="res-sub">已用 {{ fmtBytes(sys.diskUsed) }} / 共 {{ fmtBytes(sys.diskTotal) }}</div>
            </div>
          </NGi>
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>JVM 堆内存</NText><NText :style="{ color: barColor(sys.jvmRatio ?? 0) }" strong>{{ sys.jvmRatio ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.jvmRatio ?? 0) + '%', background: barColor(sys.jvmRatio ?? 0) }" /></div>
              <div class="res-sub">已用 {{ fmtBytes(sys.jvmUsed) }} / 最大 {{ fmtBytes(sys.jvmMax) }}</div>
            </div>
          </NGi>
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head">
                <NSpace align="center" :size="4"><NIcon size="14" color="#2090e0"><PulseOutline /></NIcon><NText strong>WS 连接</NText></NSpace>
                <NText :style="{ color: wsBarColor() }" strong>{{ wsConnected }}/{{ wsTotal }} 在线</NText>
              </div>
              <div class="bar"><div class="bar-inner" :style="{ width: Math.min(wsPct, 100) + '%', background: wsBarColor() }" /></div>
              <div class="res-sub">全部断开为红色，部分断开为黄色；WS 断开后框架自动重连</div>
            </div>
          </NGi>
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head">
                <NSpace align="center" :size="4"><NIcon size="14" color="#722ed1"><ShieldCheckmarkOutline /></NIcon><NText strong>熔断</NText></NSpace>
                <NText :style="{ color: cbBarColor() }" strong>{{ circuitOpenCount > 0 ? circuitOpenCount + ' 个熔断' : '正常' }}</NText>
              </div>
              <div class="bar"><div class="bar-inner" :style="{ width: Math.min(cbPct, 100) + '%', background: cbBarColor() }" /></div>
              <div class="res-sub">连续失败达到阈值自动打开熔断，冷却期后自动恢复；熔断时请求直接快速失败</div>
            </div>
          </NGi>
        </NGrid>
        <NDivider style="margin: 14px 0 10px" />
        <NGrid :cols="24" :x-gap="16" :y-gap="12" responsive="screen" item-responsive>
          <NGi span="24 m:12 l:6"><div class="kv"><NText depth="3" class="kv-label">运行时间</NText><NText strong><NIcon size="14" style="vertical-align:-2px"><TimeOutline /></NIcon> {{ fmtUptime(sys.uptimeSeconds) }}</NText></div></NGi>
          <NGi span="24 m:12 l:6"><div class="kv"><NText depth="3" class="kv-label">操作系统</NText><NText strong>{{ sys.osName ?? '—' }} {{ sys.osVersion ?? '' }}</NText></div></NGi>
          <NGi span="24 m:12 l:6"><div class="kv"><NText depth="3" class="kv-label">Java 环境</NText><NText strong>{{ sys.javaVendor ?? '—' }} {{ sys.javaVersion ?? '' }}</NText></div></NGi>
          <NGi span="24 m:12 l:6"><div class="kv"><NText depth="3" class="kv-label">磁盘明细</NText><NText strong>{{ (sys.disks ?? []).map((d: any) => d.path + ' ' + d.ratio + '%').join(' · ') || '—' }}</NText></div></NGi>
        </NGrid>
      </NCard>

      <!-- ═══════ ② QPS 实时曲线 + 统计 ═══════ -->
      <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
        <NGi span="24 l:16">
          <NCard title="QPS 实时曲线（入站 / 出站）" class="b-card">
            <template #header-extra><NIcon size="18" color="#5b5bd6"><StatsChartOutline /></NIcon></template>
            <NText depth="3" class="sec-desc">入站 QPS（蓝）= 每秒进入框架的事件数；出站 QPS（橙）= 每秒发送给平台的消息数（发送成功计数）。曲线 5 秒自动刷新。</NText>
            <CommonChart :option="qpsOption" height="240px" />
          </NCard>
        </NGi>
        <NGi span="24 l:8">
          <NCard title="QPS 统计" class="b-card">
            <template #header-extra><NIcon size="18" color="#5b5bd6"><SpeedometerOutline /></NIcon></template>
            <NGrid :cols="2" :x-gap="12" :y-gap="14">
              <NGi><div class="kv"><div class="kv-label">入站 QPS（近 1 秒）</div><NStatistic :value="qpsStat.current ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">出站 QPS（近 1 秒）</div><NStatistic :value="qpsStat.outCurrent ?? 0" :value-style="{ color: '#f0a020' }" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">入站峰值（近 60s）</div><NStatistic :value="qpsStat.peak60 ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">出站峰值（近 60s）</div><NStatistic :value="qpsStat.outPeak60 ?? 0" :value-style="{ color: '#f0a020' }" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">入站均值（近 60s）</div><NStatistic :value="qpsStat.avg60 ?? 0" :precision="2" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">出站均值（近 60s）</div><NStatistic :value="qpsStat.outAvg60 ?? 0" :precision="2" :value-style="{ color: '#f0a020' }" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">入站事件（近 1 分钟）</div><NStatistic :value="qpsStat.inTotal60 ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">出站事件（近 1 分钟）</div><NStatistic :value="qpsStat.outTotal60 ?? 0" :value-style="{ color: '#f0a020' }" /></div></NGi>
            </NGrid>
            <div class="hint">入站 = 每秒进入框架的事件数；出站 = 每秒发送给平台的消息数（发送成功时计数）。当前入站 QPS 超过 60 秒均值 3 倍且大于 15 时判定为「QPS 突增」。</div>
          </NCard>
        </NGi>
      </NGrid>

      <!-- ═══════ ③ 异常优先（不折叠） ═══════ -->
      <NText depth="3" class="sec-desc" style="margin-top: 16px">
        仅当检测到异常时展示以下区块。熔断（平台接口连续失败达阈值自动打开）、连接异常（WebSocket 断开/重连中）、Pipeline 慢阶段（单阶段处理超 100ms）——异常项会同步记录到下方「异常历史记录」。
      </NText>
      <NAlert v-if="!openCircuitRows.length && !brokenConnRows.length && !slowStageRows.length"
        type="success" :show-icon="true" style="margin-top: 10px">
        当前无异常：熔断正常 · 连接正常 · Pipeline 无慢阶段
      </NAlert>

      <NGrid v-if="openCircuitRows.length || brokenConnRows.length" :cols="2" :x-gap="16" :y-gap="16" responsive="screen" item-responsive style="margin-top: 12px">
        <NGi v-if="openCircuitRows.length" span="2 m:1">
          <NCard class="b-card alarm-card" :content-style="{ padding: '12px 14px' }">
            <template #header>
              <NSpace align="center" :size="6">
                <NIcon size="16" color="#e5484d"><AlertCircleOutline /></NIcon>
                <span style="font-weight: 600">电路熔断中</span>
                <NTag size="small" :bordered="false" type="error" round>{{ openCircuitRows.length }}</NTag>
              </NSpace>
            </template>
            <NDataTable :columns="openCircuitCols" :data="openCircuitRows" :bordered="false" size="small" />
          </NCard>
        </NGi>
        <NGi v-if="brokenConnRows.length" span="2 m:1">
          <NCard class="b-card alarm-card" :content-style="{ padding: '12px 14px' }">
            <template #header>
              <NSpace align="center" :size="6">
                <NIcon size="16" color="#f0a020"><WarningOutline /></NIcon>
                <span style="font-weight: 600">连接异常</span>
                <NTag size="small" :bordered="false" type="warning" round>{{ brokenConnRows.length }}</NTag>
              </NSpace>
            </template>
            <NDataTable :columns="brokenConnCols" :data="brokenConnRows" :bordered="false" size="small" />
          </NCard>
        </NGi>
      </NGrid>

      <NCard v-if="slowStageRows.length" class="b-card alarm-card" :content-style="{ padding: '12px 14px' }">
        <template #header>
          <NSpace align="center" :size="6">
            <NIcon size="16" color="#f0a020"><SpeedometerOutline /></NIcon>
            <span style="font-weight: 600">Pipeline 慢阶段（单阶段 > 100ms）</span>
            <NTag size="small" :bordered="false" type="warning" round>{{ slowStageTotal }}</NTag>
          </NSpace>
        </template>
        <NDataTable :columns="slowCols" :data="slowStageRows" :bordered="false" size="small" />
      </NCard>

      <!-- ═══════ ④ 异常历史记录（持久化） ═══════ -->
      <NCard class="b-card" :content-style="{ padding: '12px 14px' }">
        <template #header>
          <NSpace align="center" :size="6">
            <NIcon size="16" color="#854F0B"><InformationCircleOutline /></NIcon>
            <span style="font-weight: 600">异常历史记录</span>
            <NText depth="3" style="font-size: 12px">熔断/断连/慢阶段/QPS 突增 · 持久化</NText>
          </NSpace>
        </template>
        <template #header-extra>
          <NButton size="tiny" quaternary @click="loadAlarms">
            <template #icon><NIcon size="13"><RefreshOutline /></NIcon></template>
            刷新
          </NButton>
        </template>
        <NText depth="3" class="sec-desc">
          自动持久化每次异常事件（熔断打开、WS 断连、慢阶段、QPS 突增），同类异常 5 分钟内去重合并，便于排查偶发问题。QPS 突增指当前 QPS 超过 60 秒均值 3 倍以上且绝对值大于 15。
        </NText>
        <!-- 告警类型分布（累计） -->
        <div v-if="alarmStatCards.length" class="alarm-stats">
          <span class="as-label">告警类型分布（累计）：</span>
          <NTag v-for="c in alarmStatCards" :key="c.key" size="small" :bordered="false" round
            :style="{ background: c.color + '1a', color: c.color, fontWeight: 600 }">
            {{ c.label }} × {{ c.val }}
          </NTag>
        </div>
        <NDataTable v-if="alarms.length" :columns="alarmCols" :data="alarms" :bordered="false" size="small" />
        <NEmpty v-else description="暂无异常记录" style="padding: 24px 0" />
      </NCard>

      <!-- ═══════ ⑤ 线程池信息 ═══════ -->
      <NCard title="线程池 / 连接池明细" class="b-card">
        <template #header-extra><NIcon size="18" color="#f0a020"><LayersOutline /></NIcon></template>
        <NText depth="3" class="sec-desc">
          框架内部各线程池/连接池的运行指标（核心数/最大数/活跃/排队/完成任务）。活跃接近最大或排队持续增长时说明处理能力吃紧，可到「运行设置 → 性能模板」调整。
        </NText>
        <NDataTable v-if="pools.length" :columns="poolCols" :data="pools" :bordered="false" size="small" :scroll-x="1200" />
        <NEmpty v-else description="暂无已注册的线程池（应用启动后自动注册）" style="padding: 30px 0" />
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.grid { margin-top: 4px; }
.b-card { margin-top: 16px; }
.alarm-card { margin-top: 0; }

/* 区块说明文字 */
.sec-desc {
  display: block;
  font-size: 12px;
  line-height: 1.8;
  margin-bottom: 14px;
  padding: 8px 12px;
  background: rgba(128, 128, 128, 0.06);
  border-radius: 6px;
  color: var(--n-text-color-3);
}
.sec-desc .warn { color: #f0a020; font-weight: 600; }
.sec-desc .danger { color: #e5484d; font-weight: 600; }

.res-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid rgba(125, 125, 125, 0.18);
  border-radius: 8px;
  background: rgba(125, 125, 125, 0.03);
  transition: border-color 0.2s, background 0.2s;
}
.res-block:hover {
  border-color: rgba(125, 125, 125, 0.32);
  background: rgba(125, 125, 125, 0.05);
}
.res-head { display: flex; align-items: center; justify-content: space-between; }
/* 进度条：浅灰底层 + inset 凹槽感，让短进度（如 JVM 0.7%）也能清楚看到长度 */
.bar {
  height: 10px;
  border-radius: 5px;
  background: rgba(125, 125, 125, 0.18);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}
.bar-inner {
  height: 100%;
  border-radius: 5px;
  transition: width 0.6s ease, background 0.3s;
}
.res-sub { font-size: 12px; color: var(--n-text-color-3); line-height: 1.5; }
.kv { display: flex; flex-direction: column; gap: 4px; }
.kv-label { font-size: 13px; color: var(--n-text-color-2); }
.hint { margin-top: 12px; font-size: 12px; color: var(--n-text-color-3); line-height: 1.6; }
.alarm-stats { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.as-label { font-size: 12px; color: var(--n-text-color-3); }
</style>
