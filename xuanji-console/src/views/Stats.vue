<script setup lang="ts">
import { ref, computed, onMounted, watch, h } from 'vue'
import {
  NCard, NGrid, NGi, NStatistic, NRadioGroup, NRadioButton, NEmpty, NSpin, NText,
  NTooltip, NTag, NIcon, NSelect, NButton, NNumberAnimation, useMessage
} from 'naive-ui'
import {
  StatsChartOutline, ChatbubbleOutline, PeopleOutline, PersonOutline,
  ServerOutline, ArrowDownOutline, ArrowUpOutline, HelpCircleOutline,
  CalendarOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'
import api from '../api'
import { useBotsStore } from '../stores/bots'
import { groupName, userName } from '../utils/names'

const message = useMessage()
const botsStore = useBotsStore()
const loading = ref(false)
const err = ref('')
const days = ref(30)
const botKey = ref('')  // 空 = 全部机器人
const RANGES = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 }
]

/** 机器人筛选选项：默认"全部机器人"+ 来自 BotStore 的机器人列表 */
const botOptions = computed(() => {
  const opts: { label: string; value: string }[] = [{ label: '全部机器人', value: '' }]
  for (const b of botsStore.bots) {
    opts.push({
      label: b.name ? `${b.name} (${b.appId})` : `Bot #${b.appId}`,
      value: String(b.appId)
    })
  }
  return opts
})

const stats = ref<Record<string, any>>({
  heatmap: [], typeDist: [], activeGroups: [], activeUsers: [],
  activeBots: [], directionDist: [], eventTypeDist: []
})

const TYPE_LABEL: Record<string, string> = {
  text: '文本', markdown: 'Markdown', image: '图片', voice: '语音', video: '视频',
  file: '文件', ark: 'Ark', rich_media: '富媒体', card: '卡片', unknown: '未知',
  IN: '入站', OUT: '出站'
}
const TYPE_COLOR: Record<string, string> = {
  text: '#5b8def', markdown: '#7f77dd', image: '#07c160', voice: '#f0a020',
  video: '#e5484d', file: '#9b59b6', ark: '#1d9e75', rich_media: '#d85a30',
  card: '#888780', unknown: '#b4b2a9',
  IN: '#1E88E5', OUT: '#07c160'
}

/** 系统事件类型配色（独立表，避免被 TYPE_COLOR 兜底为灰黑色）。 */
const EVENT_TYPE_COLOR: Record<string, string> = {
  // 群聊事件
  GROUP_MESSAGE_CREATE: '#5b8def',
  GROUP_MESSAGE_RECALL: '#f0a020',
  GROUP_MEMBER_ADD: '#07c160',
  GROUP_MEMBER_DECREASE: '#e5484d',
  GROUP_MEMBER_INCREASE: '#07c160',
  GROUP_JOIN: '#1E88E5',
  GROUP_QUIT: '#9aa0a6',
  GROUP_ADD_ROBOT: '#1E88E5',
  GROUP_DEL_ROBOT: '#e5484d',
  GROUP_JOIN_REQUEST: '#722ed1',
  GROUP_JOIN_REQUEST_APPROVED: '#07c160',
  GROUP_JOIN_REQUEST_REJECTED: '#e5484d',
  GROUP_BOT_REMOVED_FROM_GROUP: '#e5484d',
  GROUP_NAME_UPDATE: '#13c2c2',
  GROUP_MSG_RECEIVE_SWITCH: '#888780',
  // 单聊/C2C 事件
  C2C_MESSAGE_CREATE: '#5b8def',
  C2C_MESSAGE_RECALL: '#f0a020',
  C2C_FRIEND_ADD: '#07c160',
  C2C_FRIEND_DEL: '#e5484d',
  // 关系链
  FRIEND_ADD: '#07c160',
  FRIEND_DEL: '#e5484d',
  // 互动事件
  MESSAGE_REACTION: '#722ed1',
  AT_BOT_MESSAGE_CREATE: '#1E88E5',
  INTERACTION_CREATE: '#13c2c2',
  // 默认兜底
  unknown: '#888780'
}

// ================== 图表 option ==================

