<script setup lang="ts">
import { ref, computed, onMounted, h, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NSelect, NDataTable, NInputNumber, NIcon, NSpin, NTag, NTooltip, NAlert
} from 'naive-ui'
import {
  StatsChartOutline, CubeOutline, FlameOutline, FlashOutline, SaveOutline, DownloadOutline
} from '@vicons/ionicons5'
import api from '../api'
import type { UsageOverview, GroupUsageRow, ProactiveLogRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'
import StatCard from '../components/StatCard.vue'
import CommonChart from '../components/CommonChart.vue'
import EmptyState from '../components/EmptyState.vue'
import dayjs from 'dayjs'
import { useBotsStore } from '../stores/bots'

const message = useMessage()
const botsStore = useBotsStore()

const botKey = ref<string>('')
const overview = ref<UsageOverview>({ totalToday: 0, bots: [] })
const groups = ref<GroupUsageRow[]>([])
const proactiveLogs = ref<ProactiveLogRow[]>([])
const loading = ref(false)
const proLoading = ref(false)
const proactiveLimit = ref(50)
const dirtyGroups = ref(new Set<string>()) // 未保存限额的群

const botOptions = computed(() =>
  botsStore.bots.map(b => ({ label: b.name || b.botKey || b.appId || '', value: b.botKey || b.appId || '' }))
)

/** 时间（后端 UTC/ISO）→ UTC+8 展示。 */
function fmtSubTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const d = dayjs(String(v))
  return d.isValid() ? d.utcOffset(8).format('YYYY-MM-DD HH:mm:ss') : String(v)
}
/** 大数字 K/M 缩写（图表轴/tooltip 用）。 */
function fmtShort(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const botToday = computed(() => {
  const b = overview.value.bots.find(x => x.botKey === botKey.value)
  return b?.tokens || 0
})
const quotaCount = computed(() => groups.value.filter(g => (g.dailyLimit || 0) > 0).length)

const chartOption = computed(() => ({
  title: { text: '各群今日 Token 用量', left: 'center', textStyle: { fontSize: 13 } },
  tooltip: { trigger: 'axis', valueFormatter: (v: number) => fmtShort(v) },
  grid: { left: 50, right: 16, top: 40, bottom: 30 },
  xAxis: { type: 'category', data: groups.value.map(g => shortId(g.groupId)), axisLabel: { fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { formatter: (v: number) => fmtShort(v) } },
  series: [{
    type: 'bar',
    data: groups.value.map(g => g.todayUsed || 0),
    itemStyle: { color: '#5b8def', borderRadius: [4, 4, 0, 0] },
    barMaxWidth: 36
  }]
}))

function shortId(id: string) {
  return id && id.length > 10 ? id.slice(0, 10) : id
}

const columns = [
  { title: '群 ID', key: 'groupId', width: 200 },
  { title: '今日用量', key: 'todayUsed', width: 110, render: (r: GroupUsageRow) => (r.todayUsed || 0) + ' tokens' },
  { title: '每日限额', key: 'limit', width: 180, render: (r: GroupUsageRow) =>
      h(NInputNumber, {
        value: r.dailyLimit || 0, min: 0, step: 100000, style: 'width: 130px',
        'onUpdate:value': (v: number | null) => { r.dailyLimit = v || 0; const s = new Set(dirtyGroups.value); s.add(r.groupId); dirtyGroups.value = s },
        placeholder: '0=不限'
      }) },
  { title: '状态', key: 'status', width: 130, render: (r: GroupUsageRow) => {
      if (dirtyGroups.value.has(r.groupId)) {
        return h(NTag, { size: 'small', type: 'warning' }, { default: () => '未保存' })
      }
      const limit = r.dailyLimit || 0
      if (limit > 0 && (r.todayUsed || 0) >= limit) {
        return h(NTag, { size: 'small', type: 'error' }, { default: () => '已超限' })
      }
      return limit > 0 ? h(NTag, { size: 'small', type: 'success' }, { default: () => '限额中' })
        : h(NTag, { size: 'small', type: 'default' }, { default: () => '不限' })
    } },
  { title: '操作', key: 'actions', width: 90, render: (r: GroupUsageRow) =>
      h(NButton, { size: 'tiny', type: 'primary', secondary: true, onClick: () => saveQuota(r) },
        { default: () => '保存限额' }) },
]

async function loadOverview() {
  try {
    overview.value = await api.llmApi.usageOverview()
  } catch (e: any) {
    message.error('加载用量失败: ' + (e.message || e))
  }
}

async function loadGroups() {
  if (!botKey.value) return
  loading.value = true
  try {
    groups.value = await api.llmApi.usageGroups(botKey.value)
  } catch (e: any) {
    message.error('加载群用量失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadProactiveLogs() {
  if (!botKey.value) return
  proLoading.value = true
  try {
    proactiveLogs.value = await api.llmApi.proactiveLogs(botKey.value, proactiveLimit.value)
  } catch (e: any) {
    message.error('加载主动记录失败: ' + (e.message || e))
  } finally {
    proLoading.value = false
  }
}

function loadMoreProactive() {
  proactiveLimit.value += 50
  loadProactiveLogs()
}

const proactiveColumns = [
  { title: '时间', key: 'createdAt', width: 150, render: (r: ProactiveLogRow) => fmtSubTime(r.createdAt) },
  { title: '类型', key: 'type', width: 90, render: (r: ProactiveLogRow) =>
      r.type === 'ASK'
        ? h(NTag, { size: 'small', type: 'warning' }, { default: () => '@问话' })
        : h(NTag, { size: 'small', type: 'info' }, { default: () => '话题卡片' }) },
  { title: '内容', key: 'content', ellipsis: { tooltip: true } },
]

async function loadBots() {
  try {
    await botsStore.loadBots()
    if (botsStore.bots.length > 0) botKey.value = botsStore.bots[0].botKey || botsStore.bots[0].appId || ''
  } catch (e: any) {
    message.error('加载机器人失败: ' + (e.message || e))
  }
}

async function saveQuota(r: GroupUsageRow) {
  try {
    await api.llmApi.setGroupQuota(botKey.value, r.groupId, r.dailyLimit || 0)
    message.success('限额已保存')
    const s = new Set(dirtyGroups.value); s.delete(r.groupId); dirtyGroups.value = s
    await loadGroups()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

// 有未保存限额时，离开/刷新提醒
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirtyGroups.value.size > 0) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  await loadBots()
  await loadOverview()
  if (botKey.value) {
    await loadGroups()
    await loadProactiveLogs()
  }
})
onUnmounted(() => {
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
  <div class="page">
    <PageHero title="用量统计" subtitle="Token 用量按 bot/群统计；可对每个群设每日限额，超限当天不再触发 AI" :icon="StatsChartOutline">
      <NSelect v-model:value="botKey" :options="botOptions" placeholder="选择机器人" clearable style="width: 200px"
        @update:value="() => { loadGroups(); loadProactiveLogs() }" />
    </PageHero>

    <div class="cards">
      <StatCard :icon="CubeOutline" color="#5b8def" :value="overview.totalToday" label="今日总用量（tokens）" sub="全部机器人" />
      <StatCard :icon="FlashOutline" color="#36ad6a" :value="botToday" label="当前机器人今日用量" sub="tokens" />
      <StatCard :icon="FlameOutline" color="#f0a020" :value="quotaCount" label="已设限额的群" sub="0=不限" />
    </div>

    <NCard :bordered="true" title="群用量与限额">
      <template v-if="!botKey">
        <EmptyState description="请先选择机器人" />
      </template>
      <NSpin :show="loading" v-else>
        <CommonChart :option="chartOption" height="240px" />
        <div class="table-gap" />
        <NAlert v-if="dirtyGroups.size > 0" type="warning" :bordered="false" style="margin-bottom: 8px">
          有 {{ dirtyGroups.size }} 个群的限额未保存，切换/离开前请先「保存限额」。
        </NAlert>
        <NDataTable :columns="columns" :data="groups" :row-key="(r: GroupUsageRow) => r.groupId" :bordered="false" :pagination="{ pageSize: 20 }" />
      </NSpin>
    </NCard>

    <NCard :bordered="true" title="主动搭话记录">
      <template v-if="!botKey">
        <EmptyState description="请先选择机器人" />
      </template>
      <NSpin :show="proLoading" v-else>
        <EmptyState v-if="proactiveLogs.length === 0" description="暂无主动搭话记录（需在 AI 设置开启主动搭话）" />
        <NDataTable v-else :columns="proactiveColumns" :data="proactiveLogs" :row-key="(r: ProactiveLogRow) => r.id" :bordered="false" />
        <NButton v-if="proactiveLogs.length > 0 && proactiveLogs.length >= proactiveLimit" size="small" secondary style="margin-top: 8px" @click="loadMoreProactive">
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          加载更多
        </NButton>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.table-gap {
  height: 12px;
}
</style>
