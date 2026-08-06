<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h, type Component } from 'vue'
import {
  NGrid, NGi, NCard, NStatistic, NButton, NSpace, NIcon, NText,
  NEmpty, NTag, NDataTable, NSwitch, NTooltip
} from 'naive-ui'
import {
  AlertCircleOutline, ShieldCheckmarkOutline, GitBranchOutline,
  ServerOutline, SpeedometerOutline, CubeOutline, HardwareChipOutline, PulseOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'

interface CircuitRow {
  appId: string
  state: string
  consecutiveFailures: number
  cooldownRemainingMs: number
}
interface ConnRow {
  key: string
  robotId: string
  envType: string
  state: string
  running: boolean
  totalEvents: number
  totalReconnects: number
}

const data = ref<Record<string, any>>({})
const err = ref('')
const loading = ref(false)
const HEALTH_AUTO_REFRESH_KEY = 'xuanji.health.autoRefresh'
// 默认开；用户开关持久化到本地存储，切换页面/刷新浏览器后保留状态
const autoRefresh = ref(localStorage.getItem(HEALTH_AUTO_REFRESH_KEY) !== 'false')
const lastUpdate = ref<string>('')

let timer: number | null = null

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    data.value = await api.getHealth()
    err.value = ''
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e: any) {
    err.value = e.message
  } finally {
    if (!silent) loading.value = false
  }
}

function startTimer() {
  stopTimer()
  if (autoRefresh.value) {
    // 轮询静默刷新（silent=true），避免 loading 状态导致卡片闪烁
    timer = window.setInterval(() => load(true), 30000) // 默认 30 秒轮询
  }
}
function stopTimer() {
  if (timer != null) {
    clearInterval(timer)
    timer = null
  }
}
function onToggle(v: boolean) {
  autoRefresh.value = v
  localStorage.setItem(HEALTH_AUTO_REFRESH_KEY, String(v))
  if (v) load().then(startTimer)
  else stopTimer()
}

onMounted(() => {
  load().then(startTimer)
})
onUnmounted(stopTimer)

// ══════ 派生指标 ══════
const plugins = computed(() => data.value.plugins ?? {})
const dedup = computed(() => data.value.dedup ?? {})
const slowStages = computed(() => data.value.pipelineSlowStages ?? {})

const slowStageRows = computed(() =>
  Object.entries(slowStages.value).map(([name, cnt]) => ({ name, count: Number(cnt) }))
)
const slowStageTotal = computed(() =>
  slowStageRows.value.reduce((s, r) => s + r.count, 0)
)

const platforms = computed(() => data.value.platforms ?? {})
const connections = computed(() => data.value.connections ?? {})

const circuitRows = computed<CircuitRow[]>(() => {
  const rows: CircuitRow[] = []
  Object.entries(platforms.value).forEach(([plat, m]: any) => {
    const cb = m?.circuitBreaker
    if (Array.isArray(cb)) {
      cb.forEach((r) => rows.push({ ...r, platform: plat }))
    } else if (cb && typeof cb === 'object') {
      // 后端返回单对象（{open, consecutiveFailures, openedAt}）→ 包装成一行
      rows.push({ ...cb, platform: plat })
    }
  })
  return rows
})

const connRows = computed<ConnRow[]>(() => {
  const rows: ConnRow[] = []
  Object.entries(connections.value).forEach(([plat, list]: any) => {
    // 后端返回 {total, online, sessions:[...]}；兼容历史数组形态
    const sessions = Array.isArray(list) ? list : list?.sessions ?? []
    sessions.forEach((r: any) => rows.push({ ...r, platform: plat }))
  })
  return rows
})

const wsConnected = computed(() => connRows.value.filter((r) => r.state === 'CONNECTED').length)
const wsTotal = computed(() => connRows.value.length)

// 顶部汇总卡
const summary = computed(() => [
  { key: 'pluginTimeout', label: '插件超时次数', icon: AlertCircleOutline, color: '#e5484d', val: plugins.value.pluginTimeoutCount ?? 0 },
  { key: 'dedupOk', label: '去重命中(DB)', icon: ShieldCheckmarkOutline, color: '#18a058', val: dedup.value.dbDedupSuccess ?? 0 },
  { key: 'dedupFallback', label: '去重降级(本地)', icon: GitBranchOutline, color: '#f0a020', val: dedup.value.localFallbackCount ?? 0 },
  { key: 'slowStages', label: '慢阶段累计', icon: SpeedometerOutline, color: '#5b5bd6', val: slowStageTotal.value },
  { key: 'wsConnected', label: 'WS 已连接', icon: ServerOutline, color: '#18a058', val: wsConnected.value },
  { key: 'wsTotal', label: 'WS 连接总数', icon: HardwareChipOutline, color: '#2090e0', val: wsTotal.value }
])

// 状态着色
function stateType(s: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (s) {
    case 'CLOSED':
    case 'CONNECTED':
      return 'success'
    case 'OPEN':
      return 'error'
    case 'RECONNECTING':
    case 'CONNECTING':
    case 'IDENTIFYING':
    case 'RESUMING':
      return 'warning'
    default:
      return 'default'
  }
}

// 状态中文标签
function stateLabel(s: string): string {
  const map: Record<string, string> = {
    CONNECTED: '已连接', DISCONNECTED: '已断开', CONNECTING: '连接中',
    IDENTIFYING: '鉴权中', RESUMING: '恢复中', RECONNECTING: '重连中',
    CLOSED: '已关闭', OPEN: '已熔断'
  }
  return map[s] ?? s
}

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
const slowCols = [
  { title: '阶段', key: 'name' },
  { title: '超过 100ms 次数', key: 'count' }
]

function fmtLastTimeout(v: any): string {
  return v ? String(v) : '—'
}
</script>

