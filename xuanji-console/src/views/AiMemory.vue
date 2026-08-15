<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NSelect, NInput, NDataTable, NModal, NForm, NFormItem,
  NIcon, NPopconfirm, NSpin, NTag, NText, NAlert
} from 'naive-ui'
import { ArchiveOutline, AddOutline, SearchOutline } from '@vicons/ionicons5'
import api from '../api'
import type { LlmMemoryRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import dayjs from 'dayjs'
import { useBotsStore } from '../stores/bots'

const message = useMessage()
const botsStore = useBotsStore()
const botKey = ref<string>('')
const mems = ref<LlmMemoryRow[]>([])
const loading = ref(false)

const botOpts = computed(() => botsStore.bots.map(b => ({ label: b.name || b.botKey || b.appId || '', value: b.botKey || b.appId || '' })))

/** 时间（后端 UTC/ISO）→ UTC+8 展示。 */
function fmtSubTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const d = dayjs(String(v))
  return d.isValid() ? d.utcOffset(8).format('YYYY-MM-DD HH:mm:ss') : String(v)
}

// 新增记忆
const showAdd = ref(false)
const newGroup = ref('')
const newUser = ref('')
const newKey = ref('')
const newValue = ref('')
const saving = ref(false)

async function load() {
  if (!botKey.value) return
  loading.value = true
  try {
    mems.value = (await api.llmApi.memory(botKey.value)) || []
  } catch (e: any) {
    message.error('加载记忆失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadBots() {
  try {
    await botsStore.loadBots()
    if (botsStore.bots.length > 0) botKey.value = botsStore.bots[0].botKey || botsStore.bots[0].appId || ''
  } catch {
    // 忽略
  }
}

async function add() {
  if (!newKey.value.trim() || !newValue.value.trim()) {
    message.warning('key 和 value 不能为空')
    return
  }
  saving.value = true
  try {
    await api.llmApi.saveMemory({ botKey: botKey.value, groupId: newGroup.value || undefined, userId: newUser.value || undefined, key: newKey.value.trim(), value: newValue.value.trim() })
    message.success('已保存记忆')
    showAdd.value = false
    newKey.value = ''
    newValue.value = ''
    newGroup.value = ''
    newUser.value = ''
    await load()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function del(r: LlmMemoryRow) {
  try {
    await api.llmApi.deleteMemory(r.id)
    message.success('已删除记忆')
    await load()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

const columns = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '群', key: 'groupId', width: 170, ellipsis: { tooltip: true }, render: (r: LlmMemoryRow) => r.groupId || '—' },
  { title: '用户', key: 'userId', width: 170, ellipsis: { tooltip: true }, render: (r: LlmMemoryRow) => r.userId || '—' },
  { title: '类型', key: 'type', width: 90, render: (r: LlmMemoryRow) =>
      h(NTag, { size: 'small', type: r.type === 'SUMMARY' ? 'warning' : 'info' }, { default: () => r.type === 'SUMMARY' ? '摘要' : '详情' }) },
  { title: '关键名', key: 'key', ellipsis: { tooltip: true } },
  { title: '内容', key: 'value', ellipsis: { tooltip: true } },
  { title: '更新时间', key: 'updatedAt', width: 160, render: (r: LlmMemoryRow) => fmtSubTime(r.updatedAt) },
  { title: '操作', key: 'actions', width: 80, render: (r: LlmMemoryRow) =>
      h(NPopconfirm, { onPositiveClick: () => del(r) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
        default: () => '确认删除这条记忆？'
      }) },
]

onMounted(async () => {
  await loadBots()
  await load()
})
</script>

<template>
  <div class="page">
    <PageHero title="AI 记忆" subtitle="查看 / 管理 AI 的长期记忆。群里对 AI 说「记住…」，AI 会记住并在这里展示；AI 对话中也会自动检索这些记忆" :icon="ArchiveOutline">
      <NSpace>
        <NSelect v-model:value="botKey" :options="botOpts" placeholder="选择机器人" clearable style="width: 180px" @update:value="load" />
        <NButton type="primary" @click="showAdd = true">
          <template #icon><NIcon><AddOutline /></NIcon></template>
          手动添加记忆
        </NButton>
      </NSpace>
    </PageHero>

    <NCard :bordered="true" title="长期记忆列表">
      <NSpin :show="loading">
        <EmptyState v-if="!loading && mems.length === 0" description="暂无记忆。在群里对 AI 说「记住…」即可让 AI 记住" />
        <NDataTable v-else :columns="columns" :data="mems" :row-key="(r: LlmMemoryRow) => r.id" :bordered="false" :pagination="{ pageSize: 20 }" />
      </NSpin>
    </NCard>

    <NAlert type="info" :bordered="false" :show-icon="false">
      <NText depth="2">提示：「记住X」记录的是长期事实记忆；AI 对话的历史上下文会自动存到数据库（重启不丢）。用户画像（对某人的认知）在「用户认知」页查看。</NText>
    </NAlert>

    <NModal v-model:show="showAdd" preset="card" title="添加记忆" style="width: 520px">
      <NForm label-placement="left" label-width="80" label-align="right">
        <NFormItem label="群 ID">
          <NInput v-model:value="newGroup" placeholder="可选，留空表示全局/所有群" />
        </NFormItem>
        <NFormItem label="用户 ID">
          <NInput v-model:value="newUser" placeholder="可选，留空表示所有用户" />
        </NFormItem>
        <NFormItem label="关键名">
          <NInput v-model:value="newKey" placeholder="如：用户昵称 / 约定事项" />
        </NFormItem>
        <NFormItem label="内容">
          <NInput v-model:value="newValue" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="记忆的具体内容" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showAdd = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="add">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
