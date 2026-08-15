<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  NInput, NButton, NAlert, NSpace, NText, NIcon, NSpin, NTag, NSelect
} from 'naive-ui'
import { ServerOutline } from '@vicons/ionicons5'
import api from '../api'
import DataTable from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import { useFillHeight } from '../composables/useFillHeight'

const { fillHeight } = useFillHeight(40)

const loading = ref(false)
const groups = ref<{ key: string; label: string; tables: { name: string; source: string }[] }[]>([])
const selected = ref<{ name: string; source: string } | null>(null)
const rows = ref<any[]>([])
const cols = ref<string[]>([])
const err = ref('')
const count = ref(0)

// 结构化筛选（替代自由 SQL）：列 / 操作符 / 值 / 排序，全部后端白名单校验
const filterColumn = ref<string | null>(null)
const filterOp = ref('=')
const filterValue = ref('')
const orderBy = ref<string | null>(null)
const orderDir = ref<'ASC' | 'DESC'>('ASC')
const filterActive = ref(false)
const filterErr = ref('')

const opOptions = [
  { label: '等于 =', value: '=' },
  { label: '不等于 !=', value: '!=' },
  { label: '包含 LIKE', value: 'LIKE' },
  { label: '大于 >', value: '>' },
  { label: '小于 <', value: '<' },
  { label: '大于等于 >=', value: '>=' },
  { label: '小于等于 <=', value: '<=' }
]

// source 值 → 友好分组名（仅用于显示，查询仍用原始 source 值）
function sourceLabel(s: string): string {
  if (s === 'business') return '框架级 · 业务库'
  if (s === 'log') return '框架级 · 日志库'
  if (s.endsWith(':shared')) return `Bot 级 · ${s.slice(0, s.indexOf(':'))} 平台共享库`
  if (s.startsWith('qqbot:')) return `Bot 级 · QQ 机器人 ${s.slice('qqbot:'.length)}`
  return s
}

async function loadTables() {
  try {
    const ts: any[] = await api.dbTables()
    const bySource = new Map<string, { name: string; source: string }[]>()
    for (const t of ts) {
      const s = t.SOURCE || 'business'
      if (!bySource.has(s)) bySource.set(s, [])
      bySource.get(s)!.push({ name: t.TABLE_NAME, source: s })
    }
    groups.value = [...bySource.entries()].map(([s, tables]) => ({
      key: s,
      label: sourceLabel(s),
      tables
    }))
  } catch (e: any) {
    err.value = e.message
  }
}

