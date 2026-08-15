<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import {
  NDataTable, NSelect, NButton, NAlert, NSpace, NIcon, NText, NInput,
  NTag, NEmpty, NSpin, NGradientText, useMessage, type DataTableColumns
} from 'naive-ui'
import { RefreshOutline, SearchOutline } from '@vicons/ionicons5'
import api from '../api'
import { useBots } from '../composables/useBots'
import dayjs from 'dayjs'
import EmptyState from '../components/EmptyState.vue'

const { bots, loadBots } = useBots()
const message = useMessage()

const botFilter = ref('')
const opTypeFilter = ref('')
const statusFilter = ref('')
const keyword = ref('')
const rows = ref<any[]>([])
const err = ref('')
const loading = ref(false)

const OP_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '禁言', value: 'mute' },
  { label: '撤回群消息', value: 'recall' },
  { label: '撤回单聊消息', value: 'recall_private' },
  { label: '入群审批', value: 'join_approve' }
]
const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failed' }
]

// op_type / action / source / status → 中文标签 + 配色
const OP_LABEL: Record<string, string> = {
  mute: '禁言', recall: '撤回群消息', recall_private: '撤回单聊消息', join_approve: '入群审批'
}
const ACTION_LABEL: Record<string, string> = {
  add: '禁言', del: '解除', approve: '同意', reject: '拒绝', recall: '撤回', run: '执行'
}
const SOURCE_LABEL: Record<string, string> = {
  group_command: '群命令', console: '控制台', plugin: '插件'
}
const STATUS_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  success: 'success', failed: 'error', denied: 'warning'
}

function fmtTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(String(v).trim())
  if (!Number.isFinite(n)) return String(v)
  const d = dayjs(n <= 9999999999 ? n * 1000 : n)
  if (!d.isValid()) return String(v)
  return d.utcOffset(8).format('YYYY-MM-DD HH:mm:ss')
}

function fmtDuration(v: unknown): string {
  if (v == null || Number(v) <= 0) return '—'
  const s = Number(v)
  if (s >= 86400) return `${Math.round(s / 86400)} 天`
  if (s >= 3600) return `${Math.round(s / 3600)} 小时`
  if (s >= 60) return `${Math.round(s / 60)} 分钟`
  return `${s} 秒`
}

function textCell(v: unknown, max = 100) {
  if (v == null || v === '') return h('span', { style: 'color: #86909c' }, '—')
  const s = String(v)
  return h('span', { title: s, style: 'cursor: help; word-break: break-all' }, s.length > max ? s.slice(0, max) + '…' : s)
}

async function load() {
  loading.value = true
  err.value = ''
  try {
    const r = await api.getOpLogs({
      bot: botFilter.value,
      opType: opTypeFilter.value,
      status: statusFilter.value,
      keyword: keyword.value.trim(),
      limit: 500
    })
    rows.value = Array.isArray(r?.rows) ? r.rows : []
  } catch (e: any) {
    err.value = e?.message || String(e)
    rows.value = []
  } finally {
    loading.value = false
  }
}

function onSearch() { load() }

