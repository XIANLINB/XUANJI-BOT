<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import { NCard, NButton, NDataTable, NTag, NIcon, NEmpty, NSpin, NAlert } from 'naive-ui'
import { ShieldCheckmarkOutline, RefreshOutline } from '@vicons/ionicons5'
import api from '../api'
import type { AuditLogRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'

const message = useMessage()
const logs = ref<AuditLogRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    logs.value = (await api.llmApi.auditLogs(100)) || []
  } catch (e: any) {
    message.error('加载拦截记录失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '时间', key: 'createdAt', width: 160 },
  { title: '群', key: 'groupId', width: 200 },
  { title: '用户', key: 'userId', width: 120 },
  { title: '结果', key: 'action', width: 90, render: (r: AuditLogRow) =>
      r.action === 'BLOCK'
        ? h(NTag, { size: 'small', type: 'error' }, { default: () => '拦截' })
        : h(NTag, { size: 'small', type: 'success' }, { default: () => '放行' }) },
  { title: '消息', key: 'text', ellipsis: { tooltip: true } },
  { title: '原因', key: 'reason', width: 160, ellipsis: { tooltip: true } }
]

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHero title="AI 审核" subtitle="消息过 LLM 审核，违规拦截不响应。开关在「AI 设置 → AI 内容审核」（默认关）" :icon="ShieldCheckmarkOutline">
      <NButton secondary @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NAlert type="info" :bordered="false">
      开启后，每条群消息先过 LLM 审核（辱骂/涉黄/涉政/广告/诈骗等），违规直接拦截不回复，并记录在此页。
      关闭时所有消息直接放行（当前状态取决于「AI 设置 → AI 内容审核」开关）。
    </NAlert>

    <NCard :bordered="true" title="审核记录">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && logs.length === 0" description="暂无审核记录（未开启审核或尚无消息）" />
        <NDataTable v-else :columns="columns" :data="logs" :row-key="(r: AuditLogRow) => r.id" :bordered="false" />
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
