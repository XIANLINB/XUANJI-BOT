<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import {
  NDataTable, NButton, NDrawer, NDrawerContent, NAlert, NSpace, NIcon, NText,
  NEmpty, NSelect, NInput, NSwitch, NTag, NCard, NGrid, NGi, NNumberAnimation,
  NGradientText, NPagination, NTooltip, useMessage, type DataTableColumns
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
import { useBotsStore } from '../stores/bots'

const message = useMessage()

const groups = ref<any[]>([])
const total = ref(0)
const summary = ref<{ notDeleted: number; deleted: number; memberSum: number }>({ notDeleted: 0, deleted: 0, memberSum: 0 })
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const search = ref('')
const showDeleted = ref(false)
const botsStore = useBotsStore()

const botNameMap = computed(
  () => new Map((botsStore.bots || []).map((b) => [String(b.appId), b.name || `Bot #${b.appId}`]))
)

// Q11：后端分页 + 服务端过滤（bot / 关键词 / 是否含已删除）
async function load() {
  loading.value = true
  try {
    const res = await api.getGroupsPage({
      page: page.value,
      size: pageSize.value,
      bot: botFilter.value || undefined,
      q: search.value.trim() || undefined,
      showDeleted: showDeleted.value
    })
    groups.value = res?.rows || []
    total.value = Number(res?.total ?? 0)
    summary.value = {
      notDeleted: Number(res?.notDeleted ?? 0),
      deleted: Number(res?.deleted ?? 0),
      memberSum: Number(res?.memberSum ?? 0)
    }
    err.value = ''
  } catch (e: any) {
    err.value = e.message
    groups.value = []
  } finally {
    loading.value = false
  }
}

const variation = ref<Record<string, number>>({})
async function loadVariation() {
  // Q1：不选机器人 → 后端聚合全量变动
  if (!botFilter.value) {
    try { variation.value = (await api.getGroupsVariationAll()) || {} } catch { variation.value = {} }
    return
  }
  // Q9：appId → botKey 映射后再请求（后端 resolveAppId 也兜底接受 appId）
  const bot = botsStore.bots.find((x) => String(x.appId) === String(botFilter.value))
  const key = bot?.botKey || botFilter.value
  try { variation.value = (await api.getBotGroupVariation(key)) || {} } catch { variation.value = {} }
}

let searchTimer: number | null = null
watch(botFilter, () => { page.value = 1; load(); loadVariation() })
watch(showDeleted, () => { page.value = 1; load() })
watch(search, () => {
  if (searchTimer !== null) clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { page.value = 1; load() }, 300)
})

onMounted(async () => {
  await botsStore.loadBots()
  await load()
  loadVariation()
})

// ---------- 统计卡（统一 widget 风格） ----------
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
  // Q3：统一 UTC+8 口径（与 Monitor/Stats 一致）
  return dayjs(n <= 9999999999 ? n * 1000 : n).utcOffset(8).format('YYYY-MM-DD HH:mm')
}

/** 群标签（存 JSON 数组字符串，如 ["a","b"]）→ 逗号文本展示。 */
function fmtTags(v: unknown): string {
  if (v == null || v === '') return '—'
  const s = String(v).trim()
  if (!s.startsWith('[')) return s
  try {
    const arr = JSON.parse(s)
    if (Array.isArray(arr) && arr.length) return arr.join('、')
    return '—'
  } catch { return s }
}