<template>
  <div>
    <!-- Hero -->
    <PageHero
      title="运行健康监控"
      subtitle="插件超时 · 去重 · Pipeline 慢阶段 · 熔断 · WebSocket 连接"
      :icon="PulseOutline"
    >
      <NSpace align="center">
        <NTooltip trigger="hover">
          <template #trigger>
            <NSwitch :value="autoRefresh" @update:value="onToggle" />
          </template>
          每 30 秒自动刷新
        </NTooltip>
        <NText depth="3" style="font-size: 12px">
          {{ lastUpdate ? '更新于 ' + lastUpdate : '—' }}
        </NText>
        <NButton type="primary" :loading="loading" @click="load">手动刷新</NButton>
      </NSpace>
    </PageHero>

    <NEmpty v-if="err" :description="'加载失败：' + err" style="padding: 60px 0" />

    <template v-if="!err">
      <!-- 汇总卡 -->
      <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
        <NGi v-for="c in summary" :key="c.key" span="24 s:12 m:8 l:6 xl:4">
          <NCard hoverable class="stat-card">
            <div class="stat-top">
              <div class="stat-icon" :style="{ background: c.color + '1a', color: c.color }">
                <NIcon size="20"><component :is="c.icon" /></NIcon>
              </div>
              <NStatistic :value="c.val" :tabular-nums="true" class="stat-value" />
            </div>
            <NText depth="3" class="stat-label">{{ c.label }}</NText>
          </NCard>
        </NGi>
      </NGrid>

      <!-- 插件执行 & 去重 -->
      <NGrid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
        <NGi span="2 m:1">
          <NCard title="插件执行" class="b-card">
            <template #header-extra>
              <NIcon size="18" color="#5b5bd6"><CubeOutline /></NIcon>
            </template>
            <NGrid :cols="2" :x-gap="14" :y-gap="14">
              <NGi><div class="kv"><div class="kv-label">群命令处理器</div><NStatistic :value="plugins.groupHandlers ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">私聊命令处理器</div><NStatistic :value="plugins.privateHandlers ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">群事件处理器</div><NStatistic :value="plugins.groupEventHandlers ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">插件超时次数</div><NStatistic :value="plugins.pluginTimeoutCount ?? 0" :value-style="{ color: (plugins.pluginTimeoutCount ?? 0) > 0 ? '#e5484d' : undefined }" /></div></NGi>
            </NGrid>
            <div class="hint">
              最近一次超时的插件：
              <NTag :bordered="false" :type="plugins.lastTimeoutPlugin ? 'error' : 'default'" size="small">
                {{ fmtLastTimeout(plugins.lastTimeoutPlugin) }}
              </NTag>
            </div>
          </NCard>
        </NGi>

        <NGi span="2 m:1">
          <NCard title="消息去重" class="b-card">
            <template #header-extra>
              <NIcon size="18" color="#18a058"><ShieldCheckmarkOutline /></NIcon>
            </template>
            <NGrid :cols="3" :x-gap="14" :y-gap="14">
              <NGi><div class="kv"><div class="kv-label">DB 命中</div><NStatistic :value="dedup.dbDedupSuccess ?? 0" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">本地降级</div><NStatistic :value="dedup.localFallbackCount ?? 0" :value-style="{ color: (dedup.localFallbackCount ?? 0) > 0 ? '#f0a020' : undefined }" /></div></NGi>
              <NGi><div class="kv"><div class="kv-label">窗口内记录</div><NStatistic :value="dedup.seenSize ?? 0" /></div></NGi>
            </NGrid>
            <div class="hint">
              <NTooltip trigger="hover">
                <template #trigger><NText depth="3" style="font-size: 12px">本地降级说明</NText></template>
                写 xuanji_dedup 失败时退化为内存去重，重启会丢失。event_id 过长（>128 字符）触发。
              </NTooltip>
            </div>
          </NCard>
        </NGi>
      </NGrid>

      <!-- Pipeline 慢阶段 -->
      <NCard title="Pipeline 慢阶段（单阶段 > 100ms）" class="b-card">
        <template #header-extra>
          <NIcon size="18" color="#5b5bd6"><SpeedometerOutline /></NIcon>
        </template>
        <NDataTable v-if="slowStageRows.length" :columns="slowCols" :data="slowStageRows" :bordered="false" size="small" />
        <NEmpty v-else description="暂无慢阶段，处理性能良好" style="padding: 30px 0" />
      </NCard>

      <!-- 熔断器 -->
      <NCard title="电路熔断（按 AppID）" class="b-card">
        <template #header-extra>
          <NIcon size="18" color="#e5484d"><AlertCircleOutline /></NIcon>
        </template>
        <NDataTable v-if="circuitRows.length" :columns="circuitCols" :data="circuitRows" :bordered="false" size="small" />
        <NEmpty v-else description="暂无已注册的平台熔断器" style="padding: 30px 0" />
      </NCard>

      <!-- WebSocket 连接 -->
      <NCard title="WebSocket 连接状态" class="b-card">
        <template #header-extra>
          <NIcon size="18" color="#2090e0"><ServerOutline /></NIcon>
        </template>
        <NDataTable v-if="connRows.length" :columns="connCols" :data="connRows" :bordered="false" size="small" />
        <NEmpty v-else description="暂无 WebSocket 连接" style="padding: 30px 0" />
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.grid {
  margin-top: 4px;
}
.stat-card {
  height: 100%;
}
.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-value {
  font-size: 24px;
}
.stat-label {
  font-size: 13px;
}
.b-card {
  margin-top: 16px;
}
.kv {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kv-label {
  font-size: 13px;
  color: var(--n-text-color-2);
}
.hint {
  margin-top: 14px;
  font-size: 13px;
  color: var(--n-text-color-2);
}
</style>
