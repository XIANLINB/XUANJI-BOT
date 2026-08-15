<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import {
  NDataTable, NButton, NAlert, NSpace, NIcon, NText, NSelect, NInput, NTag,
  NCard, NGrid, NGi, NNumberAnimation, NGradientText, NDatePicker, NImage,
  NTooltip, NPagination, NDrawer, NDrawerContent, NDivider, type DataTableColumns
} from 'naive-ui'
import {
  ChatbubbleEllipsesOutline, SearchOutline, ArrowDownOutline, ArrowUpOutline,
  RefreshOutline, ChatbubbleOutline, SwapVerticalOutline, AppsOutline
} from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import StatCard from '../components/StatCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { renderMarkdown } from '../utils/markdown'
import { useBotsStore } from '../stores/bots'

const rawRows = ref<any[]>([])
const total = ref(0)
const ins = ref(0)
const outs = ref(0)
const typeDist = ref<{ type: string; cnt: number }[]>([])
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const dirFilter = ref<string | null>(null)
const typeFilter = ref<string | null>(null)
const search = ref('')
const dateRange = ref<[number, number] | null>(null)
const botsStore = useBotsStore()

const botNameMap = computed(
  () => new Map((botsStore.bots || []).map((b) => [String(b.appId), b.name || `Bot #${b.appId}`]))
)

function msgTypeOf(row: any): string { return String(row.MSG_TYPE || 'text') }

/** 媒体渲染地址：QQ 富媒体原始 URL 走同源代理（复用 /console/media）。 */
function mediaSrc(content: string): string {
  if (!content) return ''
  if (content.startsWith('data:') || content.startsWith('/')) return content
  return '/xuanji/api/v1/console/media?ref=' + encodeURIComponent(content)
}

// 服务端筛选 + 分页（与群聊消息一致）
async function load() {
  loading.value = true
  err.value = ''
  try {
    const r = await api.getC2cMessages({
      bot: botFilter.value || undefined,
      page: page.value,
      size: pageSize.value,
      dir: dirFilter.value || undefined,
      type: typeFilter.value || undefined,
      startTime: dateRange.value ? Math.floor(dateRange.value[0] / 1000) : undefined,
      endTime: dateRange.value ? Math.floor(dateRange.value[1] / 1000) : undefined,
      q: search.value.trim() || undefined
    })
    rawRows.value = r?.rows || []
    total.value = Number(r?.total ?? 0)
    ins.value = Number(r?.ins ?? 0)
    outs.value = Number(r?.outs ?? 0)
    typeDist.value = (r?.typeDist || []) as { type: string; cnt: number }[]
  } catch (e: any) { err.value = e?.message || String(e); rawRows.value = [] }
  finally { loading.value = false }
}

let searchTimer: number | null = null
watch([botFilter, dirFilter, typeFilter, dateRange], () => { page.value = 1; load() })
watch(search, () => {
  if (searchTimer !== null) clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { page.value = 1; load() }, 300)
})

onMounted(async () => { await botsStore.loadBots(); await load() })

const TYPE_LABEL: Record<string, string> = {
  text: '文本', markdown: 'Markdown', image: '图片', voice: '语音',
  video: '视频', file: '文件', ark: 'Ark', rich_media: '富媒体', card: '卡片'
}
const TYPE_COLOR: Record<string, string> = {
  text: '#5b5bd6', markdown: '#2db7f5', image: '#07c160', voice: '#fa8c16',
  video: '#fa5151', file: '#722ed1', ark: '#13c2c2', rich_media: '#eb2f96', card: '#f0a020'
}
const TYPE_ORDER = ['text', 'image', 'voice', 'video', 'file', 'markdown', 'ark', 'card', 'rich_media']
const typeChips = computed(() => {
  const chips = TYPE_ORDER.map((t) => {
    const found = typeDist.value.find((d) => d.type === t)
    return { key: t, label: TYPE_LABEL[t] || t, color: TYPE_COLOR[t] || '#86909c', count: found ? Number(found.cnt) : 0 }
  }).filter((c) => c.count > 0)
  for (const d of typeDist.value) {
    if (!TYPE_ORDER.includes(d.type)) {
      chips.push({ key: d.type, label: TYPE_LABEL[d.type] || d.type, color: TYPE_COLOR[d.type] || '#86909c', count: Number(d.cnt) })
    }
  }
  return chips
})

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n) || n <= 0) return String(v)
  return dayjs(n <= 9999999999 ? n * 1000 : n).utcOffset(8).format('YYYY-MM-DD HH:mm:ss')
}

