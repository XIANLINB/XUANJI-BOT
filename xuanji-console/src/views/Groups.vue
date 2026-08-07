<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import {
  NDataTable, NButton, NDrawer, NDrawerContent, NAlert, NSpace, NIcon, NText,
  NEmpty, NSelect, NInput, NSwitch, NTag, type DataTableColumns
} from 'naive-ui'
import { PeopleOutline, SearchOutline } from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import { groupName, userName } from '../utils/names'
import { useFillHeight } from '../composables/useFillHeight'

const { fillHeight } = useFillHeight(16)

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

async function loadBots() {
  try {
    bots.value = (await api.getBots()) || []
  } catch {
    bots.value = []
  }
}

async function load() {
  loading.value = true
  try {
    groups.value = await api.getGroups()
    err.value = ''
  } catch (e: any) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await loadBots()
  await load()
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

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n) || n <= 0) return String(v)
  return dayjs(n <= 9999999999 ? n * 1000 : n).format('YYYY-MM-DD HH:mm')
}

const columns = computed<DataTableColumns>(() => [
  {
    title: '机器人名称', key: 'BOT_NAME', minWidth: 160,
    render: (row) =>
      h('span', { style: 'font-weight: 600' }, botNameMap.value.get(String(row.BOT_APPID)) || `Bot #${row.BOT_APPID}`)
  },
  { title: '群号', key: 'GROUP_ID', minWidth: 200, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.GROUP_ID || '—')) },
  { title: '群名称', key: 'GROUP_NAME', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => h('span', groupName(row)) },
  { title: '群主 ID', key: 'OWNER_ID', minWidth: 140, render: (row) => h('span', String(row.OWNER_ID || '—')) },
  { title: '成员数', key: 'MEMBER_COUNT', width: 100, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.MEMBER_COUNT ?? '—')) },
  { title: '加入时间', key: 'JOIN_TIME', width: 170, render: (row) => h('span', fmtTime(row.JOIN_TIME)) },
  {
    title: '状态', key: 'STATUS', width: 100,
    render: (row) => {
      if (Number(row.IS_DELETED) === 1) {
        return h(NTag, { size: 'small', type: 'default', bordered: false }, () => '已删除')
      }
      const raw = String(row.STATUS || '').toLowerCase()
      if (raw === 'active') return h(NTag, { size: 'small', type: 'success', bordered: false }, () => '正常')
      if (raw === 'removed') return h(NTag, { size: 'small', type: 'error', bordered: false }, () => '已退出')
      return h(NTag, { size: 'small', type: 'warning', bordered: false }, () => row.STATUS || '—')
    }
  }
])

const pagination = computed(() =>
  filtered.value.length > 20
    ? { pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100, 200], showTotal: (t: number) => `共 ${t} 个` }
    : false
)

const members = ref<any[]>([])
const showMembers = ref(false)
const memberGroup = ref('')
async function openMembers(g: any) {
  memberGroup.value = g.GROUP_ID
  try {
    const all: any[] = await api.getGroupMembers(g.GROUP_ID)
    members.value = all.filter((m) => String(m.BOT_APPID) === String(g.BOT_APPID))
  } catch {
    members.value = []
  }
  showMembers.value = true
}

const memberColumns: DataTableColumns = [
  { title: '成员 ID', key: 'MEMBER_ID', minWidth: 180 },
  { title: '昵称', key: 'NICKNAME', minWidth: 140, render: (row) => h('span', userName(row)) },
  { title: '角色', key: 'ROLE', width: 90, render: (row) => h(NTag, { size: 'small', type: row.ROLE === 'owner' ? 'warning' : 'default', bordered: false }, () => row.ROLE || 'member') },
  { title: '入群时间', key: 'JOIN_TIME', width: 170, render: (row) => h('span', fmtTime(row.JOIN_TIME)) }
]
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#2090e0"><PeopleOutline /></NIcon>
        <span>群聊列表</span>
        <NText depth="3" style="font-size: 13px">共 {{ filtered.length }} 个</NText>
      </div>
      <NSpace align="center" :wrap="false">
        <NInput
          v-model:value="search"
          :placeholder="'搜索群号 / 群名称'"
          clearable
          style="width: 240px"
        >
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
        <NSelect
          v-model:value="botFilter"
          :options="bots.map((b) => ({ label: b.name || `Bot #${b.appId}`, value: String(b.appId) }))"
          placeholder="按机器人过滤"
          clearable
          style="width: 200px"
        />
        <NSpace align="center" :size="6">
          <NSwitch v-model:value="showDeleted" />
          <span style="font-size: 13px">显示已删除</span>
        </NSpace>
        <NButton type="primary" :loading="loading" @click="load">刷新</NButton>
      </NSpace>
    </div>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <NDataTable
      :columns="columns"
      :data="filtered"
      :pagination="pagination"
      :loading="loading"
      :max-height="fillHeight"
      size="small"
      striped
      :row-props="(row) => ({ style: 'cursor: pointer', onClick: () => openMembers(row) })"
      :scroll-x="1080"
    />

    <NEmpty
      v-if="!loading && !filtered.length"
      :description="(groups.value && groups.value.length) ? '没有匹配结果，换个关键词试试' : '还没有群数据，收发群消息后会自动写入'"
      style="padding: 60px 0"
    />

    <NDrawer v-model:show="showMembers" :width="760" placement="right">
      <NDrawerContent :title="`群成员 · ${memberGroup}`" closable>
        <NDataTable
          :columns="memberColumns"
          :data="members"
          :pagination="{ pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100] }"
          size="small"
          :max-height="600"
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
.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
</style>