const columns = computed<DataTableColumns>(() => [
  {
    title: '机器人名称', key: 'BOT_NAME', width: 130,
    render: (row) => h('span', { style: 'font-weight: 600' }, botNameMap.value.get(String(row.BOT_APPID)) || `Bot #${row.BOT_APPID}`)
  },
  { title: '群号', key: 'GROUP_ID', width: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.GROUP_ID || '—')) },
  { title: '群名称', key: 'GROUP_NAME', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', groupName(row)) },
  { title: '群备注', key: 'GROUP_FINGER_MEMO', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', String(row.GROUP_FINGER_MEMO || '—')) },
  { title: '群分类', key: 'GROUP_CLASS_TEXT', width: 90, render: (row) => h('span', String(row.GROUP_CLASS_TEXT || '—')) },
  { title: '群标签', key: 'GROUP_TAGS', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', fmtTags(row.GROUP_TAGS)) },
  { title: '群主 ID', key: 'OWNER_ID', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', String(row.OWNER_ID || '—')) },
  {
    title: '成员数', key: 'MEMBER_COUNT', width: 110,
    render: (row) => {
      // 展示 已有成员数/真实成员数（真实数为空时只显示已有数）
      const real = row.MEMBER_COUNT
      const saved = row.MEMBER_COUNT_SAVED
      const text = (real != null && real !== '') ? `${saved ?? '—'}/${real}` : (saved ?? '—')
      return h('span', { style: 'font-variant-numeric: tabular-nums; color: #722ed1; font-weight: 600' }, String(text))
    }
  },
  { title: '群最大人数', key: 'MEMBER_MAX', width: 100, render: (row) => h('span', String(row.MEMBER_MAX ?? '—')) },
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
  },
  {
    // Q10：「详情」进机器人状态，「成员」进群成员，行点击不再进任何
    title: '操作', key: 'ACTION', width: 130, fixed: 'right',
    render: (row) => h(NSpace, { size: 4 }, {
      default: () => [
        h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: (e: MouseEvent) => { e.stopPropagation(); openRobotStates(row) } }, () => '详情'),
        h(NButton, { size: 'small', type: 'default', secondary: true, onClick: (e: MouseEvent) => { e.stopPropagation(); openMembers(row) } }, () => '成员')
      ]
    })
  }
])

// ---------- 分页 ----------
const page = ref(1)
const pageSize = ref(20)
function onPageChange(p: number) { page.value = p; load() }
function onPageSizeChange(s: number) { pageSize.value = s; page.value = 1; load() }

const members = ref<any[]>([])
const showMembers = ref(false)
const memberGroup = ref('')
async function openMembers(g: any) {
  memberGroup.value = g.GROUP_ID
  try {
    // Q6：后端按 bot 过滤，前端不再全量拉再过滤
    const all: any[] = await api.getGroupMembers(g.GROUP_ID, String(g.BOT_APPID))
    members.value = all || []
  } catch {
    members.value = []
    message.error('加载群成员失败')
  }
  showMembers.value = true
}

// ---------- 机器人在群内状态 ----------
const robotStates = ref<any[]>([])
const showRobotStates = ref(false)
const robotStateGroup = ref('')
async function openRobotStates(g: any) {
  robotStateGroup.value = String(g.GROUP_ID || '')
  try {
    robotStates.value = (await api.getGroupRobotStates(String(g.GROUP_ID || ''))) || []
  } catch {
    robotStates.value = []
    message.error('加载机器人在群内状态失败')
  }
  showRobotStates.value = true
}

function robotRoleTag(role: unknown) {
  const r = String(role || 'member').toLowerCase()
  if (r === 'owner') return h(NTag, { size: 'small', type: 'warning', bordered: false }, () => '群主')
  if (r === 'admin') return h(NTag, { size: 'small', type: 'primary', bordered: false }, () => '管理员')
  return h(NTag, { size: 'small', type: 'default', bordered: false }, () => '成员')
}

