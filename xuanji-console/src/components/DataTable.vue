<script setup lang="ts">
import { computed, h } from 'vue'
import { NDataTable, NTooltip, NText, NTag, NEmpty, NSpin } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { colLabel } from '../columns'

const props = withDefaults(
  defineProps<{
    rows: Record<string, any>[]
    columns?: string[]
    maxLen?: number
    pageSize?: number
    clickable?: boolean
    loading?: boolean
    emptyText?: string
  }>(),
  { maxLen: 200, pageSize: 20, clickable: false, loading: false, emptyText: '暂无数据', maxHeight: 640 }
)

const emit = defineEmits<{ (e: 'row-click', row: Record<string, any>): void }>()

function fmtEpoch(v: unknown): string {
  if (v == null || v === '') return ''
  const raw = String(v).trim()
  const n = Number(raw)
  if (!Number.isFinite(n)) return raw
  const ms = raw.length <= 10 ? n * 1000 : n
  const d = new Date(ms)
  if (isNaN(d.getTime())) return raw
  const p = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(
    d.getMinutes()
  )}:${p(d.getSeconds())}`
}

function isTimeCol(key: string): boolean {
  const k = key.toLowerCase()
  return k.endsWith('time') || k.includes('_time')
}

function cellVnode(key: string, v: unknown) {
  if (v == null) return h(NText, { depth: 3 }, () => '—')
  // 收发方向：IN 灰、OUT 绿
  if (key === 'DIRECTION') {
    return h(
      NTag,
      { size: 'small', type: v === 'OUT' ? 'success' : 'warning', bordered: false },
      () => (v === 'OUT' ? '发出' : '收到')
    )
  }
  if (isTimeCol(key)) return h('span', fmtEpoch(v))
  const str = typeof v === 'object' ? JSON.stringify(v) : String(v)
  if (str.length > props.maxLen) {
    return h(NTooltip, { trigger: 'hover' }, {
      trigger: () => h('span', { style: 'cursor: help; border-bottom: 1px dotted #aaa' }, str.slice(0, props.maxLen) + '…'),
      default: () => str
    })
  }
  return h('span', str)
}

const cols = computed<DataTableColumns>(() => {
  const keys =
    props.columns && props.columns.length
      ? props.columns
      : props.rows[0]
        ? Object.keys(props.rows[0])
        : []
  return keys.map((k) => ({
    title: colLabel(k),
    key: k,
    ellipsis: { tooltip: true },
    minWidth: 110,
    render: (row: any) => cellVnode(k, row[k])
  }))
})

const pagination = computed(() =>
  props.rows.length > props.pageSize
    ? {
        pageSize: props.pageSize,
        showSizePicker: true,
        pageSizes: [20, 50, 100, 200],
        showTotal: (total: number) => `共 ${total} 条`
      }
    : false
)

const rowProps = props.clickable
  ? (row: any) => ({
      style: 'cursor: pointer',
      onClick: () => emit('row-click', row)
    })
  : undefined
</script>

<template>
  <div>
    <NSpin v-if="loading" style="min-height: 200px" />
    <NEmpty v-else-if="!rows.length" :description="emptyText" style="padding: 48px 0" />
    <NDataTable
      v-else
      :columns="cols"
      :data="rows"
      :pagination="pagination"
      :row-props="rowProps"
      :max-height="props.maxHeight"
      size="small"
      striped
      :scroll-x="1100"
    />
  </div>
</template>
