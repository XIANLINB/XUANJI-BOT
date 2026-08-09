<script setup lang="ts">
import { ref, computed, onMounted, type Component } from 'vue'
import {
  NGrid, NGi, NCard, NButton, NSpace, NIcon, NText, NTag, NRadioGroup, NRadioButton, NEmpty,
  NTimeline, NTimelineItem, NNumberAnimation
} from 'naive-ui'
import {
  AppsOutline, PeopleOutline, PersonOutline, ChatbubbleOutline,
  TrendingUpOutline, TrendingDownOutline, CubeOutline, PulseOutline,
  RocketOutline, GridOutline, BarChartOutline, TimeOutline, ShieldCheckmarkOutline,
  ExtensionPuzzleOutline, HourglassOutline
} from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'
import StatCard from '../components/StatCard.vue'
import { useDashboardStore } from '../stores/dashboard'

const store = useDashboardStore()

const d = ref<Record<string, any>>({})
const err = ref('')
const loading = ref(false)

// ═══════════ 框架版本日志（时间线） ═══════════
const versionLog = ref<{ current: string; versions: any[] }>({ current: '', versions: [] })
const versionTagType: Record<string, 'success' | 'info' | 'warning' | 'default'> = {
  正式版: 'success', 开发里程碑: 'info', 规划中: 'warning'
}

async function loadVersionLog() {
  try {
    versionLog.value = await api.getVersionLog()
  } catch {
    versionLog.value = { current: '', versions: [] }
  }
}

async function load() {
  loading.value = true
  try {
    d.value = await api.getDashboard()
    err.value = ''
  } catch (e: any) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}

const cards = computed(() => {
  const v = d.value
  // 命令数 = 按 命令名|插件|方法 去重后的实际命令数（与命令管理页同源）
  const cmdTotal = Number(v.commandCount ?? 0)
  // 消息去重总命中（DB 命中 + 本地降级）
  const dedupTotal = Number(v.dedup?.dbDedupSuccess ?? 0)
    + Number(v.dedup?.localFallbackCount ?? 0)
  const pluginTimeout = Number(v.plugins?.pluginTimeoutCount ?? 0)
  return [
    { key: 'botsOnline', label: '在线机器人', icon: RocketOutline, color: '#18a058', val: v.botsOnline ?? 0 },
    { key: 'botsTotal', label: '机器人总数', icon: AppsOutline, color: '#5b5bd6', val: v.botsTotal ?? 0 },
    { key: 'groupsTotal', label: '群聊总数量', icon: PeopleOutline, color: '#2090e0', val: v.groupsTotal ?? 0 },
    { key: 'friendsTotal', label: '好友总数量', icon: PersonOutline, color: '#f0a020', val: v.friendsTotal ?? 0 },
    { key: 'todayGroupAdd', label: '今日加群数量', icon: TrendingUpOutline, color: '#18a058', val: v.todayGroupAdd ?? 0 },
    { key: 'todayGroupDel', label: '今日退群数量', icon: TrendingDownOutline, color: '#e5484d', val: v.todayGroupDel ?? 0 },
    { key: 'todayFriendAdd', label: '今日加好友数量', icon: TrendingUpOutline, color: '#18a058', val: v.todayFriendAdd ?? 0 },
    { key: 'todayFriendDel', label: '今日删好友数量', icon: TrendingDownOutline, color: '#e5484d', val: v.todayFriendDel ?? 0 },
    { key: 'todayGroupMessages', label: '今日群聊消息数量', icon: ChatbubbleOutline, color: '#5b5bd6', val: v.todayGroupMessages ?? 0 },
    { key: 'todayC2cMessages', label: '今日单聊消息数量', icon: ChatbubbleOutline, color: '#2090e0', val: v.todayC2cMessages ?? 0 },
    { key: 'messagesTotal', label: '消息总数量', icon: ChatbubbleOutline, color: '#5b5bd6', val: v.messagesTotal ?? 0 },
    { key: 'eventsTotal', label: '系统事件总数量', icon: PulseOutline, color: '#e58e26', val: v.eventsTotal ?? 0 },
    { key: 'pluginsLoaded', label: '已加载插件', icon: CubeOutline, color: '#5b5bd6', val: v.pluginsLoaded ?? 0 },
    { key: 'cmdHandlers', label: '插件命令数', icon: ExtensionPuzzleOutline, color: '#5b5bd6', val: cmdTotal },
    { key: 'pluginTimeout', label: '插件超时次数', icon: HourglassOutline, color: pluginTimeout > 0 ? '#e5484d' : '#5b5bd6', val: pluginTimeout },
    { key: 'dedupTotal', label: '消息去重次数', icon: ShieldCheckmarkOutline, color: '#18a058', val: dedupTotal }
  ]
})

// ═══════════ 消息趋势（CommonChart） ═══════════
const RANGES = [
  { label: '近 7 天', value: 7 },
  { label: '近 15 天', value: 15 },
  { label: '近 30 天', value: 30 }
]