const memberColumns: DataTableColumns = [
  { title: '成员 ID', key: 'MEMBER_ID', width: 180, ellipsis: { tooltip: true } },
  { title: '昵称', key: 'NICKNAME', width: 140, render: (row) => h('span', userName(row)) },
  { title: '角色', key: 'ROLE', width: 90, render: (row) => robotRoleTag(row.ROLE) },
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
        <NText depth="3" style="font-size: 13px">共 {{ total }} 个群</NText>
      </div>
      <NSpace align="center" :wrap="false">
        <NInput v-model:value="search" :placeholder="'搜索群号 / 群名称'" clearable style="width: 200px">
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
        <NSelect
          v-model:value="botFilter"
          :options="botsStore.bots.map((b) => ({ label: b.name || `Bot #${b.appId}`, value: String(b.appId) }))"
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
        <StatCard icon="PeopleOutline" color="#2090e0" :value="total" label="群聊总数"
          :sub="`未删除 ${summary.notDeleted} · 已删除 ${summary.deleted}`" />
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
        <StatCard icon="CubeOutline" color="#fa8c16" :value="summary.memberSum > 0 ? summary.memberSum : '—'" label="群成员总数"
          :sub="summary.memberSum > 0 ? `未删除 ${summary.notDeleted} 个群 · 部分群未同步` : '需 QQ 平台群 OPEN 事件自动同步'" :animate="summary.memberSum > 0" />
      </NGi>
    </NGrid>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <NDataTable
      :columns="columns"
      :data="groups"
      :pagination="false"
      :loading="loading"
      :bordered="false"
      size="small"
      striped
      style="margin-bottom: 12px"
    />
    <NSpace justify="end" align="center" style="margin-bottom: 16px">
      <NPagination
        :page="page"
        :page-size="pageSize"
        :item-count="total"
        :page-sizes="[20, 50, 100, 200]"
        show-size-picker
        size="small"
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </NSpace>

    <NEmpty
      v-if="!loading && !groups.length"
      :description="total ? '没有匹配结果，换个关键词试试' : '还没有群数据，收发群消息后会自动写入'"
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

    <!-- 机器人在群内状态详情 -->
    <NDrawer v-model:show="showRobotStates" :width="640" placement="right">
      <NDrawerContent :title="`机器人在群内状态 · ${robotStateGroup}`" closable>
        <NEmpty v-if="!robotStates.length" description="暂无记录（机器人入群或定时同步后自动补全）" style="padding: 40px 0" />
        <NSpace vertical :size="12" v-else>
          <NCard v-for="(s, i) in robotStates" :key="i" size="small" :bordered="true">
            <template #header>
              <NSpace align="center" :size="8">
                <NText strong>{{ botNameMap.get(String(s.BOT_APPID)) || `Bot #${s.BOT_APPID}` }}</NText>
                <NText depth="3" style="font-size: 12px">({{ s.BOT_APPID }})</NText>
                {{ robotRoleTag(s.MEMBER_ROLE) }}
              </NSpace>
            </template>
            <div class="rs-grid">
              <div class="rs-item"><span class="rs-label">机器人在群 openid</span><span class="rs-value">{{ s.ROBOT_OPENID || '—' }}</span></div>
              <div class="rs-item"><span class="rs-label">允许主动消息</span><span class="rs-value">{{ s.ALLOW_PROACTIVE_MSG ? '是' : (s.ALLOW_PROACTIVE_MSG === 0 ? '否' : '—') }}</span></div>
              <div class="rs-item"><span class="rs-label">消息接收设置</span><span class="rs-value">{{ s.RECV_MSG_SETTING || '—' }}</span></div>
              <div class="rs-item"><span class="rs-label">入群时间</span><span class="rs-value">{{ fmtTime(s.JOINED_AT) }}</span></div>
              <div class="rs-item"><span class="rs-label">最后同步时间</span><span class="rs-value">{{ fmtTime(s.UPDATED_AT) }}</span></div>
            </div>
          </NCard>
        </NSpace>
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

/* 机器人在群内状态字段网格 */
.rs-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 20px; }
.rs-item { display: flex; flex-direction: column; gap: 2px; }
.rs-label { font-size: 12px; color: #86909c; }
.rs-value { font-size: 13px; color: #1d2129; word-break: break-all; }
</style>
