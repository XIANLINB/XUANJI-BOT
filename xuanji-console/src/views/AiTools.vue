<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NDataTable, NTag, NIcon, NSpin, NTooltip, NSpace, NInput
} from 'naive-ui'
import {
  ExtensionPuzzleOutline, ShieldCheckmarkOutline, FlashOutline, RefreshOutline, SearchOutline
} from '@vicons/ionicons5'
import api from '../api'
import type { LlmToolInfo } from '../api/llm'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'

const message = useMessage()
const tools = ref<LlmToolInfo[]>([])
const loading = ref(false)
const search = ref('')

// 搜索（名称/中文释义/描述）+ 按名称排序
const filteredTools = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const list = kw
    ? tools.value.filter(t =>
        (t.name || '').toLowerCase().includes(kw) ||
        (t.descriptionZh || '').toLowerCase().includes(kw) ||
        (t.description || '').toLowerCase().includes(kw))
    : [...tools.value]
  return list.sort((a, b) => (a.name || '').localeCompare(b.name || ''))
})

/** 参数摘要：name:type(必填) 逗号连接。 */
function paramSummary(t: LlmToolInfo): string {
  const props = t.parameters?.properties as Record<string, any> | undefined
  if (!props) return '（无）'
  const required = Array.isArray(t.parameters?.required) ? (t.parameters.required as string[]) : []
  const entries = Object.entries(props)
  if (!entries.length) return '（无）'
  return entries.map(([k, v]) => {
    const type = (v as any)?.type || (v as any)?.format || ''
    const req = required.includes(k) ? '·必填' : ''
    return type ? `${k}:${type}${req}` : `${k}${req}`
  }).join('、')
}

async function load() {
  loading.value = true
  try {
    tools.value = (await api.llmApi.tools()) || []
  } catch (e: any) {
    message.error('加载工具失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '工具名', key: 'name', width: 160, render: (r: LlmToolInfo) =>
      h(NSpace, { size: 6, align: 'center' }, { default: () => [
        h(NTag, { size: 'small', type: 'primary', bordered: false }, { default: () => r.name }),
        r.source && r.source !== 'BuiltinTools' ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => r.source }) : null
      ] }) },
  { title: '中文释义', key: 'descriptionZh', minWidth: 180, ellipsis: { tooltip: true }, render: (r: LlmToolInfo) => r.descriptionZh || '—' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true }, render: (r: LlmToolInfo) => r.description || '—' },
  { title: '确认执行', key: 'confirm', width: 110, render: (r: LlmToolInfo) =>
      r.confirm
        ? h(NTag, { size: 'small', type: 'warning' }, { default: () => '需确认' })
        : h(NTag, { size: 'small', type: 'success' }, { default: () => '直接执行' }) },
  { title: '参数', key: 'params', ellipsis: { tooltip: true }, render: (r: LlmToolInfo) => paramSummary(r) },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHero title="AI 工具" subtitle="@LlmTool 工具清单 — 对话时 AI 可按需调用这些能力（Function Calling）；需确认的工具先征求你的同意再执行" :icon="ExtensionPuzzleOutline">
      <NInput v-model:value="search" placeholder="搜索工具名 / 描述" clearable size="small" style="width: 220px">
        <template #prefix><NIcon><SearchOutline /></NIcon></template>
      </NInput>
      <NButton secondary @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NCard :bordered="true">
      <NSpace vertical size="medium">
        <div class="tip-row">
          <NTag size="small" type="info" :bordered="false">
            <template #icon><NIcon><FlashOutline /></NIcon></template>
            群里 @机器人 说人话即可触发，如「现在几点」「掷个骰子」「每周五15点提醒我喝水」
          </NTag>
          <NTag size="small" type="warning" :bordered="false">
            <template #icon><NIcon><ShieldCheckmarkOutline /></NIcon></template>
            标记「需确认」的工具（执行命令/建任务）会先问你是否执行
          </NTag>
        </div>
        <NSpin :show="loading">
          <EmptyState v-if="!loading && tools.length === 0" description="暂无已注册工具（需重启生效）" />
          <NDataTable v-else :columns="columns" :data="filteredTools" :row-key="(r: LlmToolInfo) => r.name" :bordered="false" :pagination="{ pageSize: 20 }" />
        </NSpin>
      </NSpace>
    </NCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.tip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
