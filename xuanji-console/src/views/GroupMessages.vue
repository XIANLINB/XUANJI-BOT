<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import {
  NDataTable, NButton, NAlert, NSpace, NIcon, NText, NSelect, NInput, NTag,
  NCard, NGrid, NGi, NNumberAnimation, NGradientText, NDatePicker, NImage,
  NTooltip, NPagination, useMessage, type DataTableColumns
} from 'naive-ui'
import {
  ChatbubbleOutline, SearchOutline, ArrowDownOutline, ArrowUpOutline,
  RefreshOutline, SwapVerticalOutline, AppsOutline
} from '@vicons/ionicons5'
import dayjs from 'dayjs'
import api from '../api'
import StatCard from '../components/StatCard.vue'

const rawRows = ref<any[]>([])
const total = ref(0)
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const dirFilter = ref<string | null>(null)
const typeFilter = ref<string | null>(null)
const search = ref('')
const dateRange = ref<[number, number] | null>(null)
const bots = ref<{ appId: string; name: string }[]>([])

const botNameMap = computed(
  () => new Map((bots.value || []).map((b) => [String(b.appId), b.name || `Bot #${b.appId}`]))
)

const message = useMessage()

/** 发送超过 2 分钟的消息不可撤回（QQ 平台限制）。 */
const TWO_MIN_MS = 2 * 60 * 1000
function isMsgExpired(row: any): boolean {
  const t = Number(row.CREATE_TIME)
  if (!Number.isFinite(t) || t <= 0) return false
  const ts = t <= 9999999999 ? t * 1000 : t
  return Date.now() - ts > TWO_MIN_MS
}

/** 撤回群消息：机器人自己发的（OUT）免角色校验；他人消息后端会校验机器人本群是否管理员。 */
async function recallMsg(row: any) {
  if (!row.MSG_ID) { message.warning('该消息没有消息 ID，无法撤回'); return }
  if (isMsgExpired(row)) { message.warning('发送超过 2 分钟的消息不可撤回'); return }
  try {
    const r: any = await api.recallGroupMessage({
      appId: String(row.BOT_APPID || ''),
      groupOpenid: String(row.GROUP_ID || ''),
      msgId: String(row.MSG_ID),
      isOwn: row.DIRECTION === 'OUT'
    })
    if (r?.error) message.error(r.error)
    else { message.success('已撤回'); await load() }
  } catch (e: any) { message.error('撤回失败：' + (e?.message ?? e)) }
}

async function loadBots() { try { bots.value = (await api.getBots()) || [] } catch { bots.value = [] } }
async function load() {
  loading.value = true
  err.value = ''
  try {
    const r = await api.getMessages('group', botFilter.value || '', 0, 500)
    rawRows.value = r?.rows || []
    total.value = Number(r?.total ?? 0)
  } catch (e: any) { err.value = e?.message || String(e); rawRows.value = [] }
  finally { loading.value = false }
}
onMounted(async () => { await loadBots(); await load() })

const filtered = computed(() => {
  let rows = rawRows.value || []
  if (dirFilter.value) rows = rows.filter((r) => String(r.DIRECTION) === String(dirFilter.value))
  if (typeFilter.value) rows = rows.filter((r) => String(r.MSG_TYPE || 'text') === String(typeFilter.value))
  if (dateRange.value) {
    const [s, e] = dateRange.value
    rows = rows.filter((r) => {
      const t = Number(r.CREATE_TIME)
      const ms = t <= 9999999999 ? t * 1000 : t
      return ms >= s && ms <= e
    })
  }
  const q = search.value.trim().toLowerCase()
  if (q) {
    rows = rows.filter(
      (r) =>
        String(r.USER_ID || '').toLowerCase().includes(q) ||
        String(r.CONTENT || '').toLowerCase().includes(q) ||
        String(r.GROUP_ID || '').toLowerCase().includes(q)
    )
  }
  return rows
})

// ---------- 统计卡（统一 widget 风格） ----------
const ins = computed(() => filtered.value.filter((r) => String(r.DIRECTION) === 'IN').length)
const outs = computed(() => filtered.value.filter((r) => String(r.DIRECTION) === 'OUT').length)

const TYPE_LABEL: Record<string, string> = {
  text: '文本', markdown: 'Markdown', image: '图片', voice: '语音',
  video: '视频', file: '文件', ark: 'Ark', rich_media: '富媒体', card: '卡片'
}
const TYPE_COLOR: Record<string, string> = {
  text: '#5b5bd6', markdown: '#2db7f5', image: '#07c160', voice: '#fa8c16',
  video: '#fa5151', file: '#722ed1', ark: '#13c2c2', rich_media: '#eb2f96', card: '#f0a020'
}
const TYPE_ORDER = ['text', 'image', 'voice', 'video', 'file', 'markdown', 'ark', 'card', 'rich_media']
const typeChips = computed(() =>
  TYPE_ORDER.map((t) => ({
    key: t, label: TYPE_LABEL[t] || t, color: TYPE_COLOR[t] || '#86909c',
    count: filtered.value.filter((r) => String(r.MSG_TYPE || 'text') === t).length
  })).filter((c) => c.count > 0)
)

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n) || n <= 0) return String(v)
  return dayjs(n <= 9999999999 ? n * 1000 : n).format('YYYY-MM-DD HH:mm:ss')
}

