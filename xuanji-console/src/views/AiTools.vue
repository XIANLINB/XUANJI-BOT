<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NDataTable, NTag, NIcon, NEmpty, NSpin, NTooltip, NSpace
} from 'naive-ui'
import {
  ExtensionPuzzleOutline, ShieldCheckmarkOutline, FlashOutline, RefreshOutline
} from '@vicons/ionicons5'
import api from '../api'
import type { LlmToolInfo } from '../api/llm'
import PageHero from '../components/PageHero.vue'

const message = useMessage()
const tools = ref<LlmToolInfo[]>([])
const loading = ref(false)

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
  { title: '参数', key: 'params', ellipsis: { tooltip: true }, render: (r: LlmToolInfo) =>
      r.parameters && (r.parameters.properties as Record<string, any>)
        ? Object.keys(r.parameters.properties as Record<string, any>).join('、')
        : '（无）' },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHero title="AI 工具" subtitle="@LlmTool 工具清单 — 对话时 AI 可按需调用这些能力（Function Calling）；需确认的工具先征求你的同意再执行" :icon="ExtensionPuzzleOutline">
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
          <NEmpty v-if="!loading && tools.length === 0" description="暂无已注册工具（需重启生效）" />
          <NDataTable v-else :columns="columns" :data="tools" :row-key="(r: LlmToolInfo) => r.name" :bordered="false" />
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