/** 热力图：周 × 小时 */
const heatmapOption = computed(() => {
  const rows: number[][] = stats.value.heatmap || []
  const data = rows.map((r: number[]) => [r[1], r[0], r[2]])
  const max = Math.max(1, ...data.map((d: number[]) => d[2]))
  return {
    tooltip: {
      position: 'top',
      formatter: (p: any) => {
        const dow = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][p.value[1]]
        return `${dow} ${String(p.value[0]).padStart(2, '0')}:00-${String(p.value[0]).padStart(2, '0')}:59<br/><b>${p.value[2]}</b> 条消息`
      }
    },
    grid: { left: 70, right: 20, top: 10, bottom: 60 },
    xAxis: { type: 'category', data: Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}`), splitArea: { show: true }, name: '小时（0-23）', nameLocation: 'middle', nameGap: 30, nameTextStyle: { fontSize: 11 } },
    yAxis: { type: 'category', data: ['周六', '周五', '周四', '周三', '周二', '周一', '周日'], splitArea: { show: true }, name: '星期', nameTextStyle: { fontSize: 11 } },
    visualMap: { min: 0, max, calculable: true, orient: 'horizontal', left: 'center', bottom: 5, inRange: { color: ['#e8f7ee', '#07c160'] } },
    series: [{ type: 'heatmap', data, label: { show: false }, emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.3)' } } }]
  }
})

/** 消息类型环形图 */
const typeOption = computed(() => {
  const dist = stats.value.typeDist || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      data: dist.map((d: any) => ({
        name: TYPE_LABEL[d.name] ?? d.name,
        value: d.value,
        itemStyle: { color: TYPE_COLOR[d.name] ?? '#888780' }
      }))
    }]
  }
})

/** 通用 TOP 横向条形图 */
function topOption(title: string, data: any[], color: string, nameFn: (r: any) => string, showId = false) {
  const rows = [...(data || [])].reverse()
  return {
    title: { text: title, left: 8, textStyle: { fontSize: 13, fontWeight: 500 } },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const i = params[0].dataIndex
        const row = data[i]
        return `${nameFn(row)}<br/>消息数：<b>${row.value}</b>`
      }
    },
    grid: { left: 8, right: 50, top: 34, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: rows.map((r: any) => {
        const label = nameFn(r)
        return label.length > 14 ? label.slice(0, 12) + '…' : label
      }),
      inverse: false,
      axisLabel: { fontSize: 11 }
    },
    series: [{
      type: 'bar', data: rows.map((r: any) => r.value),
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      barMaxWidth: 16,
      label: { show: true, position: 'right', fontSize: 10, color: '#6b7280' }
    }]
  }
}

const groupTopOption = computed(() =>
  topOption('活跃群 TOP10', stats.value.activeGroups || [], '#2090e0', groupName))
const userTopOption = computed(() =>
  topOption('活跃用户 TOP10', stats.value.activeUsers || [], '#5b5bd6', userName))
const botTopOption = computed(() =>
  topOption('活跃机器人 TOP10', stats.value.activeBots || [], '#fa8c16', (r: any) => {
    // 后端已用 ref.botName() 直接填充 name，id 是 appId（=instanceId）
    if (r.name && r.name.trim()) return r.name
    // 前端兜底：用 BotStore.nameMap 查
    const nameMap = botsStore.nameMap
    if (nameMap.has(String(r.id))) return nameMap.get(String(r.id)) as string
    return `Bot #${r.id}`
  }))

/** 消息方向分布：IN 入站 vs OUT 出站 */
const directionOption = computed(() => {
  const dist = stats.value.directionDist || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['55%', '75%'], center: ['50%', '42%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      data: dist.map((d: any) => ({
        name: TYPE_LABEL[d.name] ?? d.name,
        value: d.value,
        itemStyle: { color: TYPE_COLOR[d.name] ?? '#888780' }
      }))
    }]
  }
})

/** 事件类型分布：系统事件专用配色表 */
const eventTypeOption = computed(() => {
  const dist = stats.value.eventTypeDist || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', fontSize: 10 },
      data: dist.map((d: any) => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: EVENT_TYPE_COLOR[d.name] ?? hashColor(d.name) }
      }))
    }]
  }
})

/** 给未知事件类型一个稳定的随机色，避免全是灰黑。 */
function hashColor(key: string) {
  const palette = ['#5b8def', '#07c160', '#fa8c16', '#722ed1', '#13c2c2', '#eb2f96', '#f5222d', '#a0d911']
  let hash = 0
  for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) >>> 0
  return palette[hash % palette.length]
}

