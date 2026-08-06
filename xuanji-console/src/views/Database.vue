<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  NSelect, NInput, NInputGroup, NButton, NAlert, NSpace, NText, NIcon, NEmpty
} from 'naive-ui'
import { ServerOutline, SearchOutline } from '@vicons/ionicons5'
import api from '../api'
import DataTable from '../components/DataTable.vue'
import { useFillHeight } from '../composables/useFillHeight'

const { fillHeight } = useFillHeight(40)

const loading = ref(false)
const tableOptions = ref<any[]>([])
const selected = ref<string | null>(null)
const source = ref<string>('')
const rows = ref<any[]>([])
const cols = ref<string[]>([])
const err = ref('')
const count = ref(0)

const sql = ref('')
const queryErr = ref('')
const queryRows = ref<any[]>([])

function splitKey(key: string): [string, string] {
  const i = key.indexOf('@@')
  return i < 0 ? [key, ''] : [key.slice(0, i), key.slice(i + 2)]
}

// source 值 → 友好分组名（仅用于显示，查询仍用原始 source 值）
function sourceLabel(s: string): string {
  if (s === 'business') return '璇玑框架库（业务）'
  if (s === 'log') return '璇玑框架库（日志）'
  if (s.startsWith('qqbot:')) return `QQ 机器人 · ${s.slice('qqbot:'.length)}`
  if (s.startsWith('onebot:')) return `OneBot · ${s.slice('onebot:'.length)}`
  return s
}

async function loadTables() {
  try {
    const ts: any[] = await api.dbTables()
    const bySource = new Map<string, any[]>()
    for (const t of ts) {
      const s = t.SOURCE || 'business'
      if (!bySource.has(s)) bySource.set(s, [])
      bySource.get(s)!.push({ label: t.TABLE_NAME, value: `${t.TABLE_NAME}@@${s}` })
    }
    tableOptions.value = [...bySource.entries()].map(([s, children]) => ({
      type: 'group',
      label: sourceLabel(s),
      key: s,
      children
    }))
  } catch (e: any) {
    err.value = e.message
  }
}

async function onSelect(key: string) {
  if (!key) return
  const [table, src] = splitKey(key)
  source.value = src
  loading.value = true
  err.value = ''
  try {
    const r = await api.dbRows(table, src)
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

async function runQuery() {
  if (!sql.value.trim()) return
  queryErr.value = ''
  queryRows.value = []
  try {
    const r = await api.dbQuery(sql.value, source.value)
    if (r.error) queryErr.value = r.error
    else queryRows.value = r.rows || []
  } catch (e: any) {
    queryErr.value = e.message
  }
}

const title = computed(() => {
  if (!selected.value) return '数据库浏览'
  const [table, src] = splitKey(selected.value)
  return `数据库浏览 · ${table} (${src})`
})

onMounted(loadTables)
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#5b5bd6"><ServerOutline /></NIcon>
        <span>数据库浏览</span>
        <NText depth="3" style="font-size: 13px">{{ count }} 行</NText>
      </div>
      <NSelect
        v-model:value="selected"
        :options="tableOptions"
        placeholder="选择表"
        style="width: 360px"
        @update:value="onSelect"
      />
    </div>

    <NAlert v-if="err" type="error" :title="'查询失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <DataTable v-if="rows.length" :rows="rows" :columns="cols" :page-size="50" :max-height="fillHeight" empty-text="空表" />

    <div class="query-bar">
      <NInputGroup style="width: 600px">
        <NInput
          v-model:value="sql"
          placeholder="只读 SELECT 查询（当前 source）"
          @keyup.enter="runQuery"
        >
          <template #prefix><NIcon><SearchOutline /></NIcon></template>
        </NInput>
        <NButton type="primary" :loading="loading" @click="runQuery">执行</NButton>
      </NInputGroup>
    </div>
    <NAlert v-if="queryErr" type="error" :title="'SQL 错误'" style="margin-top: 12px">{{ queryErr }}</NAlert>
    <DataTable v-if="queryRows.length" style="margin-top: 12px" :rows="queryRows" :page-size="50" :max-height="fillHeight" empty-text="无结果" />

    <NEmpty
      v-if="!rows.length && !err && !queryRows.length"
      description="从上方选择一个表查看数据"
      style="padding: 60px 0"
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
.query-bar {
  margin-top: 24px;
  display: flex;
  justify-content: flex-start;
}
</style>
