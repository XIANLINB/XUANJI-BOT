<script setup lang="ts">
import { ref, computed, onMounted, h, type Component } from 'vue'
import {
  NGrid, NGi, NCard, NButton, NSpace, NIcon, NText,
  NProgress, NEmpty, NTag
} from 'naive-ui'
import {
  AppsOutline, PeopleOutline, PersonOutline, ChatbubbleOutline,
  TrendingUpOutline, TrendingDownOutline, CubeOutline, HardwareChipOutline,
  SpeedometerOutline, TimeOutline, PulseOutline, RocketOutline, GridOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'

const d = ref<Record<string, any>>({})
const err = ref('')
const loading = ref(false)

const sys = ref<{ heapUsedMb: number | null; heapMaxMb: number | null; uptimeMin: number | null }>({
  heapUsedMb: null,
  heapMaxMb: null,
  uptimeMin: null
})

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

async function loadSys() {
  const [heapUsed, heapMax, uptime] = await Promise.all([
    api.getActuatorMetric('jvm.memory.used', 'area:heap'),
    api.getActuatorMetric('jvm.memory.max', 'area:heap'),
    api.getActuatorMetric('process.uptime')
  ])
  sys.value = {
    heapUsedMb: heapUsed != null ? Math.round(heapUsed / 1048576) : null,
    heapMaxMb: heapMax != null ? Math.round(heapMax / 1048576) : null,
    uptimeMin: uptime != null ? Math.round(uptime / 60) : null
  }
}

onMounted(async () => {
  await load()
  loadSys()
})

const cards = computed(() => {
  const v = d.value
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
    { key: 'pluginsLoaded', label: '已加载插件', icon: CubeOutline, color: '#5b5bd6', val: v.pluginsLoaded ?? 0 }
  ]
})

const heapPct = computed(() => {
  const { heapUsedMb, heapMaxMb } = sys.value
  if (!heapUsedMb || !heapMaxMb) return 0
  return Math.min(100, Math.round((heapUsedMb / heapMaxMb) * 100))
})

function fmtUptime(m: number | null): string {
  if (m == null) return '—'
  const h = Math.floor(m / 60)
  const mm = m % 60
  return h > 0 ? `${h} 小时 ${mm} 分` : `${mm} 分`
}
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
        <NCard hoverable class="stat-card" :content-style="{ padding: '12px 14px' }">
          <div class="stat-top">
            <div class="stat-icon" :style="{ background: c.color + '1a', color: c.color }">
              <NIcon size="18"><component :is="c.icon" /></NIcon>
            </div>
            <div class="stat-value" :style="{ color: c.color }">{{ c.val }}</div>
          </div>
          <NText depth="3" class="stat-label">{{ c.label }}</NText>
        </NCard>
      </NGi>
    </NGrid>

    <!-- 系统信息 -->
    <NCard title="运行信息" class="sys-card">
      <template #header-extra>
        <NText depth="3" style="font-size: 12px">数据来自 /actuator/metrics</NText>
      </template>
      <NGrid :cols="3" :x-gap="20" responsive="screen" item-responsive>
        <NGi span="3 m:1">
          <div class="sys-item">
            <div class="sys-label"><NIcon size="15"><SpeedometerOutline /></NIcon> 堆内存使用</div>
            <NProgress
              type="line"
              :percentage="heapPct"
              :height="14"
              :border-radius="7"
              :color="heapPct > 85 ? '#e5484d' : '#5b5bd6'"
              indicator-placement="inside"
            />
            <NText depth="3" style="font-size: 12px">
              {{ sys.heapUsedMb ?? '—' }} / {{ sys.heapMaxMb ?? '—' }} MB
            </NText>
          </div>
        </NGi>
        <NGi span="3 m:1">
          <div class="sys-item">
            <div class="sys-label"><NIcon size="15"><TimeOutline /></NIcon> 已运行</div>
            <NStatistic :value="fmtUptime(sys.uptimeMin)" />
          </div>
        </NGi>
        <NGi span="3 m:1">
          <div class="sys-item">
            <div class="sys-label"><NIcon size="15"><HardwareChipOutline /></NIcon> 插件 / 机器人</div>
            <NStatistic :value="`${(d.pluginsLoaded ?? 0)} / ${(d.botsTotal ?? 0)}`" />
          </div>
        </NGi>
      </NGrid>
    </NCard>
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
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.stat-label {
  font-size: 12px;
}
.sys-card {
  margin-top: 16px;
}
.sys-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sys-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--n-text-color-2);
}
</style>
