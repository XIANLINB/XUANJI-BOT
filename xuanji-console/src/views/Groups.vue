<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import {
  NDataTable, NButton, NDrawer, NDrawerContent, NAlert, NSpace, NIcon, NText,
  NEmpty, NSelect, NInput, NSwitch, NTag, NCard, NGrid, NGi, NNumberAnimation,
  NGradientText, NPagination, NTooltip, type DataTableColumns
} from 'naive-ui'
import {
  PeopleOutline, SearchOutline, AddCircleOutline, RefreshOutline, InformationCircleOutline,
  TrendingUpOutline, PersonOutline, CubeOutline
} from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import PageHero from '../components/PageHero.vue'
import StatCard from '../components/StatCard.vue'
import { groupName, userName } from '../utils/names'

const groups = ref<any[]>([])
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const search = ref('')
const showDeleted = ref(false)
const bots = ref<{ appId: string; name: string }[]>([])

const botNameMap = computed(
  () => new Map((bots.value || []).map((b) => [String(b.appId), b.name || `Bot #${b.appId}`]))
)

async function loadBots() { try { bots.value = (await api.getBots()) || [] } catch { bots.value = [] } }
async function load() {
  loading.value = true
  try { groups.value = await api.getGroups(); err.value = '' }
  catch (e: any) { err.value = e.message }
  finally { loading.value = false }
}

const variation = ref<Record<string, number>>({})
async function loadVariation() {
  if (!botFilter.value) { variation.value = {}; return }
  try { variation.value = (await api.getBotGroupVariation(botFilter.value)) || {} } catch { variation.value = {} }
}
watch(botFilter, () => { load(); loadVariation() })
onMounted(async () => {
  await loadBots()
  await load()
  if (botFilter.value) loadVariation()
})

const filtered = computed(() => {
  let rows = groups.value || []
  if (!showDeleted.value) rows = rows.filter((g) => Number(g.IS_DELETED) !== 1)
  if (botFilter.value) rows = rows.filter((g) => String(g.BOT_APPID) === String(botFilter.value))
  const q = search.value.trim().toLowerCase()
  if (q) {
    rows = rows.filter(
      (g) =>
        String(g.GROUP_ID || '').toLowerCase().includes(q) ||
        String(g.GROUP_NAME || '').toLowerCase().includes(q)
    )
  }
  return rows
})

// ---------- 统计卡（统一 widget 风格） ----------
const notDeletedCount = computed(() =>
  filtered.value.filter((g) => Number(g.IS_DELETED) !== 1).length
)
const deletedCount = computed(() =>
  filtered.value.filter((g) => Number(g.IS_DELETED) === 1).length
)
const memberSum = computed(() =>
  filtered.value
    .filter((g) => Number(g.IS_DELETED) !== 1)
    .reduce((s, g) => s + (Number(g.MEMBER_COUNT) || 0), 0)
)
const memberHasData = computed(() => filtered.value.some((g) => Number(g.MEMBER_COUNT) > 0))

const todayNew = computed(() => Number(variation.value.todayNewGroups) || 0)
const ydayNew = computed(() => Number(variation.value.ydayNewGroups) || 0)
const todayActive = computed(() => Number(variation.value.todayActiveMembers) || 0)
const ydayActive = computed(() => Number(variation.value.ydayActiveMembers) || 0)
const deltaNew = computed(() => todayNew.value - ydayNew.value)
const deltaActive = computed(() => todayActive.value - ydayActive.value)

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n) || n <= 0) return String(v)
  return dayjs(n <= 9999999999 ? n * 1000 : n).format('YYYY-MM-DD HH:mm')
}

const columns = computed<DataTableColumns>(() => [
  {
    title: '机器人名称', key: 'BOT_NAME', width: 130,
    render: (row) => h('span', { style: 'font-weight: 600' }, botNameMap.value.get(String(row.BOT_APPID)) || `Bot #${row.BOT_APPID}`)
  },
  { title: '群号', key: 'GROUP_ID', width: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.GROUP_ID || '—')) },
  { title: '群名称', key: 'GROUP_NAME', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', groupName(row)) },
  { title: '群主 ID', key: 'OWNER_ID', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', String(row.OWNER_ID || '—')) },
  {
    title: '成员数', key: 'MEMBER_COUNT', width: 80,
    render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #722ed1; font-weight: 600' }, String(row.MEMBER_COUNT ?? '—'))
  },
  { title: '加入时间', key: 'JOIN_TIME', width: 150, ellipsis: { tooltip: true }, render: (row) => h('span', fmtTime(row.JOIN_TIME)) },
  {
    title: '状态', key: 'STATUS', width: 80,
    render: (row) => {
      if (Number(row.IS_DELETED) === 1) return h(NTag, { size: 'small', type: 'default', bordered: false }, () => '已删除')
      const raw = String(row.STATUS || '').toLowerCase()
      if (raw === 'active') return h(NTag, { size: 'small', type: 'success', bordered: false }, () => '正常')
      if (raw === 'removed') return h(NTag, { size: 'small', type: 'error', bordered: false }, () => '已退出')
      return h(NTag, { size: 'small', type: 'warning', bordered: false }, () => row.STATUS || '—')
    }
  }
])