const columns = computed<DataTableColumns>(() => [
  {
    title: '时间', key: 'CREATE_TIME', width: 160,
    render: (row: any) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, fmtTime(row.CREATE_TIME))
  },
  {
    title: '类型', key: 'OP_TYPE', width: 110,
    render: (row: any) => h(NTag, { size: 'small', round: true, bordered: false, type: 'info' },
      { default: () => OP_LABEL[row.OP_TYPE] || row.OP_TYPE || '—' })
  },
  {
    title: '动作', key: 'ACTION', width: 80,
    render: (row: any) => ACTION_LABEL[row.ACTION] || row.ACTION || '—'
  },
  {
    title: '结果', key: 'STATUS', width: 80,
    render: (row: any) => h(NTag, { size: 'small', round: true, bordered: false, type: STATUS_COLOR[row.STATUS] ?? 'default' },
      { default: () => (row.STATUS === 'success' ? '成功' : row.STATUS === 'failed' ? '失败' : row.STATUS || '—') })
  },
  { title: '群', key: 'GROUP_ID', width: 170, ellipsis: { tooltip: true }, render: (row: any) => textCell(row.GROUP_ID, 60) },
  {
    title: '目标', key: 'USER_ID', width: 170, ellipsis: { tooltip: true },
    render: (row: any) => {
      const t = row.USER_ID || row.TARGET_MSG_ID
      return textCell(t, 60)
    }
  },
  { title: '时长', key: 'DURATION_SEC', width: 90, render: (row: any) => h('span', { style: 'color: #86909c' }, fmtDuration(row.DURATION_SEC)) },
  { title: '操作人', key: 'OPERATOR', width: 160, ellipsis: { tooltip: true },
    render: (row: any) => {
      const name = row.OPERATOR_NAME
      const id = row.OPERATOR_ID
      const text = name ? (id && id !== name ? `${name}(${id})` : name) : (id || '—')
      return textCell(text, 40)
    }
  },
  { title: '来源', key: 'SOURCE', width: 90, render: (row: any) => SOURCE_LABEL[row.SOURCE] || row.SOURCE || '—' },
  {
    title: '说明', key: 'ERROR_MSG', width: 260, ellipsis: { tooltip: true },
    render: (row: any) => {
      const s = row.ERROR_MSG
      if (!s) return h('span', { style: 'color: #86909c' }, '—')
      return h('span', { style: 'color: #e5484d; cursor: help', title: s }, s.length > 60 ? s.slice(0, 60) + '…' : s)
    }
  },
  { title: 'Bot', key: 'BOT_APPID', width: 120, ellipsis: { tooltip: true }, render: (row: any) => textCell(row.BOT_APPID, 30) }
])

const pagination = computed(() =>
  rows.value.length > 20
    ? { pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100, 200], showTotal: (t: number) => `共 ${t} 条` }
    : false
)

onMounted(async () => {
  await loadBots()
  await load()
})
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NGradientText :gradient="{ deg: 90, from: '#18a058', to: '#2090e0' }" :size="18" style="font-weight: 700">
          操作日志
        </NGradientText>
        <NText depth="3" style="font-size: 13px">共 {{ rows.length }} 条（禁言/撤回/审批等管理操作，含失败与被拒记录）</NText>
      </div>
      <NSpace align="center" :size="8" wrap>
        <NSelect
          v-model:value="botFilter"
          :options="bots"
          placeholder="按 Bot 过滤"
          clearable
          size="small"
          style="width: 150px"
          @update:value="onSearch"
        />
        <NSelect v-model:value="opTypeFilter" :options="OP_TYPE_OPTIONS" size="small" style="width: 120px" @update:value="onSearch" />
        <NSelect v-model:value="statusFilter" :options="STATUS_OPTIONS" size="small" style="width: 110px" @update:value="onSearch" />
        <NInput
          v-model:value="keyword"
          placeholder="搜索操作人/目标/消息ID"
          clearable
          size="small"
          style="width: 200px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <NButton size="small" type="primary" :loading="loading" @click="onSearch">
          <template #icon><NIcon><SearchOutline /></NIcon></template>
          查询
        </NButton>
        <NButton size="small" secondary :loading="loading" @click="load">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </div>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <NSpin :show="loading" style="min-height: 200px">
      <EmptyState
        v-if="!loading && !rows.length"
        :description="'暂无操作日志（禁言/撤回/审批等管理操作会写入，需重启应用后生效）'"
      />
      <NDataTable
        v-else
        :columns="columns"
        :data="rows"
        :pagination="pagination"
        :bordered="false"
        size="small"
        striped
        style="margin-bottom: 12px"
      />
    </NSpin>
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
  gap: 10px;
}
</style>
