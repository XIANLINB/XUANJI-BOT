<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import {
  NDataTable, NButton, NDrawer, NDrawerContent, NAlert, NSpace, NIcon, NText,
  NSelect, NInput, NSwitch, NTag, NCard, NGrid, NGi, NNumberAnimation,
  NGradientText, NPagination, useMessage, type DataTableColumns
} from 'naive-ui'
import { PersonOutline, SearchOutline, RefreshOutline, TrendingUpOutline } from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import StatCard from '../components/StatCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { userName } from '../utils/names'
import { useBotsStore } from '../stores/bots'

const message = useMessage()

const friends = ref<any[]>([])
const total = ref(0)
const summary = ref<{ notDeleted: number; deleted: number }>({ notDeleted: 0, deleted: 0 })
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const search = ref('')
const showDeleted = ref(false)
const botsStore = useBotsStore()

const botNameMap = computed(
  () => new Map((botsStore.bots || []).map((b) => [String(b.appId), b.name || `Bot #${b.appId}`]))
)

// 服务端分页 + 过滤（与群聊列表一致）
async function load() {
  loading.value = true
  try {
    const res = await api.getFriendsPage({
      page: page.value,
      size: pageSize.value,
      bot: botFilter.value || undefined,
      q: search.value.trim() || undefined,
      showDeleted: showDeleted.value
    })
    friends.value = res?.rows || []
    total.value = Number(res?.total ?? 0)
    summary.value = {
      notDeleted: Number(res?.notDeleted ?? 0),
      deleted: Number(res?.deleted ?? 0)
    }
    err.value = ''
  } catch (e: any) {
    err.value = e.message
    friends.value = []
  } finally {
    loading.value = false
  }
}

const variation = ref<Record<string, number>>({})
async function loadVariation() {
  if (!botFilter.value) {
    try { variation.value = (await api.getFriendsVariationAll()) || {} } catch { variation.value = {} }
    return
  }
  const bot = botsStore.bots.find((x) => String(x.appId) === String(botFilter.value))
  const key = bot?.botKey || botFilter.value
  try { variation.value = (await api.getBotFriendVariation(key)) || {} } catch { variation.value = {} }
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
const todayNew = computed(() => Number(variation.value.todayNewFriends) || 0)
const ydayNew = computed(() => Number(variation.value.ydayNewFriends) || 0)
const todayActive = computed(() => Number(variation.value.todayActiveUsers) || 0)
const ydayActive = computed(() => Number(variation.value.ydayActiveUsers) || 0)
const deltaNew = computed(() => todayNew.value - ydayNew.value)
const deltaActive = computed(() => todayActive.value - ydayActive.value)

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n) || n <= 0) return String(v)
  return dayjs(n <= 9999999999 ? n * 1000 : n).utcOffset(8).format('YYYY-MM-DD HH:mm')
}

const columns = computed<DataTableColumns>(() => [
  {
    title: '机器人名称', key: 'BOT_NAME', width: 130,
    render: (row) => h('span', { style: 'font-weight: 600' }, botNameMap.value.get(String(row.BOT_APPID)) || `Bot #${row.BOT_APPID}`)
  },
  { title: '用户 ID', key: 'PLATFORM_USER_ID', width: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.PLATFORM_USER_ID || '—')) },
  { title: '昵称', key: 'NICKNAME', width: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-weight: 500' }, userName(row)) },
  { title: '备注', key: 'REMARK', width: 120, ellipsis: { tooltip: true }, render: (row) => h('span', String(row.REMARK || '—')) },
  { title: 'UnionID', key: 'UNION_OPENID', width: 170, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #722ed1' }, String(row.UNION_OPENID || '—')) },
  { title: '加入时间', key: 'JOIN_TIME', width: 150, ellipsis: { tooltip: true }, render: (row) => h('span', fmtTime(row.JOIN_TIME)) },
  {
    title: '已删除', key: 'IS_DELETED', width: 80,
    render: (row) => {
      if (Number(row.IS_DELETED) === 1) return h(NTag, { size: 'small', type: 'error', bordered: false }, () => '已删除')
      return h(NTag, { size: 'small', type: 'success', bordered: false }, () => '正常')
    }
  }
])

