<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NSelect, NInput, NDataTable, NModal, NForm, NFormItem,
  NIcon, NPopconfirm, NSpin, NTag, NTooltip, NSwitch
} from 'naive-ui'
import {
  GitNetworkOutline, AddOutline, TrashOutline, LinkOutline, UnlinkOutline,
  RefreshOutline, FlashOutline
} from '@vicons/ionicons5'
import api from '../api'
import type { McpServerRow, LlmToolInfo } from '../api/llm'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import { useBotsStore } from '../stores/bots'
import { BASE } from '../api/http'

const message = useMessage()
const botsStore = useBotsStore()

const botKey = ref<string>('')
const servers = ref<McpServerRow[]>([])
const mcpTools = ref<LlmToolInfo[]>([])
const loading = ref(false)
const connecting = ref<Record<string, boolean>>({})

const botOpts = computed(() => botsStore.bots.map(b => ({ label: b.name || b.botKey || b.appId || '', value: b.botKey || b.appId || '' })))

async function load() {
  loading.value = true
  try {
    const [s, t] = await Promise.all([
      api.llmApi.mcpList(botKey.value || undefined),
      api.llmApi.mcpTools()
    ])
    servers.value = s || []
    mcpTools.value = t || []
  } catch (e: any) {
    message.error('加载失败: ' + (e.message || e))
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

// 表单
const showModal = ref(false)
const form = ref<McpServerRow>({ botKey: '', name: '', url: '', description: '', whitelist: false, enabled: true })

function openAdd() {
  form.value = { botKey: botKey.value, name: '', url: '', description: '', whitelist: false, enabled: true }
  showModal.value = true
}

async function save() {
  if (!form.value.name.trim()) { message.warning('请填写名称'); return }
  if (!form.value.url.trim()) { message.warning('请填写 URL'); return }
  try {
    await api.llmApi.mcpRegister(form.value)
    message.success('已保存')
    showModal.value = false
    await load()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function toggleEnabled(r: McpServerRow) {
  try {
    await api.llmApi.mcpRegister({ ...r, enabled: !r.enabled })
    message.success(r.enabled ? '已停用' : '已启用')
    await load()
  } catch (e: any) {
    message.error('操作失败: ' + (e.message || e))
  }
}

async function remove(r: McpServerRow) {
  try {
    await api.llmApi.mcpDelete(r.botKey, r.name)
    message.success('已删除')
    await load()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

async function connect(r: McpServerRow) {
  const k = r.botKey + ':' + r.name
  connecting.value[k] = true
  try {
    const res = await api.llmApi.mcpConnect(r.botKey, r.name)
    message.success(`连接成功，注册 ${res.tools || 0} 个工具`)
    await load()
  } catch (e: any) {
    message.error('连接失败: ' + (e.message || e))
  } finally {
    connecting.value[k] = false
  }
}

async function disconnect(r: McpServerRow) {
  try {
    await api.llmApi.mcpDisconnect(r.botKey, r.name)
    message.success('已断开')
    await load()
  } catch (e: any) {
    message.error('断开失败: ' + (e.message || e))
  }
}

async function connectDemo() {
  const url = window.location.origin + BASE + '/console/llm/mcp-demo'
  form.value = { botKey: botKey.value, name: 'demo', url, description: '内置演示 MCP server', whitelist: true, enabled: true }
  showModal.value = true
}

const columns = [
  { title: '名称', key: 'name', width: 110, render: (r: McpServerRow) => h(NTag, { size: 'small', type: 'primary', bordered: false }, { default: () => r.name }) },
  { title: 'URL', key: 'url', ellipsis: { tooltip: true } },
  { title: '白名单', key: 'whitelist', width: 90, render: (r: McpServerRow) => r.whitelist
      ? h(NTag, { size: 'small', type: 'success' }, { default: () => '直连' })
      : h(NTag, { size: 'small', type: 'warning' }, { default: () => '需确认' }) },
  { title: '启用', key: 'enabled', width: 90, render: (r: McpServerRow) =>
      h(NSwitch, { size: 'small', value: !!r.enabled, 'onUpdate:value': () => toggleEnabled(r) }) },
  { title: '操作', key: 'actions', width: 230, render: (r: McpServerRow) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'tiny', type: 'primary', secondary: true, loading: connecting.value[r.botKey + ':' + r.name] || false, onClick: () => connect(r) },
          { default: () => '连接' }),
        h(NPopconfirm, { onPositiveClick: () => disconnect(r) }, {
          trigger: () => h(NButton, { size: 'tiny', secondary: true }, { default: () => '断开' }),
          default: () => '确认断开该 MCP server？'
        }),
        h(NPopconfirm, { onPositiveClick: () => remove(r) }, {
          trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
          default: () => '确认删除？'
        })
      ] }) },
]

onMounted(async () => {
  await loadBots()
  await load()
})
</script>

<template>
  <div class="page">
    <PageHero title="MCP 服务" subtitle="连接外部 MCP server（行业标准协议），其工具自动进入 AI 工具库，Agent 可调用。非白名单工具执行前需确认" :icon="GitNetworkOutline">
      <NSpace>
        <NSelect v-model:value="botKey" :options="botOpts" placeholder="选择机器人" clearable style="width: 180px" @update:value="load" />
        <NButton secondary @click="load">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </PageHero>

    <NCard :bordered="true">
      <div class="toolbar">
        <NButton type="primary" @click="openAdd">
          <template #icon><NIcon><AddOutline /></NIcon></template>
          注册 MCP server
        </NButton>
        <NButton secondary @click="connectDemo">
          <template #icon><NIcon><FlashOutline /></NIcon></template>
          一键连接内置演示
        </NButton>
      </div>
      <NSpin :show="loading">
        <EmptyState v-if="!loading && servers.length === 0" description="暂无 MCP server，点击「一键连接内置演示」体验" />
        <NDataTable v-else :columns="columns" :data="servers" :row-key="(r: McpServerRow) => r.botKey + ':' + r.name" :bordered="false" :pagination="{ pageSize: 20 }" />
      </NSpin>
    </NCard>

    <NCard :bordered="true" title="已桥接的 MCP 工具">
      <NSpin :show="loading">
        <EmptyState v-if="!loading && mcpTools.length === 0" description="连接 MCP server 后，其工具会出现在这里（也在「AI 工具」页）" />
        <NDataTable v-else :columns="[
          { title: '工具名', key: 'name', width: 180, render: (r: LlmToolInfo) => h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => r.name }) },
          { title: '来源', key: 'source', width: 120, render: (r: LlmToolInfo) => r.source },
          { title: '确认', key: 'confirm', width: 100, render: (r: LlmToolInfo) => r.confirm
              ? h(NTag, { size: 'small', type: 'warning' }, { default: () => '需确认' }) : h(NTag, { size: 'small', type: 'success' }, { default: () => '直连' }) },
          { title: '描述', key: 'description', ellipsis: { tooltip: true } }
        ]" :data="mcpTools" :row-key="(r: LlmToolInfo) => r.name" :bordered="false" :pagination="{ pageSize: 20 }" />
      </NSpin>
    </NCard>

    <NModal v-model:show="showModal" preset="card" title="注册 MCP server" style="width: 520px">
      <NForm label-placement="left" label-width="90" label-align="right">
        <NFormItem label="机器人">
          <NSelect v-model:value="form.botKey" :options="botOpts" placeholder="选择机器人" style="width: 100%" />
        </NFormItem>
        <NFormItem label="名称">
          <NInput v-model:value="form.name" placeholder="如 demo / 文件系统" />
        </NFormItem>
        <NFormItem label="URL">
          <NInput v-model:value="form.url" placeholder="MCP 端点地址，如 http://localhost:9000/mcp" />
        </NFormItem>
        <NFormItem label="描述">
          <NInput v-model:value="form.description" placeholder="选填" />
        </NFormItem>
        <NFormItem label="白名单">
          <NTooltip trigger="hover">
            <template #trigger><NSwitch v-model:value="form.whitelist" /></template>
            白名单 = 工具直接执行；否则 Agent 调用前需用户确认
          </NTooltip>
        </NFormItem>
        <NFormItem label="启用">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="save">保存</NButton>
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
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