const trendOption = computed(() => {
  const rows = store.trend
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['单聊消息', '群聊消息', '总消息'], top: 0 },
    grid: { left: 52, right: 24, top: 44, bottom: 30 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((r) => dayjs(r.date).format('MM-DD'))
    },
    yAxis: { type: 'value' },
    series: [
      { name: '单聊消息', type: 'line', smooth: true, symbol: 'none', data: rows.map((r) => r.c2c), itemStyle: { color: '#5b5bd6' } },
      { name: '群聊消息', type: 'line', smooth: true, symbol: 'none', data: rows.map((r) => r.group), itemStyle: { color: '#2090e0' } },
      {
        name: '总消息', type: 'line', smooth: true, symbol: 'none', data: rows.map((r) => r.total),
        itemStyle: { color: '#18a058' }, lineStyle: { width: 3 },
        areaStyle: { opacity: 0.08 }
      }
    ]
  }
})

onMounted(async () => {
  await load()
  await store.loadTrend(7)
  loadVersionLog()
})
</script>

<template>
  <div>
    <!-- Hero -->
    <PageHero
      title="璇玑机器人控制台"
      subtitle="Xuanji Bot Framework · 实时数据总览"
      :icon="GridOutline"
    >
      <NTag :bordered="false" type="success" round>
        <template #icon><NIcon><PulseOutline /></NIcon></template>
        {{ (d.botsOnline ?? 0) + '/' + (d.botsTotal ?? 0) }} 在线
      </NTag>
      <NButton type="primary" :loading="loading" @click="load">刷新数据</NButton>
    </PageHero>

    <NEmpty v-if="err" :description="'加载失败：' + err" style="padding: 60px 0" />

    <!-- 统计卡 -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive class="grid">
      <NGi v-for="c in cards" :key="c.key" span="24 s:12 m:8 l:6 xl:4">
        <StatCard :icon="c.icon" :color="c.color" :value="c.val" :label="c.label" />
      </NGi>
    </NGrid>

    <!-- 消息趋势（近 7/15/30 天 · 单聊/群聊/总消息） -->
    <NCard class="trend-card" :bordered="true">
      <template #header>
        <div class="trend-title">
          <NIcon size="18" color="#5b5bd6"><BarChartOutline /></NIcon>
          <span>消息趋势</span>
          <NText depth="3" style="font-size: 12px; font-weight: 400">
            单聊 / 群聊 / 总消息 · 按天统计
          </NText>
        </div>
      </template>
      <template #header-extra>
        <NRadioGroup
          :value="store.days"
          size="small"
          @update:value="(v) => store.loadTrend(Number(v))"
        >
          <NRadioButton v-for="r in RANGES" :key="r.value" :value="r.value">
            {{ r.label }}
          </NRadioButton>
        </NRadioGroup>
      </template>
      <CommonChart :option="trendOption" height="300px" />
      <NEmpty
        v-if="store.err"
        :description="'趋势加载失败：' + store.err"
        style="padding: 40px 0"
      />
    </NCard>

    <!-- 框架版本日志（时间线） -->
    <NCard class="version-card" :bordered="true">
      <template #header>
        <div class="trend-title">
          <NIcon size="18" color="#854F0B"><TimeOutline /></NIcon>
          <span>框架版本日志</span>
          <NTag v-if="versionLog.current" :bordered="false" size="small" type="primary" round>
            当前 {{ versionLog.current }}
          </NTag>
        </div>
      </template>
      <NTimeline v-if="versionLog.versions.length" horizontal class="version-timeline">
        <NTimelineItem
          v-for="(v, i) in versionLog.versions"
          :key="v.version + i"
          :type="versionTagType[v.tag] ?? 'default'"
          :title="v.version"
          :time="v.date || '——'"
        >
          <div class="v-item">
            <NTag :bordered="false" size="tiny" :type="versionTagType[v.tag] ?? 'default'">{{ v.tag }}</NTag>
            <ul class="v-list">
              <li v-for="(it, j) in (v.items || [])" :key="j">{{ it }}</li>
            </ul>
          </div>
        </NTimelineItem>
      </NTimeline>
      <NEmpty v-else description="暂无版本日志" style="padding: 24px 0" />
    </NCard>
  </div>
</template>

<style scoped>
.grid {
  margin-top: 4px;
}
.trend-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(128, 128, 128, 0.05);
  height: 100%;
}
.trend-card {
  margin-top: 16px;
}
.trend-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
}
.version-card {
  margin-top: 16px;
}
.version-timeline {
  padding: 6px 2px 10px 2px;
  overflow-x: auto;
}
.v-item {
  margin-top: 6px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  white-space: nowrap;
}
.v-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--n-text-color-2);
}
.v-list li::marker {
  color: var(--n-text-color-3);
}
</style>