const TYPE_OPTIONS = Object.entries(TYPE_LABEL).map(([value, label]) => ({ label, value }))
const DIR_OPTIONS = [
  { label: '收到', value: 'IN' },
  { label: '发出', value: 'OUT' }
]

function renderContent(row: any) {
  const t = msgTypeOf(row)
  const c = row.CONTENT == null ? '' : String(row.CONTENT)
  if (t === 'image') {
    const cc = c.trim()
    if (cc.startsWith('http') || cc.startsWith('data:')) {
      return h(NImage, {
        src: mediaSrc(cc),
        width: 48, height: 48,
        objectFit: 'cover',
        style: { borderRadius: '6px', display: 'block' }
      })
    }
    return h('span', {}, c || '—')
  }
  if (['voice', 'video', 'file', 'ark', 'card', 'rich_media', 'markdown'].includes(t)) {
    return h('div', { style: 'display:flex;align-items:center;gap:6px;min-width:0' }, [
      h(NTag, { size: 'small', type: 'default', bordered: false, round: true,
        style: { color: TYPE_COLOR[t] || '#86909c', borderColor: TYPE_COLOR[t] || '#86909c' } },
        () => TYPE_LABEL[t] || t),
      h('span', {
        style: 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:160px;color:#6b7280;font-size:12px'
      }, c || '—')
    ])
  }
  if (c.length > 40) {
    return h(NTooltip, null, {
      trigger: () => h('span', { style: 'word-break:break-all;cursor:default' }, c.slice(0, 40) + '…'),
      default: () => c
    })
  }
  return h('span', { style: 'word-break:break-all' }, c || '—')
}

const columns: DataTableColumns = [
  { title: '时间', key: 'CREATE_TIME', width: 170, fixed: 'left', render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, fmtTime(row.CREATE_TIME)) },
  { title: '机器人', key: 'BOT', width: 100, render: (row) => h('span', { style: 'font-weight: 600' }, botNameMap.value.get(String(row.BOT_APPID)) || `Bot #${row.BOT_APPID}`) },
  {
    title: '方向', key: 'DIRECTION', width: 80,
    render: (row) => {
      const out = row.DIRECTION === 'OUT'
      return h(NTag, { size: 'small', type: out ? 'success' : 'default', bordered: false, round: true }, {
        icon: () => h(NIcon, { size: 12, component: out ? ArrowUpOutline : ArrowDownOutline }),
        default: () => out ? '发出' : '收到'
      })
    }
  },
  {
    title: '类型', key: 'MSG_TYPE', width: 80,
    render: (row) => {
      const t = msgTypeOf(row)
      return h('span', {
        style: `display:inline-block;padding:1px 8px;border-radius:10px;font-size:11px;color:${TYPE_COLOR[t] || '#86909c'};background:${TYPE_COLOR[t] || '#86909c'}15;border:1px solid ${TYPE_COLOR[t] || '#86909c'}40`
      }, TYPE_LABEL[t] || t)
    }
  },
  { title: '内容', key: 'CONTENT', width: 320, ellipsis: { tooltip: true }, render: (row) => renderContent(row) },
  { title: '用户 ID', key: 'USER_ID', width: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.USER_ID || '—')) },
  { title: '消息 ID', key: 'MSG_ID', width: 220, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, String(row.MSG_ID || '—')) }
]

// ---------- 分页 ----------
const page = ref(1)
const pageSize = ref(20)
function onPageChange(p: number) { page.value = p; load() }
function onPageSizeChange(s: number) { pageSize.value = s; page.value = 1; load() }