// ---------- 分页 ----------
const page = ref(1)
const pageSize = ref(20)
function onPageChange(p: number) { page.value = p; load() }
function onPageSizeChange(s: number) { pageSize.value = s; page.value = 1; load() }

const msgs = ref<any[]>([])
const showMsgs = ref(false)
const targetName = ref('')
async function openMsgs(f: any) {
  targetName.value = f.PLATFORM_USER_ID
  try {
    // 修复：getContactMessages 是单对象参数 { type, targetId }，非位置参数
    const res: any = await api.getContactMessages({ type: 'c2c', targetId: f.PLATFORM_USER_ID })
    const all: any[] = res?.rows || []
    msgs.value = all.filter((m) => String(m.BOT_APPID) === String(f.BOT_APPID))
  } catch {
    msgs.value = []
    message.error('加载单聊记录失败')
  }
  showMsgs.value = true
}

const msgColumns: DataTableColumns = [
  { title: '时间', key: 'CREATE_TIME', width: 170, render: (row) => h('span', fmtTime(row.CREATE_TIME)) },
  { title: '方向', key: 'DIRECTION', width: 90, render: (row) => h(NTag, { size: 'small', type: row.DIRECTION === 'OUT' ? 'success' : 'default', bordered: false }, () => (row.DIRECTION === 'OUT' ? '发出' : '收到')) },
  { title: '类型', key: 'MSG_TYPE', width: 100, render: (row) => h(NTag, { size: 'small', type: 'info', bordered: false }, () => row.MSG_TYPE || 'text') },
  { title: '内容', key: 'CONTENT', minWidth: 260, ellipsis: { tooltip: true }, render: (row) => h('span', String(row.CONTENT || '—')) }
]
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NGradientText :gradient="{ deg: 90, from: '#f0a020', to: '#eb2f96' }" :size="18" style="font-weight: 700">
          单聊列表
        </NGradientText>
        <NText depth="3" style="font-size: 13px">共 {{ total }} 人</NText>
      </div>
      <NSpace align="center" :wrap="false">
        <NInput v-model:value="search" :placeholder="'搜索用户 ID / 昵称'" clearable style="width: 200px">
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

    <!-- 统计卡：统一 widget 风格 -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
      <NGi span="24 s:12 m:6">
        <StatCard icon="PersonOutline" color="#f0a020" :value="total" label="单聊用户总数"
          :sub="`未删除 ${summary.notDeleted} · 已删除 ${summary.deleted}`" />
      </NGi>
      <NGi span="24 s:12 m:18">
        <StatCard icon="TrendingUpOutline" color="#eb2f96" :value="todayNew + todayActive" label="用户变动"
          :sub="`今日新加 ${todayNew} · 今日活跃 ${todayActive} · 新加较昨日 ${deltaNew > 0 ? '+' + deltaNew : deltaNew} · 活跃较昨日 ${deltaActive > 0 ? '+' + deltaActive : deltaActive}`" />
      </NGi>
    </NGrid>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <NDataTable
      :columns="columns"
      :data="friends"
      :pagination="false"
      :loading="loading"
      :bordered="false"
      size="small"
      striped
      :row-props="(row) => ({ style: 'cursor: pointer', onClick: () => openMsgs(row) })"
      style="margin-bottom: 12px"
    />
    <NSpace v-if="friends.length" justify="end" align="center" style="margin-bottom: 16px">
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
    <EmptyState v-if="!loading && !friends.length" :description="total ? '没有匹配结果，换个关键词试试' : '还没有单聊好友，发过消息后会自动写入'" />

    <NDrawer v-model:show="showMsgs" :width="760" placement="right">
      <NDrawerContent :title="`单聊记录 · ${targetName}`" closable>
        <NDataTable
          :columns="msgColumns"
          :data="msgs"
          :pagination="{ pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100] }"
          size="small"
        />
        <EmptyState v-if="!msgs.length" :description="'暂无消息'" />
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
</style>