// ================== 派生 ==================
const totalMsg = computed(() => (stats.value.heatmap || []).reduce((s: number, r: number[]) => s + r[2], 0))
const inMsg = computed(() => (stats.value.directionDist || []).filter((d: any) => String(d.name).toUpperCase() === 'IN').reduce((s: number, d: any) => s + d.value, 0))
const outMsg = computed(() => (stats.value.directionDist || []).filter((d: any) => String(d.name).toUpperCase() === 'OUT').reduce((s: number, d: any) => s + d.value, 0))

// 热力图高峰日 + 高峰时段（从 heatmap 中提取）
const peakInfo = computed(() => {
  const rows = stats.value.heatmap || []
  if (!rows.length) return null
  let max = 0, peakRow: any = null
  for (const r of rows) if (r[2] > max) { max = r[2]; peakRow = r }
  if (!peakRow) return null
  const dow = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][peakRow[0]]
  return { dow, hr: peakRow[1], cnt: peakRow[2] }
})

// ================== 数据加载 ==================
async function load() {
  loading.value = true
  err.value = ''
  try {
    await botsStore.loadBots()
    stats.value = await api.getStats(days.value, botKey.value)
  } catch (e: any) {
    err.value = e?.message ?? e
    message.error('统计加载失败：' + err.value)
  } finally {
    loading.value = false
  }
}

watch(days, load)
watch(botKey, load)
onMounted(load)
</script>

