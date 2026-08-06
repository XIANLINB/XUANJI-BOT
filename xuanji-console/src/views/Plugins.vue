<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NButton, NCard, NDataTable, NModal, NSelect, NSpace, NTag, NText, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { ExtensionPuzzleOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'

const message = useMessage()
const loading = ref(false)
const rows = ref<any[]>([])

const columns: DataTableColumns<any> = [
  { title: '插件 ID', key: 'id', width: 180 },
  { title: '版本', key: 'version', width: 120 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'state',
    width: 110,
    render: (r: any) =>
      h(
        NTag,
        { size: 'small', type: r.running ? 'success' : 'default', round: true },
        { default: () => (r.running ? '运行中' : '已停用') }
      )
  },
  {
    title: '操作',
    key: 'op',
    width: 160,
    render: (r: any) =>
      h(
        NSpace,
        { size: 8 },
        {
          default: () => [
            h(
              NSpace,
              { size: 8 },
              {
                default: () => [
                  h(
                    NButton,
                    {
                      size: 'small',
                      type: r.running ? 'warning' : 'primary',
                      disabled: loading.value,
                      onClick: () => (r.running ? stop(r.id) : start(r.id))
                    },
                    { default: () => (r.running ? '停用' : '启用') }
                  ),
                  h(
                    NButton,
                    {
                      size: 'small',
                      secondary: true,
                      onClick: () => openBindings(r)
                    },
                    { default: () => '绑定' }
                  ),
                  h(
                    NButton,
                    {
                      size: 'small',
                      tertiary: true,
                      onClick: () => reload(r.id)
                    },
                    { default: () => '热加载' }
                  )
                ]
              }
            )
          ]
        }
      )
  }
]

// ===== 插件-机器人绑定 =====
const showBindings = ref(false)
const bindPluginId = ref('')
const bindBots = ref<any[]>([])
const bindRows = ref<any[]>([])
const bindSelectBotKey = ref('')
const botsList = ref<any[]>([])

async function openBindings(r: any) {
  bindPluginId.value = r.id
  bindSelectBotKey.value = ''
  try {
    const [binds, bots] = await Promise.all([api.listBindings(r.id), api.getBots()])
    bindRows.value = binds || []
    botsList.value = bots || []
    showBindings.value = true
  } catch (e: any) {
    message.error('加载绑定失败：' + (e?.message ?? e))
  }
}

async function addBinding() {
  if (!bindSelectBotKey.value) {
    message.warning('请选择机器人')
    return
  }
  const bot = botsList.value.find((b: any) => (b.botKey || b.appId) === bindSelectBotKey.value)
  const platform = bot?.platform || 'qqbot'
  try {
    await api.bindPlugin(bindPluginId.value, platform, bindSelectBotKey.value)
    message.success('已绑定')
  } catch (e: any) {
    message.error('绑定失败：' + (e?.message ?? e))
  } finally {
    bindRows.value = await api.listBindings(bindPluginId.value)
  }
}

async function removeBinding(row: any) {
  try {
    await api.unbindPlugin(bindPluginId.value, row.platform, row.botKey)
    message.success('已解绑')
  } catch (e: any) {
    message.error('解绑失败：' + (e?.message ?? e))
  } finally {
    bindRows.value = await api.listBindings(bindPluginId.value)
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await api.getPlugins()
  } catch (e: any) {
    message.error('加载插件失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function start(id: string) {
  try {
    const r = await api.startPlugin(id)
    if (r.error) message.error(r.error)
    else message.success('插件已启用：' + id)
  } catch (e: any) {
    message.error('启用失败：' + (e?.message ?? e))
  } finally {
    load()
  }
}

async function stop(id: string) {
  try {
    const r = await api.stopPlugin(id)
    if (r.error) message.error(r.error)
    else message.success('插件已停用：' + id)
  } catch (e: any) {
    message.error('停用失败：' + (e?.message ?? e))
  } finally {
    load()
  }
}

async function reload(id: string) {
  try {
    const r = await api.reloadPlugin(id)
    if (r.status === 'error') message.error('热加载失败（看后端日志）')
    else message.success('插件已热加载：' + id)
  } catch (e: any) {
    message.error('热加载失败：' + (e?.message ?? e))
  } finally {
    load()
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="插件管理" subtitle="查看并启停已加载的 Xuanji 插件" :icon="ExtensionPuzzleOutline">
      <NButton :loading="loading" @click="load">刷新</NButton>
    </PageHero>
    <NCard :bordered="false" class="card">
      <NDataTable
        :columns="columns"
        :data="rows"
        :loading="loading"
        :row-key="(r: any) => r.id"
        :bordered="false"
      />
      <NText depth="3" style="font-size: 12px; display: block; margin-top: 12px">
        插件 jar 放在项目根 plugins/ 目录，修改后需重新打包并在 IDEA 重启框架生效。
        「绑定」可为插件指定生效的机器人（不绑定 = 对所有机器人生效）。
      </NText>
    </NCard>

    <!-- 绑定弹窗 -->
    <NModal
      v-model:show="showBindings"
      preset="card"
      :title="`插件绑定 · ${bindPluginId}`"
      style="width: 520px; max-width: 92vw"
      :bordered="false"
    >
      <NSpace vertical :size="12">
        <NSpace :size="8" align="center">
          <NSelect
            v-model:value="bindSelectBotKey"
            :options="botsList.map((b: any) => ({ label: (b.botKey || b.appId) + (b.name ? ` (${b.name})` : ''), value: b.botKey || b.appId }))"
            placeholder="选择机器人"
            style="width: 300px"
          />
          <NButton type="primary" size="small" :disabled="!bindSelectBotKey" @click="addBinding">
            绑定该机器人
          </NButton>
        </NSpace>
        <NText depth="3" style="font-size: 12px">
          当前绑定列表（空 = 全局生效，对所有机器人可用）：
        </NText>
        <div v-if="bindRows.length">
          <div
            v-for="(row, i) in bindRows"
            :key="i"
            style="display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 0.5px solid rgba(0,0,0,0.06)"
          >
            <span style="font-size: 13px">{{ row.botKey }} <NTag size="small" :bordered="false" type="info">{{ row.platform }}</NTag></span>
            <NButton size="tiny" type="error" @click="removeBinding(row)">解绑</NButton>
          </div>
        </div>
        <NText v-else depth="3" style="font-size: 12px">暂无绑定（全局生效）</NText>
      </NSpace>
    </NModal>
  </div>
</template>

<style scoped>
.card {
  border-radius: 14px;
}
</style>
