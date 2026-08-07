<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import {
  NDataTable, NButton, NAlert, NSpace, NIcon, NText, NSelect, NInput, NTag,
  type DataTableColumns
} from 'naive-ui'
import { ChatbubbleEllipsesOutline, SearchOutline } from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import { useFillHeight } from '../composables/useFillHeight'

const { fillHeight } = useFillHeight(16)

const rawRows = ref<any[]>([])
const total = ref(0)
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const dirFilter = ref<string | null>(null)
const typeFilter = ref<string | null>(null)
const search = ref('')
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
  err.value = ''
  try {
    const r = await api.getMessages('c2c', botFilter.value || '', 0, 500)
    rawRows.value = r?.rows || []
    total.value = Number(r?.total ?? 0)
  } catch (e: any) {
    err.value = e?.message || String(e)
    rawRows.value = []
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await loadBots()
  await load()
})

const filtered = computed(() => {
  let rows = rawRows.value || []
  if (dirFilter.value) rows = rows.filter((r) => String(r.DIRECTION) === String(dirFilter.value))
  if (typeFilter.value) rows = rows.filter((r) => String(r.MSG_TYPE || 'text') === String(typeFilter.value))
  const q = search.value.trim().toLowerCase()
  if (q) {
    rows = rows.filter(
      (r) =>
        String(r.USER_ID || '').toLowerCase().includes(q) ||
        String(r.CONTENT || '').toLowerCase().includes(q)
    )
  }
  return rows
})

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n) || n <= 0) return String(v)
  return dayjs(n <= 9999999999 ? n * 1000 : n).format('YYYY-MM-DD HH:mm:ss')
}

const DIRECTION_LABEL: Record<string, string> = { IN: '收到', OUT: '发出' }
const TYPE_LABEL: Record<string, string> = {
  text: '文本', markdown: 'Markdown', image: '图片', voice: '语音',
  video: '视频', file: '文件', ark: 'Ark', rich_media: '富媒体', card: '卡片'
}
const TYPE_COLOR: Record<string, 'default' | 'info' | 'success' | 'warning' | 'error' | 'primary'> = {
  text: 'default', markdown: 'info', image: 'success', voice: 'warning',
  video: 'error', file: 'primary', ark: 'info', rich_media: 'warning', card: 'primary'
}
const TYPE_OPTIONS = Object.entries(TYPE_LABEL).map(([value, label]) => ({ label, value }))
const DIR_OPTIONS = [
  { label: '收到', value: 'IN' },
  { label: '发出', value: 'OUT' }
]

function longCell(v: unknown) {
  const s = v == null ? '—' : String(v)
  return h('span', { style: 'word-break: break-all' }, s)
}

const columns: DataTableColumns = [
  { title: '时间', key: 'CREATE_TIME', width: 172, fixed: 'left', render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, fmtTime(row.CREATE_TIME)) },
  { title: '机器人名称', key: 'BOT', width: 110, render: (row) => h('span', { style: 'font-weight: 600' }, botNameMap.value.get(String(row.BOT_APPID)) || `Bot #${row.BOT_APPID}`) },
  {
    title: '方向', key: 'DIRECTION', width: 80,
    render: (row) => h(NTag, { size: 'small', type: row.DIRECTION === 'OUT' ? 'success' : 'default', bordered: false }, () => DIRECTION_LABEL[row.DIRECTION] || row.DIRECTION || '—')
  },
  { title: '聊天类型', key: 'CHAT_TYPE', width: 100, render: () => h(NTag, { size: 'small', type: 'info', bordered: false }, () => '单聊') },
  {
    title: '消息类型', key: 'MSG_TYPE', width: 100,
    render: (row) => {
      const t = String(row.MSG_TYPE || 'text')
      return h(NTag, { size: 'small', type: TYPE_COLOR[t] || 'default', bordered: false }, () => TYPE_LABEL[t] || t)
    }
  },
  { title: '内容', key: 'CONTENT', minWidth: 320, ellipsis: { tooltip: true }, render: (row) => longCell(row.CONTENT) },
  { title: '用户 ID', key: 'USER_ID', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => longCell(row.USER_ID) },
  { title: '消息 ID', key: 'MSG_ID', minWidth: 250, ellipsis: { tooltip: true }, render: (row) => longCell(row.MSG_ID) }
]

const pagination = computed(() =>
  filtered.value.length > 20
    ? { pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100, 200], showTotal: (t: number) => `共 ${t} 条` }
    : false
)
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#2090e0"><ChatbubbleEllipsesOutline /></NIcon>
        <span>单聊消息</span>
        <NText depth="3" style="font-size: 13px">共 {{ filtered.length }} 条（全部 {{ total }}）</NText>
      </div>
      <NSpace align="center" :wrap="false">
        <NInput
          v-model:value="search"
          :placeholder="'搜索用户 ID / 内容'"
          clearable
          style="width: 240px"
        >
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
        <NSelect
          v-model:value="dirFilter"
          :options="DIR_OPTIONS"
          placeholder="方向"
          clearable
          style="width: 110px"
        />
        <NSelect
          v-model:value="typeFilter"
          :options="TYPE_OPTIONS"
          placeholder="消息类型"
          clearable
          style="width: 130px"
        />
        <NSelect
          v-model:value="botFilter"
          :options="bots.map((b) => ({ label: b.name || `Bot #${b.appId}`, value: String(b.appId) }))"
          placeholder="按机器人过滤"
          clearable
          style="width: 160px"
        />
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
      :scroll-x="1400"
    />
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