// ---------- 消息详情抽屉 ----------
const detail = ref<any>(null)
const showDetail = ref(false)
function openDetail(row: any) { detail.value = row; showDetail.value = true }
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NGradientText :gradient="{ deg: 90, from: '#2090e0', to: '#5b5bd6' }" :size="18" style="font-weight: 700">
          单聊消息
        </NGradientText>
        <NText depth="3" style="font-size: 13px">共 {{ total }} 条</NText>
      </div>
      <NSpace align="center" :wrap="true" style="row-gap: 8px">
        <NInput v-model:value="search" :placeholder="'搜索用户 ID / 内容'" clearable style="width: 180px">
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
        <NSelect v-model:value="dirFilter" :options="DIR_OPTIONS" placeholder="方向" clearable style="width: 90px" />
        <NSelect v-model:value="typeFilter" :options="TYPE_OPTIONS" placeholder="消息类型" clearable style="width: 110px" />
        <NDatePicker v-model:value="dateRange" type="daterange" clearable style="width: 200px" placeholder="按日期范围筛选" />
        <NSelect v-model:value="botFilter" :options="botsStore.bots.map((b) => ({ label: b.name || `Bot #${b.appId}`, value: String(b.appId) }))" placeholder="按机器人过滤" clearable style="width: 140px" />
        <NButton type="primary" :loading="loading" @click="load" size="small">
          <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <!-- 统计卡：统一 widget 风格 -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
      <NGi span="24 s:8">
        <StatCard icon="ChatbubbleOutline" color="#2090e0" :value="total" label="消息总数"
          :sub="`含 ${typeChips.length} 种类型`" />
      </NGi>
      <NGi span="24 s:8">
        <StatCard icon="SwapVerticalOutline" color="#07c160" :value="`收 ${ins} · 发 ${outs}`" label="消息方向" />
      </NGi>
      <NGi span="24 s:8">
        <StatCard icon="AppsOutline" color="#722ed1" :value="typeChips.length" label="消息类型分布"
          :sub="typeChips.length ? `${typeChips.map(c => c.label + '·' + c.count).join(' ')}` : '暂无消息'" />
      </NGi>
    </NGrid>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <NDataTable
      :columns="columns"
      :data="rawRows"
      :pagination="false"
      :loading="loading"
      :bordered="false"
      size="small"
      striped
      :row-props="(row) => ({ style: 'cursor: pointer', onClick: () => openDetail(row) })"
      style="margin-bottom: 12px"
    />
    <NSpace v-if="rawRows.length" justify="end" align="center" style="margin-bottom: 16px">
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
    <EmptyState v-if="!loading && !rawRows.length" :description="total ? '没有匹配结果，换个关键词试试' : '暂无单聊消息'" />

    <!-- 消息详情抽屉（markdown 渲染 / 大图 / 元数据） -->
    <NDrawer v-model:show="showDetail" :width="560" placement="right">
      <NDrawerContent title="消息详情" closable>
        <template v-if="detail">
          <div class="detail-grid">
            <div class="dg-item"><span class="dg-k">时间</span><span class="dg-v">{{ fmtTime(detail.CREATE_TIME) }}</span></div>
            <div class="dg-item"><span class="dg-k">方向</span><span class="dg-v">{{ detail.DIRECTION === 'OUT' ? '发出' : '收到' }}</span></div>
            <div class="dg-item"><span class="dg-k">类型</span><span class="dg-v">{{ TYPE_LABEL[msgTypeOf(detail)] || msgTypeOf(detail) }}</span></div>
            <div class="dg-item"><span class="dg-k">机器人</span><span class="dg-v">{{ botNameMap.get(String(detail.BOT_APPID)) || `Bot #${detail.BOT_APPID}` }}</span></div>
            <div class="dg-item"><span class="dg-k">用户 ID</span><span class="dg-v mono">{{ detail.USER_ID || '—' }}</span></div>
            <div class="dg-item"><span class="dg-k">消息 ID</span><span class="dg-v mono">{{ detail.MSG_ID || '—' }}</span></div>
          </div>
          <NDivider />
          <div v-if="msgTypeOf(detail) === 'markdown'" class="md-body" v-html="renderMarkdown(String(detail.CONTENT || ''))"></div>
          <template v-else-if="msgTypeOf(detail) === 'image'">
            <NImage :src="mediaSrc(String(detail.CONTENT || ''))" style="max-width: 100%" />
          </template>
          <template v-else-if="['voice', 'video', 'file'].includes(msgTypeOf(detail))">
            <NText depth="3" style="word-break: break-all">[{{ TYPE_LABEL[msgTypeOf(detail)] || msgTypeOf(detail) }}] {{ detail.CONTENT || '—' }}</NText>
          </template>
          <NText v-else style="word-break: break-all; white-space: pre-wrap">{{ detail.CONTENT || '—' }}</NText>
        </template>
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

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 20px; }
.dg-item { display: flex; flex-direction: column; gap: 2px; }
.dg-k { font-size: 12px; color: #86909c; }
.dg-v { font-size: 13px; color: #1d2129; word-break: break-all; }
.mono { font-variant-numeric: tabular-nums; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }

.md-body { word-break: break-word; line-height: 1.6; font-size: 13px; }
.md-body :deep(p) { margin: 0 0 8px; }
.md-body :deep(pre) { background: #f6f8fa; padding: 10px; border-radius: 6px; overflow: auto; }
.md-body :deep(code) { background: #f6f8fa; padding: 1px 4px; border-radius: 4px; font-size: 12px; }
.md-body :deep(blockquote) { margin: 0 0 8px; padding: 4px 12px; border-left: 3px solid #ddd; color: #666; }
.md-body :deep(img) { max-width: 100%; }
.md-body :deep(a) { color: #2090e0; }
</style>