async function onSelectTable(name: string, source: string) {
  selected.value = { name, source }
  loading.value = true
  err.value = ''
  clearFilter()
  try {
    const r = await api.dbRows(name, source)
    if (r.error) {
      err.value = r.error
      rows.value = []
    } else {
      rows.value = r.rows || []
      cols.value = r.columns || []
      count.value = r.count || 0
    }
  } catch (e: any) {
    err.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

function clearFilter() {
  filterColumn.value = null
  filterOp.value = '='
  filterValue.value = ''
  orderBy.value = null
  orderDir.value = 'ASC'
  filterActive.value = false
  filterErr.value = ''
}

async function runFilter() {
  if (!selected.value) return
  filterErr.value = ''
  loading.value = true
  try {
    const r = await api.dbFilter({
      table: selected.value.name,
      source: selected.value.source,
      column: filterColumn.value || undefined,
      op: filterOp.value,
      value: filterValue.value || undefined,
      orderBy: orderBy.value || undefined,
      orderDir: orderDir.value
    })
    if (r.error) {
      filterErr.value = r.error
    } else {
      rows.value = r.rows || []
      count.value = r.count || 0
      filterActive.value = true
    }
  } catch (e: any) {
    filterErr.value = e.message
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  clearFilter()
  if (selected.value) onSelectTable(selected.value.name, selected.value.source)
}

const currentTitle = computed(() =>
  selected.value ? `${selected.value.name}（${sourceLabel(selected.value.source)}）` : ''
)

onMounted(loadTables)
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#5b5bd6"><ServerOutline /></NIcon>
        <span>数据库浏览</span>
        <NText depth="3" style="font-size: 13px">{{ groups.flatMap(g => g.tables).length }} 张表 · 只读</NText>
      </div>
    </div>

    <NAlert v-if="err" type="error" :title="'查询失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <!-- 表清单：按框架级 / Bot 级分组平铺，点击即查 -->
    <div class="table-browser">
      <div class="tb-list">
        <div v-for="g in groups" :key="g.key" class="tb-group">
          <div class="tb-group-title">
            <NTag size="small" :bordered="false" type="info">{{ g.tables.length }}</NTag>
            <span>{{ g.label }}</span>
          </div>
          <div class="tb-tables">
            <button
              v-for="t in g.tables"
              :key="t.source + '@@' + t.name"
              class="tb-table"
              :class="{ active: selected && selected.name === t.name && selected.source === t.source }"
              @click="onSelectTable(t.name, t.source)"
              :title="t.source"
            >
              <NIcon size="12" style="flex-shrink: 0"><ServerOutline /></NIcon>
              <span class="tb-name">{{ t.name }}</span>
              <span class="tb-src">{{ t.source }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 数据区 -->
      <div class="tb-data">
        <template v-if="selected">
          <div class="tb-data-head">
            <span class="tb-data-title">{{ selected.name }}</span>
            <NText depth="3" style="font-size: 13px">{{ count }} 行{{ filterActive ? '（已筛选）' : '' }}</NText>
          </div>
          <div class="tb-filter-bar">
            <NSelect v-model:value="filterColumn" :options="cols.map(c => ({ label: c, value: c }))" placeholder="筛选列" clearable style="width: 170px" />
            <NSelect v-model:value="filterOp" :options="opOptions" style="width: 130px" />
            <NInput v-model:value="filterValue" placeholder="筛选值（LIKE 为模糊匹配）" clearable style="width: 200px" @keyup.enter="runFilter" />
            <NSelect v-model:value="orderBy" :options="cols.map(c => ({ label: c, value: c }))" placeholder="排序列" clearable style="width: 170px" />
            <NSelect v-model:value="orderDir" :options="[{ label: '升序', value: 'ASC' }, { label: '降序', value: 'DESC' }]" style="width: 90px" />
            <NButton type="primary" size="small" :loading="loading" @click="runFilter">查询</NButton>
            <NButton v-if="filterActive" size="small" @click="resetFilter">重置</NButton>
          </div>
          <NAlert v-if="filterErr" type="error" :title="'筛选错误'" style="margin-bottom: 10px">{{ filterErr }}</NAlert>
          <NSpin :show="loading">
            <DataTable v-if="rows.length" :rows="rows" :columns="cols" :page-size="50" :max-height="fillHeight" empty-text="空表" />
            <EmptyState v-else-if="!err" description="该表暂无数据" />
          </NSpin>
        </template>
        <EmptyState v-else description="点击上方任意表名，查看该表全部字段和数据">
          <template #icon><NIcon size="48"><ServerOutline /></NIcon></template>
        </EmptyState>
      </div>
    </div>

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

.table-browser {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* 左侧表清单 */
.tb-list {
  width: 320px;
  flex-shrink: 0;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.2));
  border-radius: 8px;
  padding: 12px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  background: var(--n-color, rgba(128, 128, 128, 0.04));
}
.tb-group {
  margin-bottom: 14px;
}
.tb-group:last-child {
  margin-bottom: 0;
}
.tb-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--n-text-color-3, #888);
  margin-bottom: 6px;
  padding: 0 2px;
}
.tb-tables {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tb-table {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 3px 8px;
  border: 1px solid rgba(128, 128, 128, 0.25);
  border-radius: 6px;
  background: transparent;
  color: var(--n-text-color, #333);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.tb-table:hover {
  border-color: #5b5bd6;
  color: #5b5bd6;
}
.tb-table.active {
  background: #5b5bd6;
  border-color: #5b5bd6;
  color: #fff;
}
.tb-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tb-src {
  font-size: 10px;
  opacity: 0.65;
}

/* 右侧数据区 */
.tb-data {
  flex: 1;
  min-width: 0;
}
.tb-data-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.tb-filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.tb-data-title {
  font-size: 15px;
  font-weight: 600;
}
</style>