// ---------- 分页 ----------
const page = ref(1)
const pageSize = ref(20)
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})
watch(filtered, () => { page.value = 1 })

const members = ref<any[]>([])
const showMembers = ref(false)
const memberGroup = ref('')
async function openMembers(g: any) {
  memberGroup.value = g.GROUP_ID
  try {
    const all: any[] = await api.getGroupMembers(g.GROUP_ID)
    members.value = all.filter((m) => String(m.BOT_APPID) === String(g.BOT_APPID))
  } catch { members.value = [] }
  showMembers.value = true
}

const memberColumns: DataTableColumns = [
  { title: '成员 ID', key: 'MEMBER_ID', width: 180, ellipsis: { tooltip: true } },
  { title: '昵称', key: 'NICKNAME', width: 140, render: (row) => h('span', userName(row)) },
  { title: '角色', key: 'ROLE', width: 90, render: (row) => h(NTag, { size: 'small', type: row.ROLE === 'owner' ? 'warning' : 'default', bordered: false }, () => row.ROLE || 'member') },
  { title: '入群时间', key: 'JOIN_TIME', width: 150, ellipsis: { tooltip: true }, render: (row) => h('span', fmtTime(row.JOIN_TIME)) }
]
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NGradientText :gradient="{ deg: 90, from: '#2090e0', to: '#07c160' }" :size="18" style="font-weight: 700">
          群聊列表
        </NGradientText>
        <NText depth="3" style="font-size: 13px">共 {{ filtered.length }} 个群</NText>
      </div>
      <NSpace align="center" :wrap="false">
        <NInput v-model:value="search" :placeholder="'搜索群号 / 群名称'" clearable style="width: 200px">
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
        <NSelect
          v-model:value="botFilter"
          :options="bots.map((b) => ({ label: b.name || `Bot #${b.appId}`, value: String(b.appId) }))"
          placeholder="按机器人过滤" clearable style="width: 160px"
        />
        <NSpace align="center" :size="6">
          <NSwitch v-model:value="showDeleted" size="small" />
          <span style="font-size: 13px">显示已删除</span>
        </NSpace>
        <NButton type="primary" :loading="loading" @click="load" size="small">
          <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <!-- 统计卡：统一 widget 风格（等高、紧凑） -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
      <NGi span="24 s:12 m:6">
        <StatCard icon="PeopleOutline" color="#2090e0" :value="filtered.length" label="群聊总数"
          :sub="`正常 ${notDeletedCount} · 已退出 ${deletedCount}`" />
      </NGi>
      <NGi span="24 s:12 m:6">
        <StatCard icon="TrendingUpOutline" color="#07c160" :value="todayNew" label="今日新加群"
          :sub="`较昨日 ${deltaNew > 0 ? '+' + deltaNew : deltaNew}`" />
      </NGi>
      <NGi span="24 s:12 m:6">
        <StatCard icon="PersonOutline" color="#722ed1" :value="todayActive" label="今日活跃成员"
          :sub="`较昨日 ${deltaActive > 0 ? '+' + deltaActive : deltaActive}`" />
      </NGi>
      <NGi span="24 s:12 m:6">
        <StatCard icon="CubeOutline" color="#fa8c16" :value="memberHasData ? memberSum : '—'" label="群成员总数"
          :sub="memberHasData ? `未删除群累加 ${notDeletedCount} 个群` : '需 QQ 平台群 OPEN 事件自动同步'" :animate="memberHasData" />
      </NGi>
    </NGrid>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <NDataTable
      :columns="columns"
      :data="pagedRows"
      :pagination="false"
      :loading="loading"
      :bordered="false"
      size="small"
      striped
      :row-props="(row) => ({ style: 'cursor: pointer', onClick: () => openMembers(row) })"
      style="margin-bottom: 12px"
    />
    <NSpace justify="end" align="center" style="margin-bottom: 16px">
      <NPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :item-count="filtered.length"
        :page-sizes="[20, 50, 100, 200]"
        show-size-picker
        size="small"
      />
    </NSpace>

    <NEmpty
      v-if="!loading && !filtered.length"
      :description="(groups.value && groups.value.length) ? '没有匹配结果，换个关键词试试' : '还没有群数据，收发群消息后会自动写入'"
      style="padding: 40px 0"
    >
      <template #icon><NIcon size="48"><AddCircleOutline /></NIcon></template>
    </NEmpty>

    <NDrawer v-model:show="showMembers" :width="760" placement="right">
      <NDrawerContent :title="`群成员 · ${memberGroup}`" closable>
        <NDataTable
          :columns="memberColumns"
          :data="members"
          :pagination="{ pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100] }"
          size="small"
        />
        <NEmpty v-if="!members.length" :description="'暂无成员'" style="padding: 40px 0" />
      </NDrawerContent>
    </NDrawer>
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
.page-title { display: flex; align-items: center; gap: 10px; }

/* 统一 widget 风格（NCard 包裹 StatCard 4 卡） */
.grid { margin-top: 4px; }
</style>
