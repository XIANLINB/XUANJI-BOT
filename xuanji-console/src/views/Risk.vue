<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NGrid, NGi, NStatistic, NButton, NIcon, NTag, NDataTable, NEmpty, NText, useMessage
} from 'naive-ui'
import { ShieldCheckmarkOutline, RefreshOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'
import dayjs from 'dayjs'
import { groupName, userName } from '../utils/names'

const message = useMessage()
const loading = ref(false)
const overview = ref<Record<string, any>>({ rateLimit: {}, dedup: {}, blacklist: {} })
const groups = ref<any[]>([])
const timeline = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    const [o, g, t] = await Promise.all([
      api.riskOverview(),
      api.riskGroups(),
      api.riskTimeline()
    ])
    overview.value = o || {}
    groups.value = g || []
    timeline.value = t?.rows ?? []
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

function fmtTime(t: number): string {
  return t > 0 ? dayjs(t * 1000).format('YYYY-MM-DD HH:mm:ss') : '—'
}

const STATUS_TAG: Record<string, { label: string; type: 'error' | 'warning' | 'success' }> = {
  risk: { label: '风险', type: 'error' },
  watch: { label: '关注', type: 'warning' },
  normal: { label: '正常', type: 'success' }
}

const groupCols = [
  {
    title: '群', key: 'gid', minWidth: 220,
    render: (r: any) => h('div', [
      h('div', { style: 'font-weight: 600' }, groupName(r)),
      h('div', { style: 'font-size: 11px; color: var(--n-text-color-3); font-family: ui-monospace, monospace' }, r.gid)
    ])
  },
  { title: '成员数', key: 'memberCnt', width: 90, render: (r: any) => h('span', { style: 'font-variant-numeric: tabular-nums' }, r.memberCnt || '—') },
  { title: '近7天消息', key: 'msgCnt', width: 110, render: (r: any) => h('span', { style: 'font-variant-numeric: tabular-nums' }, r.msgCnt) },
  { title: '黑名单', key: 'blackCnt', width: 90, render: (r: any) => r.blackCnt > 0 ? h(NTag, { size: 'small', type: 'error', bordered: false }, { default: () => r.blackCnt + ' 人' }) : h('span', { style: 'color: var(--n-text-color-3)' }, '0') },
  { title: '状态', key: 'status', width: 90, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: STATUS_TAG[r.status]?.type ?? 'default' }, { default: () => STATUS_TAG[r.status]?.label ?? r.status }) }
]

const timelineCols = [
  { title: '时间', key: 'createTime', width: 160, render: (r: any) => fmtTime(r.createTime) },
  { title: '动作', key: 'action', width: 80, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: r.action === 'ADD' ? 'error' : 'success' }, { default: () => r.action === 'ADD' ? '拉黑' : '解除' }) },
  { title: '群', key: 'groupId', minWidth: 180, render: (r: any) => h('span', groupName(r)) },
  { title: '用户', key: 'userId', minWidth: 180, render: (r: any) => h('span', userName(r)) },
  { title: '原因', key: 'reason', minWidth: 160, ellipsis: { tooltip: true }, render: (r: any) => h('span', r.reason || '—') }
]

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="风控中心" subtitle="限速 / 去重命中统计 · 各群风控状态 · 黑名单操作时间线" :icon="ShieldCheckmarkOutline">
      <NButton secondary :loading="loading" @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
      风控命中统计为<b>进程累计值</b>（重启归零）；各群风控状态按<b>近 7 天</b>消息量聚合；
      黑名单时间线记录每次<b>拉黑 / 解除</b>操作，来源与「权限管理」页共用同一份数据。
    </NAlert>

    <!-- ═══════ 概览统计卡 ═══════ -->
    <NCard title="命中概览（累计）" :bordered="true" style="margin-bottom: 14px">
      <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive>
        <NGi :span="6"><NStatistic label="命令限速命中" :value="overview.rateLimit?.commandHits ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">次</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="框架限流命中" :value="overview.rateLimit?.stageHits ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">次</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="去重命中(跨实例)" :value="overview.dedup?.dbHits ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">次</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="去重命中(本地降级)" :value="overview.dedup?.localHits ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">次</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="去重缓存行数" :value="overview.dedup?.rows ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">条</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="黑名单总数" :value="overview.blacklist?.total ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">人</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="近24h拉黑" :value="overview.blacklist?.add24h ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">人</NText></template></NStatistic></NGi>
        <NGi :span="6"><NStatistic label="近24h解除" :value="overview.blacklist?.remove24h ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">人</NText></template></NStatistic></NGi>
      </NGrid>
    </NCard>

    <!-- ═══════ 各群风控状态 ═══════ -->
    <NCard title="各群风控状态（近 7 天）" :bordered="true" style="margin-bottom: 14px">
      <template #header-extra>
        <NText depth="3" style="font-size: 12px">共 {{ groups.length }} 个活跃群 · 黑名单 ≥1 或消息 ≥200 条标记「风险」</NText>
      </template>
      <NDataTable :columns="groupCols" :data="groups" :bordered="false" size="small" :loading="loading" :row-key="(r: any) => r.gid" />
      <NEmpty v-if="!loading && !groups.length" description="暂无群消息数据（有群聊消息后自动出现）" style="padding: 30px 0" />
    </NCard>

    <!-- ═══════ 黑名单时间线 ═══════ -->
    <NCard title="黑名单操作时间线" :bordered="true">
      <template #header-extra>
        <NText depth="3" style="font-size: 12px">最近 {{ timeline.length }} 条 · 拉黑/解除均留痕</NText>
      </template>
      <NDataTable :columns="timelineCols" :data="timeline" :bordered="false" size="small" :loading="loading" :row-key="(r: any) => r.id" />
      <NEmpty v-if="!loading && !timeline.length" description="暂无黑名单操作记录" style="padding: 30px 0" />
    </NCard>
  </div>
</template>