const TYPE_OPTIONS = Object.entries(TYPE_LABEL).map(([value, label]) => ({ label, value }))
const DIR_OPTIONS = [
  { label: '收到', value: 'IN' },
  { label: '发出', value: 'OUT' }
]

function renderContent(row: any) {
  const t = String(row.MSG_TYPE || 'text')
  const c = row.CONTENT == null ? '' : String(row.CONTENT)
  if (t === 'image' && /^https?:\/\//.test(c.trim())) {
    return h(NImage, {
      src: c.trim(),
      width: 48, height: 48,
      objectFit: 'cover',
      style: { borderRadius: '6px', display: 'block' }
    })
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
  { title: '时间', key: 'CREATE_TIME', width: 160, fixed: 'left', render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, fmtTime(row.CREATE_TIME)) },
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
      const t = String(row.MSG_TYPE || 'text')
      return h('span', {
        style: `display:inline-block;padding:1px 8px;border-radius:10px;font-size:11px;color:${TYPE_COLOR[t] || '#86909c'};background:${TYPE_COLOR[t] || '#86909c'}15;border:1px solid ${TYPE_COLOR[t] || '#86909c'}40`
      }, TYPE_LABEL[t] || t)
    }
  },
  { title: '内容', key: 'CONTENT', width: 300, ellipsis: { tooltip: true }, render: (row) => renderContent(row) },
  { title: '群号', key: 'GROUP_ID', width: 140, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.GROUP_ID || '—')) },
  { title: '用户 ID', key: 'USER_ID', width: 160, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.USER_ID || '—')) },
  { title: '消息 ID', key: 'MSG_ID', width: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, String(row.MSG_ID || '—')) },
  {
    title: '操作', key: 'ACTION', width: 110, fixed: 'right',
    render: (row) => {
      if (Number(row.RETRACTED) === 1) {
        return h(NTag, { size: 'small', type: 'default', bordered: false }, () => '已撤回')
      }
      const expired = isMsgExpired(row)
      const btn = h(NButton, {
        size: 'small', type: 'warning', secondary: true,
        disabled: !row.MSG_ID || expired,
        onClick: () => recallMsg(row)
      }, () => expired ? '超2分钟' : '撤回')
      return h(NTooltip, { disabled: !expired }, {
        trigger: () => btn,
        default: () => '发送超过 2 分钟的消息不可撤回'
      })
    }
  }
]

// ---------- 分页 ----------
const page = ref(1)
const pageSize = ref(20)
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})
watch(filtered, () => { page.value = 1 })
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NGradientText :gradient="{ deg: 90, from: '#5b5bd6', to: '#2090e0' }" :size="18" style="font-weight: 700">
          群聊消息
        </NGradientText>
        <NText depth="3" style="font-size: 13px">共 {{ filtered.length }} 条（全部 {{ total }}）</NText>
      </div>
      <NSpace align="center" :wrap="true" style="row-gap: 8px">
        <NInput v-model:value="search" :placeholder="'搜索用户 ID / 内容 / 群号'" clearable style="width: 180px">
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
        <NSelect v-model:value="dirFilter" :options="DIR_OPTIONS" placeholder="方向" clearable style="width: 90px" />
        <NSelect v-model:value="typeFilter" :options="TYPE_OPTIONS" placeholder="消息类型" clearable style="width: 110px" />
        <NDatePicker v-model:value="dateRange" type="daterange" clearable style="width: 200px" placeholder="按日期范围筛选" />
        <NSelect v-model:value="botFilter" :options="bots.map((b) => ({ label: b.name || `Bot #${b.appId}`, value: String(b.appId) }))" placeholder="按机器人过滤" clearable style="width: 140px" />
        <NButton type="primary" :loading="loading" @click="load" size="small">
          <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <!-- 统计卡：统一 widget 风格 -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
      <NGi span="24 s:8">
        <StatCard icon="ChatbubbleOutline" color="#5b5bd6" :value="filtered.length" label="消息总数"
          :sub="`含 ${typeChips.length} 种类型 · DB 共 ${total} 条`" />
      </NGi>
      <NGi span="24 s:8">
        <StatCard icon="SwapVerticalOutline" color="#07c160" :value="filtered.length" label="消息方向"
          :sub="`收 ${ins} · 发 ${outs}`" />
      </NGi>
      <NGi span="24 s:8">
        <StatCard icon="AppsOutline" color="#722ed1" :value="typeChips.length" label="消息类型分布"
          :sub="typeChips.length ? `${typeChips.map(c => c.label + '·' + c.count).join(' ')}` : '暂无消息'" />
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