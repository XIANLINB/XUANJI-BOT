<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h, type Component } from 'vue'
import {
  NGrid, NGi, NCard, NStatistic, NButton, NSpace, NIcon, NText,
  NEmpty, NTag, NDataTable, NSwitch, NTooltip, NRadioGroup, NRadioButton,
  NAlert, NDivider, NSpin
} from 'naive-ui'
import {
  PulseOutline, HardwareChipOutline, SpeedometerOutline, ServerOutline,
  CubeOutline, ShieldCheckmarkOutline, GitBranchOutline, AlertCircleOutline,
  StatsChartOutline, TimeOutline, InformationCircleOutline, LayersOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'

// ══════════════ 数据 ══════════════
const overview = ref<Record<string, any>>({})
const health = ref<Record<string, any>>({})
const qps = ref<Record<string, any>>({ labels: [] as string[], values: [] as number[] })
const tune = ref<Record<string, any>>({ eco: null, perf: null })
const err = ref('')
const loading = ref(false)

const AUTO_REFRESH_KEY = 'xuanji.health.autoRefresh'
const autoRefresh = ref(localStorage.getItem(AUTO_REFRESH_KEY) !== 'false')
const lastUpdate = ref('')
let timer: number | null = null
let qpsTimer: number | null = null

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [ov, h, q] = await Promise.all([api.getMetricsOverview(), api.getHealth(), api.getMetricsQps(60)])
    overview.value = ov
    health.value = h
    qps.value = q
    err.value = ''
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    if (tune.value.eco === null) {
      tune.value.eco = await api.getTuneRecommend('eco')
      tune.value.perf = await api.getTuneRecommend('perf')
    }
  } catch (e: any) {
    err.value = e.message
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadQps() {
  try {
    qps.value = await api.getMetricsQps(60)
  } catch { /* 静默：下一轮再试 */ }
}

// ══════════════ QPS 曲线（CommonChart，5s 轮询自动重绘） ══════════════
const qpsOption = computed(() => {
  const labels = (qps.value.labels as string[]) || []
  const values = (qps.value.values as number[]) || []
  return {
    animation: false,
    grid: { left: 40, right: 16, top: 30, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { fontSize: 10, interval: 11 }
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } } },
    series: [{
      name: '入站 QPS',
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#5b5bd6' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(91,91,214,0.35)' },
        { offset: 1, color: 'rgba(91,91,214,0.02)' }
      ] } }
    }]
  }
})

// ══════════════ 自动刷新 ══════════════
function startTimer() {
  stopTimer()
  if (autoRefresh.value) {
    timer = window.setInterval(() => load(true), 30000)   // 资源/健康 30s
    qpsTimer = window.setInterval(loadQps, 5000)          // QPS 曲线 5s 实时
  }
}
function stopTimer() {
  if (timer != null) { clearInterval(timer); timer = null }
  if (qpsTimer != null) { clearInterval(qpsTimer); qpsTimer = null }
}
function onToggle(v: boolean) {
  autoRefresh.value = v
  localStorage.setItem(AUTO_REFRESH_KEY, String(v))
  if (v) load().then(startTimer)
  else stopTimer()
}

onMounted(() => {
  load().then(startTimer)
})
onUnmounted(() => {
  stopTimer()
})

// ══════════════ 派生 ══════════════
const sys = computed(() => overview.value.system ?? {})
const qpsStat = computed(() => overview.value.qps ?? {})
const pools = computed(() => overview.value.pools ?? [])
const fw = computed(() => overview.value.framework ?? {})

const plugins = computed(() => health.value.plugins ?? {})
const dedup = computed(() => health.value.dedup ?? {})
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
function fmtDetected(ms: any): string {
  const n = Number(ms)
  if (!isFinite(n) || n <= 0) return '—'
  return new Date(n).toLocaleTimeString('zh-CN', { hour12: false })
}

// 资源进度条（value=百分比 0-100）
function barColor(v: number): string {
  if (v >= 85) return '#e5484d'
  if (v >= 65) return '#f0a020'
  return '#18a058'
}

