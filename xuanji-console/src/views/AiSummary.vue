<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NSelect, NDataTable, NInputNumber, NIcon, NEmpty, NSpin, NTag, NPopconfirm, NAlert, NText, NSwitch, NFormItem
} from 'naive-ui'
import { NewspaperOutline, AddOutline, FlashOutline, TrashOutline, TimeOutline } from '@vicons/ionicons5'
import api from '../api'
import type { SummaryConfigRow, SummaryLogRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'

const message = useMessage()
const bots = ref<any[]>([])
const groups = ref<any[]>([])
const botKey = ref<string>('')
const configs = ref<SummaryConfigRow[]>([])
const history = ref<SummaryLogRow[]>([])
const loading = ref(false)
// 每行独立 loading：用「botKey:groupId」作为正在生成的行标识（避免单变量导致所有行一起转圈）
const generatingKey = ref('')
const genResults = ref<Record<string, string>>({})
const lastGenKey = ref('')
function rk(r: SummaryConfigRow) { return r.botKey + ':' + r.groupId }
const genResultText = computed(() => (lastGenKey.value ? (genResults.value[lastGenKey.value] || '') : ''))

const botOpts = computed(() => bots.value.map(b => ({ label: b.name || b.botKey || b.appId || '', value: b.botKey || b.appId || '' })))
const groupOptions = computed(() =>
  groups.value.map((g: any) => {
    const id = g.GROUP_ID || g.groupId || g.groupOpenid || ''
    const name = g.GROUP_NAME || g.groupName || id
    return { label: name || id, value: id }
  })
)

// 新增/编辑配置
const showAdd = ref(false)
const editRow = ref<SummaryConfigRow | null>(null)
const newGroup = ref('')
const newHour = ref(22)
const newMinute = ref(0)
const newImageMode = ref(false)

function openAdd() {
  editRow.value = null
  newGroup.value = ''
  newHour.value = 22
  newMinute.value = 0
  newImageMode.value = false
  showAdd.value = true
}

function openEdit(r: SummaryConfigRow) {
  editRow.value = r
  newGroup.value = r.groupId
  newHour.value = r.runHour
  newMinute.value = r.runMinute
  newImageMode.value = !!r.imageMode
  showAdd.value = true
}

async function load() {
  if (!botKey.value) return
  loading.value = true
  try {
    const [c, h] = await Promise.all([
      api.llmApi.summaryConfigs(botKey.value),
      api.llmApi.summaryHistory(50)
    ])
    configs.value = c || []
    history.value = h || []
  } catch (e: any) {
    message.error('加载日报配置失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadBots() {
  try {
    bots.value = (await api.getBots()) || []
    if (bots.value.length > 0) botKey.value = bots.value[0].botKey || bots.value[0].appId || ''
  } catch {
    bots.value = []
  }
}

async function loadGroups() {
  try {
    groups.value = (await api.getGroups()) || []
  } catch {
    groups.value = []
  }
}

async function add() {
  if (!editRow.value && !newGroup.value) {
    message.warning('请选择群')
    return
  }
  try {
    const botKeyToUse = editRow.value ? editRow.value.botKey : botKey.value
    const groupIdToUse = editRow.value ? editRow.value.groupId : newGroup.value
    await api.llmApi.summarySave({
      botKey: botKeyToUse,
      groupId: groupIdToUse,
      enabled: editRow.value ? editRow.value.enabled : true,
      hour: newHour.value,
      minute: newMinute.value,
      imageMode: newImageMode.value
    })
    message.success(editRow.value ? '日报配置已更新' : '日报配置已添加')
    showAdd.value = false
    editRow.value = null
    newGroup.value = ''
    await load()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function del(r: SummaryConfigRow) {
  try {
    await api.llmApi.summaryDelete(r.botKey, r.groupId)
    message.success('日报配置已删除')
    await load()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

async function toggle(r: SummaryConfigRow) {
  try {
    await api.llmApi.summarySave({ botKey: r.botKey, groupId: r.groupId, enabled: !r.enabled, hour: r.runHour, minute: r.runMinute, imageMode: !!r.imageMode })
    await load()
  } catch (e: any) {
    message.error('更新失败: ' + (e.message || e))
  }
}

async function generate(r: SummaryConfigRow) {
  const k = rk(r)
  generatingKey.value = k
  try {
    const res = await api.llmApi.summaryGenerate(r.botKey, r.groupId)
    genResults.value[k] = res.content || ''
    lastGenKey.value = k
    await load()
  } catch (e: any) {
    message.error('生成失败: ' + (e.message || e))
  } finally {
    if (generatingKey.value === k) generatingKey.value = ''
  }
}

const columns = [
  { title: '群', key: 'groupId', ellipsis: { tooltip: true } },
  { title: '生成时间', key: 'time', width: 130, render: (r: SummaryConfigRow) => `${String(r.runHour).padStart(2, '0')}:${String(r.runMinute).padStart(2, '0')}` },
  { title: '状态', key: 'enabled', width: 90, render: (r: SummaryConfigRow) => r.enabled
      ? h(NTag, { size: 'small', type: 'success' }, { default: () => '开启' })
      : h(NTag, { size: 'small', type: 'default' }, { default: () => '关闭' }) },
  { title: '形式', key: 'imageMode', width: 90, render: (r: SummaryConfigRow) => r.imageMode
      ? h(NTag, { size: 'small', type: 'info' }, { default: () => '图文卡片' })
      : h(NTag, { size: 'small', type: 'default' }, { default: () => '文本' }) },
  { title: '操作', key: 'actions', width: 300, render: (r: SummaryConfigRow) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'tiny', secondary: true, onClick: () => toggle(r) }, { default: () => r.enabled ? '停用' : '启用' }),
        h(NButton, { size: 'tiny', secondary: true, onClick: () => openEdit(r) }, { default: () => '编辑' }),
        h(NButton, { size: 'tiny', type: 'primary', secondary: true, loading: generatingKey.value === rk(r), onClick: () => generate(r) }, { default: () => '手动生成' }),
        h(NPopconfirm, { onPositiveClick: () => del(r) }, {
          trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
          default: () => '确定删除该群的日报配置？'
        })
      ] }) },
]

const historyColumns = [
  { title: '时间', key: 'createdAt', width: 160 },
  { title: '群', key: 'groupId', width: 200 },
  { title: '内容', key: 'content', ellipsis: { tooltip: true } }
]

onMounted(async () => {
  await loadBots()
  await loadGroups()
  await load()
})
</script>

<template>
  <div class="page">
    <PageHero title="AI 日报" subtitle="每日定时为群生成「今日群聊总结」并发到群里。基于群消息数 + 用户画像 + 记忆摘要生成" :icon="NewspaperOutline">
      <NSpace>
        <NSelect v-model:value="botKey" :options="botOpts" placeholder="选择机器人" clearable style="width: 180px" @update:value="load" />
        <NButton type="primary" @click="openAdd">
          <template #icon><NIcon><AddOutline /></NIcon></template>
          添加日报
        </NButton>
      </NSpace>
    </PageHero>

    <NCard :bordered="true" title="日报配置">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && configs.length === 0" description="暂无日报配置，点「添加日报」为群开启每日总结" />
        <NDataTable v-else :columns="columns" :data="configs" :row-key="(r: SummaryConfigRow) => r.botKey + ':' + r.groupId" :bordered="false" />
      </NSpin>
    </NCard>

    <NCard :bordered="true" title="生成历史">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && history.length === 0" description="暂无日报历史" />
        <NDataTable v-else :columns="historyColumns" :data="history" :row-key="(r: SummaryLogRow) => r.id" :bordered="false" />
      </NSpin>
    </NCard>

    <NAlert v-if="genResultText" type="success" :bordered="false" :show-icon="false">
      <div style="white-space: pre-wrap">{{ genResultText }}</div>
    </NAlert>

    <NModal v-model:show="showAdd" preset="card" :title="editRow ? '编辑日报' : '添加日报'" style="width: 440px">
      <NSpace vertical>
        <NFormItem label="群">
          <NSelect v-model:value="newGroup" :options="groupOptions" placeholder="选择群" filterable style="width: 100%" :disabled="!!editRow" />
        </NFormItem>
        <NFormItem label="生成时间">
          <NSpace>
            <NInputNumber v-model:value="newHour" :min="0" :max="23" style="width: 90px" />
            <NText depth="3">时</NText>
            <NInputNumber v-model:value="newMinute" :min="0" :max="59" style="width: 90px" />
            <NText depth="3">分</NText>
          </NSpace>
        </NFormItem>
        <NFormItem label="形式">
          <NSwitch v-model:value="newImageMode">
            <template #checked>图文卡片</template>
            <template #unchecked>文本</template>
          </NSwitch>
          <NText depth="3" style="margin-left: 10px; font-size: 12px">图文卡片需已安装渲染浏览器（Chromium）</NText>
        </NFormItem>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showAdd = false">取消</NButton>
          <NButton type="primary" @click="add">{{ editRow ? '保存' : '添加' }}</NButton>
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