<template>
  <div>
    <PageHero title="聚合统计" subtitle="消息热力图 · 类型分布 · 活跃 TOP · 方向与事件" :icon="StatsChartOutline">
      <NSelect
        v-model:value="botKey"
        :options="botOptions"
        size="small"
        placeholder="选择机器人"
        style="width: 180px"
      />
      <NRadioGroup v-model:value="days" size="small">
        <NRadioButton v-for="r in RANGES" :key="r.value" :value="r.value">{{ r.label }}</NRadioButton>
      </NRadioGroup>
      <NButton secondary :loading="loading" @click="load">刷新</NButton>
    </PageHero>

    <NEmpty v-if="err" :description="'加载失败：' + err" style="padding: 60px 0" />

    <template v-else>
      <!-- ════════════ 4 张统计卡片（统一高度） ════════════ -->
      <NGrid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
        <NGi span="4 m:1">
          <NCard class="stat-card" hoverable>
            <div class="stat-row">
              <NIcon size="22" color="#5b5bd6"><ChatbubbleOutline /></NIcon>
              <div class="stat-meta">
                <div class="stat-label">统计周期内消息</div>
                <NStatistic style="--n-value-font-size: 22px">
                  <NNumberAnimation :from="0" :to="totalMsg" :duration="900" />
                </NStatistic>
              </div>
            </div>
          </NCard>
        </NGi>
        <NGi span="4 m:1">
          <NCard class="stat-card" hoverable>
            <div class="stat-row">
              <NIcon size="22" color="#1E88E5"><ArrowDownOutline /></NIcon>
              <div class="stat-meta">
                <div class="stat-label">入站消息</div>
                <NStatistic style="--n-value-font-size: 22px">
                  <NNumberAnimation :from="0" :to="inMsg" :duration="900" />
                </NStatistic>
              </div>
            </div>
            <div class="stat-sub">机器人收到</div>
          </NCard>
        </NGi>
        <NGi span="4 m:1">
          <NCard class="stat-card" hoverable>
            <div class="stat-row">
              <NIcon size="22" color="#07c160"><ArrowUpOutline /></NIcon>
              <div class="stat-meta">
                <div class="stat-label">出站消息</div>
                <NStatistic style="--n-value-font-size: 22px">
                  <NNumberAnimation :from="0" :to="outMsg" :duration="900" />
                </NStatistic>
              </div>
            </div>
            <div class="stat-sub">机器人发出</div>
          </NCard>
        </NGi>
        <NGi span="4 m:1">
          <NCard class="stat-card" hoverable>
            <div class="stat-row">
              <NIcon size="22" color="#fa8c16"><ServerOutline /></NIcon>
              <div class="stat-meta">
                <div class="stat-label">活跃机器人数</div>
                <NStatistic style="--n-value-font-size: 22px">
                  <NNumberAnimation :from="0" :to="(stats.activeBots || []).length" :duration="900" />
                </NStatistic>
              </div>
            </div>
            <div class="stat-sub">周期内有消息的机器人</div>
          </NCard>
        </NGi>
      </NGrid>

      <NSpin :show="loading">
        <!-- ════════════ 热力图（含详细说明） ════════════ -->
        <NCard :bordered="true" style="margin-bottom: 14px">
          <template #header>
            <span style="font-weight: 600">消息活跃热力图（周 × 小时）</span>
          </template>
          <template #header-extra>
            <NSpace align="center" :size="6">
              <NTooltip v-if="peakInfo" :delay="300">
                <template #trigger>
                  <NTag :bordered="false" type="success" size="small">
                    <template #icon><NIcon size="12"><CalendarOutline /></NIcon></template>
                    高峰 {{ peakInfo.dow }} {{ peakInfo.hr }}时 · {{ peakInfo.cnt }} 条
                  </NTag>
                </template>
                周期内消息量最大的时段，用于判断何时是用户/机器人最忙的时候
              </NTooltip>
              <NTooltip :delay="300">
                <template #trigger>
                  <NIcon size="16" color="#9aa0a6"><HelpCircleOutline /></NIcon>
                </template>
                <div style="max-width: 260px; line-height: 1.6">
                  横轴：一天 24 小时（00-23）<br/>
                  纵轴：周一到周日（周六在上、周日在下）<br/>
                  颜色越深代表该时段消息越多<br/>
                  鼠标悬浮查看具体数量
                </div>
              </NTooltip>
            </NSpace>
          </template>
          <CommonChart :option="heatmapOption" height="320px" />
        </NCard>

        <!-- ════════════ 第一行图表：类型分布 + 方向 + 事件类型 ════════════ -->
        <NGrid :cols="3" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
          <NGi span="3 m:1">
            <NCard :bordered="true">
              <template #header><span style="font-weight: 600">消息类型分布</span></template>
              <template #header-extra>
                <NText depth="3" style="font-size: 11.5px">周期内消息按类型占比</NText>
              </template>
              <CommonChart :option="typeOption" height="280px" />
            </NCard>
          </NGi>
          <NGi span="3 m:1">
            <NCard :bordered="true">
              <template #header><span style="font-weight: 600">消息方向分布</span></template>
              <template #header-extra>
                <NText depth="3" style="font-size: 11.5px">入站（用户→机器人）vs 出站（机器人→用户）</NText>
              </template>
              <CommonChart :option="directionOption" height="280px" />
            </NCard>
          </NGi>
          <NGi span="3 m:1">
            <NCard :bordered="true">
              <template #header><span style="font-weight: 600">系统事件类型分布</span></template>
              <template #header-extra>
                <NText depth="3" style="font-size: 11.5px">加群/退群/加好友/撤回 等系统事件</NText>
              </template>
              <CommonChart :option="eventTypeOption" height="280px" />
            </NCard>
          </NGi>
        </NGrid>

        <!-- ════════════ 第二行图表：活跃群 / 活跃用户 / 活跃机器人 ════════════ -->
        <NGrid :cols="3" :x-gap="12" :y-gap="12" responsive="screen" item-responsive>
          <NGi span="3 m:1">
            <NCard :bordered="true">
              <CommonChart :option="groupTopOption" height="300px" />
              <NText depth="3" style="font-size: 11.5px; display: block; margin-top: 6px">名称缺失自动用群 ID 前 8 位兜底</NText>
            </NCard>
          </NGi>
          <NGi span="3 m:1">
            <NCard :bordered="true">
              <CommonChart :option="userTopOption" height="300px" />
              <NText depth="3" style="font-size: 11.5px; display: block; margin-top: 6px">名称缺失自动用用户 ID 前 8 位兜底</NText>
            </NCard>
          </NGi>
          <NGi span="3 m:1">
            <NCard :bordered="true">
              <CommonChart :option="botTopOption" height="300px" />
              <NText depth="3" style="font-size: 11.5px; display: block; margin-top: 6px">按机器人聚合周期内消息数</NText>
            </NCard>
          </NGi>
        </NGrid>
      </NSpin>
    </template>
  </div>
</template>

<style scoped>
/* 统一卡片高度 */
.stat-card {
  height: 100%;
  min-height: 96px;
}
.stat-card :deep(.n-statistic .n-statistic__label) {
  font-size: 12.5px !important;
  color: #6b7280 !important;
  margin-bottom: 2px !important;
}
.stat-card :deep(.n-statistic .n-statistic-value) {
  font-weight: 600 !important;
}
.stat-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
}
.stat-meta {
  flex: 1;
  min-width: 0;
}
.stat-label {
  font-size: 12.5px;
  color: #6b7280;
  margin-bottom: 2px;
}
.stat-sub {
  font-size: 11px;
  color: #9aa0a6;
  margin-top: 2px;
}
</style>