// ══════════════ 原有健康卡片（插件/去重/慢阶段/熔断/连接） ══════════════
const slowStageRows = computed(() =>
  Object.entries(slowStages.value).map(([name, cnt]) => ({ name, count: Number(cnt) }))
)
const slowStageTotal = computed(() => slowStageRows.value.reduce((s, r) => s + r.count, 0))
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
    const sessions = Array.isArray(list) ? list : list?.sessions ?? []
    sessions.forEach((r: any) => rows.push({ ...r, platform: plat }))
  })
  return rows
})
const wsConnected = computed(() => connRows.value.filter((r) => r.state === 'CONNECTED').length)
const wsTotal = computed(() => connRows.value.length)

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

const poolCols = [
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
const slowCols = [
  { title: '阶段', key: 'name' },
  { title: '超过 100ms 次数', key: 'count' }
]
const circuitCols = [
  { title: '平台', key: 'platform', render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => r.platform }) },
  { title: 'AppID', key: 'appId' },
  { title: '状态', key: 'state', render: (r: any) => h(NTag, { size: 'small', bordered: false, type: stateType(r.state) }, { default: () => stateLabel(r.state) }) },
  { title: '连续失败', key: 'consecutiveFailures' },
  { title: '冷却剩余(ms)', key: 'cooldownRemainingMs' }
]
const connCols = [
  { title: '平台', key: 'platform', render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => r.platform }) },
  { title: 'RobotID', key: 'robotId' },
  { title: '环境', key: 'envType' },
  { title: '状态', key: 'state', render: (r: any) => h(NTag, { size: 'small', bordered: false, type: stateType(r.state) }, { default: () => stateLabel(r.state) }) },
  { title: '运行', key: 'running', render: (r: any) => h(NTag, { size: 'small', bordered: false, type: r.running ? 'success' : 'default' }, { default: () => (r.running ? '是' : '否') }) },
  { title: '累计事件', key: 'totalEvents' },
  { title: '累计重连', key: 'totalReconnects' }
]

// 模板说明
const tuneMode = ref<'eco' | 'perf'>('eco')
const activeTune = computed(() => tune.value[tuneMode.value])
const capacity = computed(() => activeTune.value?.capacity ?? {})
const params = computed(() => activeTune.value?.params ?? {})
</script>

<template>
  <div>
    <PageHero title="运行健康监控" subtitle="系统资源 · QPS 曲线 · 线程池 · 性能模板 · 插件/去重/熔断/连接" :icon="PulseOutline">
      <NSpace align="center">
        <NTooltip trigger="hover">
          <template #trigger><NSwitch :value="autoRefresh" @update:value="onToggle" /></template>
          自动刷新：资源 30s · QPS 曲线 5s
        </NTooltip>
        <NText depth="3" style="font-size: 12px">{{ lastUpdate ? '更新于 ' + lastUpdate : '—' }}</NText>
        <NButton type="primary" :loading="loading" @click="load">手动刷新</NButton>
      </NSpace>
    </PageHero>

    <NEmpty v-if="err" :description="'加载失败：' + err" style="padding: 60px 0" />

    <template v-else>
      <!-- ═══════ ① 系统资源概览 ═══════ -->
      <NCard title="系统资源" class="b-card">
        <template #header-extra><NIcon size="18" color="#2090e0"><HardwareChipOutline /></NIcon></template>
        <NGrid :cols="24" :x-gap="16" :y-gap="18" responsive="screen" item-responsive>
          <!-- CPU -->
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>CPU 占用率</NText><NText :style="{ color: barColor(sys.cpuLoad ?? 0) }" strong>{{ sys.cpuLoad ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.cpuLoad ?? 0) + '%', background: barColor(sys.cpuLoad ?? 0) }" /></div>
              <div class="res-sub">CPU 核数 {{ sys.cpuCores ?? '—' }} · 负载 {{ sys.systemLoadAvg ?? '—' }}</div>
            </div>
          </NGi>
          <!-- 内存 -->
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>内存占用率</NText><NText :style="{ color: barColor(sys.memRatio ?? 0) }" strong>{{ sys.memRatio ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.memRatio ?? 0) + '%', background: barColor(sys.memRatio ?? 0) }" /></div>
              <div class="res-sub">已用 {{ fmtBytes(sys.memUsed) }} / 共 {{ fmtBytes(sys.memTotal) }}</div>
            </div>
          </NGi>
          <!-- 磁盘 -->
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>硬盘使用率</NText><NText :style="{ color: barColor(sys.diskRatio ?? 0) }" strong>{{ sys.diskRatio ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.diskRatio ?? 0) + '%', background: barColor(sys.diskRatio ?? 0) }" /></div>
              <div class="res-sub">已用 {{ fmtBytes(sys.diskUsed) }} / 共 {{ fmtBytes(sys.diskTotal) }}</div>
            </div>
          </NGi>
          <!-- JVM 堆 -->
          <NGi span="24 m:12 l:6">
            <div class="res-block">
              <div class="res-head"><NText strong>JVM 堆内存</NText><NText :style="{ color: barColor(sys.jvmRatio ?? 0) }" strong>{{ sys.jvmRatio ?? 0 }}%</NText></div>
              <div class="bar"><div class="bar-inner" :style="{ width: (sys.jvmRatio ?? 0) + '%', background: barColor(sys.jvmRatio ?? 0) }" /></div>
              <div class="res-sub">已用 {{ fmtBytes(sys.jvmUsed) }} / 最大 {{ fmtBytes(sys.jvmMax) }}</div>
            </div>
          </NGi>
        </NGrid>
        <NDivider style="margin: 14px 0 10px" />
        <NGrid :cols="24" :x-gap="16" :y-gap="12" responsive="screen" item-responsive>
          <NGi span="24 m:8 l:6"><div class="kv"><NText depth="3" class="kv-label">运行时间</NText><NText strong><NIcon size="14" style="vertical-align:-2px"><TimeOutline /></NIcon> {{ fmtUptime(sys.uptimeSeconds) }}</NText></div></NGi>
          <NGi span="24 m:8 l:6"><div class="kv"><NText depth="3" class="kv-label">操作系统</NText><NText strong>{{ sys.osName ?? '—' }} {{ sys.osVersion ?? '' }} ({{ sys.osArch ?? '' }})</NText></div></NGi>
          <NGi span="24 m:8 l:6"><div class="kv"><NText depth="3" class="kv-label">Java 环境</NText><NText strong>{{ sys.javaVendor ?? '—' }} {{ sys.javaVersion ?? '' }}</NText></div></NGi>
          <NGi span="24 m:8 l:6"><div class="kv"><NText depth="3" class="kv-label">磁盘明细</NText><NText strong>{{ (sys.disks ?? []).map((d: any) => d.path + ' ' + d.ratio + '%').join(' · ') || '—' }}</NText></div></NGi>
          <NGi span="24 m:8 l:6"><div class="kv"><NText depth="3" class="kv-label">框架数据</NText><NText strong>机器人 {{ fw.bots ?? 0 }} · 群 {{ fw.groups ?? 0 }} · 好友 {{ fw.friends ?? 0 }}</NText></div></NGi>
          <NGi span="24 m:8 l:6"><div class="kv"><NText depth="3" class="kv-label">消息 / 事件</NText><NText strong>消息 {{ fw.messages ?? 0 }} · 事件 {{ fw.events ?? 0 }}</NText></div></NGi>
        </NGrid>
      </NCard>

      <!-- ═══════ ② QPS 实时曲线 + 统计 ═══════ -->
      <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
        <NGi span="24 l:16">
          <NCard title="QPS 实时曲线（入站事件 / 秒）" class="b-card">
            <template #header-extra><NIcon size="18" color="#5b5bd6"><StatsChartOutline /></NIcon></template>
            <CommonChart :option="qpsOption" height="260px" />
          </NCard>
        </NGi>
        <NGi span="24 l:8">
          <NCard title="QPS 统计" class="b-card">
            <template #header-extra><NIcon size="18" color="#5b5bd6"><SpeedometerOutline /></NIcon></template>
            <NGrid :cols="1" :x-gap="12" :y-gap="16">
              <NGi><div class="kv"><div class="kv-label">当前 QPS（近 1 秒）</div><NStatistic :value="qpsStat.current ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">峰值 QPS（近 60 秒）</div><NStatistic :value="qpsStat.peak60 ?? 0" :value-style="{ color: '#5b5bd6' }" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">平均 QPS（近 60 秒）</div><NStatistic :value="qpsStat.avg60 ?? 0" :precision="2" /></div></NGi>
            </NGrid>
            <div class="hint">QPS = 每秒进入框架的事件数（群消息/单聊消息/系统事件，经 BotPipeline 统一计数）。</div>
          </NCard>
        </NGi>
      </NGrid>

      <!-- ═══════ ③ 线程池信息 ═══════ -->
      <NCard title="线程池 / 连接池明细" class="b-card">
        <template #header-extra><NIcon size="18" color="#f0a020"><LayersOutline /></NIcon></template>
        <NDataTable v-if="pools.length" :columns="poolCols" :data="pools" :bordered="false" size="small" :scroll-x="1200" />
        <NEmpty v-else description="暂无已注册的线程池（应用启动后自动注册）" style="padding: 30px 0" />
      </NCard>

      <!-- ═══════ ④ 性能模板说明 ═══════ -->
      <NCard title="性能模板推荐（基于当前系统资源估算）" class="b-card">
        <template #header-extra><NIcon size="18" color="#18a058"><InformationCircleOutline /></NIcon></template>
        <NSpace vertical :size="14">
          <NRadioGroup v-model:value="tuneMode">
            <NRadioButton value="eco">经济模板（舒适优先）</NRadioButton>
            <NRadioButton value="perf">性能模板（极限吞吐）</NRadioButton>
          </NRadioGroup>
          <NSpin :show="!activeTune">
            <template v-if="activeTune">
              <NAlert type="info" :show-icon="true" style="margin-bottom: 12px">
                <template #title>{{ activeTune.modeLabel }}模板 · 检测到本机 {{ activeTune.sysSummary.cpuCores }} 核 / {{ activeTune.sysSummary.memGb }} GB（检测于 {{ fmtDetected(activeTune.detectedAt) }}）</template>
                {{ params.note }}
              </NAlert>
              <NGrid :cols="24" :x-gap="16" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 12px">
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">定时任务调度池</div><NText strong>{{ fmtInt(params.schedPool) }} 线程</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">WS 连接池</div><NText strong>{{ fmtInt(params.wsCore) }} / {{ fmtInt(params.wsMax) }}</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">每库 Hikari 连接</div><NText strong>{{ fmtInt(params.hikari) }}</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">出站节奏</div><NText strong>{{ fmtInt(params.paceMs) }} ms/条</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">可支持机器人</div><NText strong style="color:#5b5bd6">{{ fmtInt(capacity.maxBots) }} 个</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">入站吞吐</div><NText strong>{{ capacity.msgInPerSec ?? '—' }} 条/秒</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">出站吞吐</div><NText strong>{{ capacity.msgOutPerSec ?? '—' }} 条/秒</NText></div></NGi>
                <NGi span="12 m:6"><div class="kv"><div class="kv-label">单 bot 内存估算</div><NText strong>{{ fmtInt(capacity.perBotMemMb) }} MB</NText></div></NGi>
              </NGrid>
              <NAlert type="warning" :show-icon="true" title="风险提示">
                <ul class="risk-list">
                  <li v-for="(r, i) in activeTune.risks" :key="i">{{ r }}</li>
                </ul>
              </NAlert>
            </template>
          </NSpin>
        </NSpace>
      </NCard>

      <!-- ═══════ ⑤ 原有监控卡片 ═══════ -->
      <NGrid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
        <NGi span="2 m:1">
          <NCard title="插件执行" class="b-card">
            <template #header-extra><NIcon size="18" color="#5b5bd6"><CubeOutline /></NIcon></template>
            <NGrid :cols="2" :x-gap="14" :y-gap="14">
              <NGi><div class="kv"><div class="kv-label">群命令处理器</div><NStatistic :value="plugins.groupHandlers ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">私聊命令处理器</div><NStatistic :value="plugins.privateHandlers ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">群事件处理器</div><NStatistic :value="plugins.groupEventHandlers ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">插件超时次数</div><NStatistic :value="plugins.pluginTimeoutCount ?? 0" :value-style="{ color: (plugins.pluginTimeoutCount ?? 0) > 0 ? '#e5484d' : undefined }" /></div></NGi>
            </NGrid>
            <div class="hint">最近超时插件：
              <NTag :bordered="false" :type="plugins.lastTimeoutPlugin ? 'error' : 'default'" size="small">{{ plugins.lastTimeoutPlugin || '—' }}</NTag>
            </div>
          </NCard>
        </NGi>
        <NGi span="2 m:1">
          <NCard title="消息去重" class="b-card">
            <template #header-extra><NIcon size="18" color="#18a058"><ShieldCheckmarkOutline /></NIcon></template>
            <NGrid :cols="3" :x-gap="14" :y-gap="14">
              <NGi><div class="kv"><div class="kv-label">DB 命中</div><NStatistic :value="dedup.dbDedupSuccess ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">本地降级</div><NStatistic :value="dedup.localFallbackCount ?? 0" :value-style="{ color: (dedup.localFallbackCount ?? 0) > 0 ? '#f0a020' : undefined }" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">窗口内记录</div><NStatistic :value="dedup.seenSize ?? 0" /></div></NGi>
            </NGrid>
          </NCard>
        </NGi>
      </NGrid>

      <NCard title="Pipeline 慢阶段（单阶段 > 100ms）" class="b-card">
        <template #header-extra><NIcon size="18" color="#5b5bd6"><SpeedometerOutline /></NIcon></template>
        <NDataTable v-if="slowStageRows.length" :columns="slowCols" :data="slowStageRows" :bordered="false" size="small" />
        <NEmpty v-else description="暂无慢阶段，处理性能良好" style="padding: 30px 0" />
      </NCard>

      <NCard title="电路熔断（按 AppID）" class="b-card">
        <template #header-extra><NIcon size="18" color="#e5484d"><AlertCircleOutline /></NIcon></template>
        <NDataTable v-if="circuitRows.length" :columns="circuitCols" :data="circuitRows" :bordered="false" size="small" />
        <NEmpty v-else description="暂无已注册的平台熔断器" style="padding: 30px 0" />
      </NCard>

      <NCard title="WebSocket 连接状态" class="b-card">
        <template #header-extra><NIcon size="18" color="#2090e0"><ServerOutline /></NIcon></template>
        <NSpace style="margin-bottom: 10px" :size="8">
          <NTag size="small" type="success" :bordered="false">已连接 {{ wsConnected }}</NTag>
          <NTag size="small" type="default" :bordered="false">总数 {{ wsTotal }}</NTag>
        </NSpace>
        <NDataTable v-if="connRows.length" :columns="connCols" :data="connRows" :bordered="false" size="small" />
        <NEmpty v-else description="暂无 WebSocket 连接" style="padding: 30px 0" />
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.grid { margin-top: 4px; }
.b-card { margin-top: 16px; }
.res-block { display: flex; flex-direction: column; gap: 8px; }
.res-head { display: flex; align-items: center; justify-content: space-between; }
.bar { height: 8px; border-radius: 4px; background: var(--n-divider-color); overflow: hidden; }
.bar-inner { height: 100%; border-radius: 4px; transition: width 0.6s ease, background 0.3s; }
.res-sub { font-size: 12px; color: var(--n-text-color-3); }
.kv { display: flex; flex-direction: column; gap: 4px; }
.kv-label { font-size: 13px; color: var(--n-text-color-2); }
.hint { margin-top: 12px; font-size: 12px; color: var(--n-text-color-3); line-height: 1.6; }
.qps-chart { width: 100%; height: 260px; }
.risk-list { margin: 0; padding-left: 18px; line-height: 1.8; font-size: 13px; }
</style>
